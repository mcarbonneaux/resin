/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.network.listen;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.env.shutdown.ExitCode;
import com.caucho.env.shutdown.ShutdownSystem;
import com.caucho.env.thread.ThreadPool;
import com.caucho.inject.Module;
import com.caucho.inject.RequestContext;
import com.caucho.loader.Environment;
import com.caucho.management.server.AbstractManagedObject;
import com.caucho.management.server.TcpConnectionMXBean;
import com.caucho.util.Alarm;
import com.caucho.util.Friend;
import com.caucho.util.L10N;
import com.caucho.vfs.ClientDisconnectException;
import com.caucho.vfs.QSocket;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.SocketTimeoutException;
import com.caucho.vfs.StreamImpl;

/**
 * A protocol-independent TcpConnection.  TcpConnection controls the
 * TCP Socket and provides buffered streams.
 *
 * <p>Each TcpConnection has its own thread.
 */
@Module
public class TcpSocketLink extends AbstractSocketLink
{
  private static final L10N L = new L10N(TcpSocketLink.class);
  private static final Logger log
    = Logger.getLogger(TcpSocketLink.class.getName());

  private static final ThreadLocal<ProtocolConnection> _currentRequest
    = new ThreadLocal<ProtocolConnection>();

  private static ClassLoader _systemClassLoader;

  private final int _connectionId;  // The connection's id
  private final String _id;
  private final String _name;
  private String _dbgId;

  private final TcpSocketLinkListener _listener;
  private final QSocket _socket;
  private final ProtocolConnection _request;
  private final ClassLoader _loader;
  private final byte []_testBuffer = new byte[1];

  private final AcceptTask _acceptTask;
  // HTTP keepalive task
  private final KeepaliveRequestTask _keepaliveTask;
  // Comet resume task
  private final CometResumeTask _resumeTask;
  // duplex (websocket) task
  private DuplexReadTask _duplexReadTask;

  private final Admin _admin = new Admin();

  private SocketLinkState _state = SocketLinkState.INIT;
  
  private final AtomicReference<SocketLinkRequestState> _requestStateRef
    = new AtomicReference<SocketLinkRequestState>(SocketLinkRequestState.INIT)
    ;
  private TcpAsyncController _async;

  private long _idleTimeout;
  private long _suspendTimeout;

  private long _connectionStartTime;
  private long _requestStartTime;
  
  private long _readBytes;
  private long _writeBytes;

  private long _idleStartTime;
  private long _idleExpireTime;

  // statistics state
  private String _displayState;

  private Thread _thread;

  /**
   * Creates a new TcpConnection.
   *
   * @param server The TCP server controlling the connections
   * @param request The protocol Request
   */
  TcpSocketLink(int connId,
                TcpSocketLinkListener listener,
                QSocket socket)
  {
    _connectionId = connId;

    _listener = listener;
    _socket = socket;

    int id = getId();

    _loader = listener.getClassLoader();

    Protocol protocol = listener.getProtocol();

    _request = protocol.createConnection(this);

    _id = listener.getDebugId() + "-" + id;
    _name = _id;

    _acceptTask = new AcceptTask(this);
    _keepaliveTask = new KeepaliveRequestTask(this);
    _resumeTask = new CometResumeTask(this);
  }

  /**
   * Returns the ServerRequest for the current thread.
   */
  public static ProtocolConnection getCurrentRequest()
  {
    return _currentRequest.get();
  }

  /**
   * For QA only, set the current request.
   */
  public static void qaSetCurrentRequest(ProtocolConnection request)
  {
    _currentRequest.set(request);
  }

  /**
   * Returns the connection id.  Primarily for debugging.
   */
  @Override
  public int getId()
  {
    return _connectionId;
  }
  
  public String getDebugId()
  {
    return _id;
  }

  /**
   * Returns the object name for jmx.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Returns the port which generated the connection.
   */
  public TcpSocketLinkListener getListener()
  {
    return _listener;
  }

  /**
   * Returns the request for the connection.
   */
  public final ProtocolConnection getRequest()
  {
    return _request;
  }

  /**
   * Returns the admin
   */
  public TcpConnectionMXBean getAdmin()
  {
    return _admin;
  }

  //
  // timeout properties
  //

  /**
   * Sets the idle time for a keepalive connection.
   */
  public void setIdleTimeout(long idleTimeout)
  {
    _idleTimeout = idleTimeout;
    _suspendTimeout = idleTimeout;
  }

  /**
   * The idle time for a keepalive connection
   */
  public long getIdleTimeout()
  {
    return _idleTimeout;
  }

  //
  // port information
  //
  
  @Override
  public boolean isPortActive()
  {
    return _listener.isActive();
  }
  
  //
  // state information
  //

  /**
   * Returns the state.
   */
  @Override
  public SocketLinkState getState()
  {
    return _state;
  }

  public final boolean isIdle()
  {
    return _state.isIdle();
  }

  /**
   * Returns true for active.
   */
  public boolean isActive()
  {
    return _state.isActive();
  }

  /**
   * Returns true for active.
   */
  public boolean isRequestActive()
  {
    return _state.isRequestActive();
  }

  @Override
  public boolean isKeepaliveAllocated()
  {
    return _state.isKeepaliveAllocated();
  }

  /**
   * Returns true for closed.
   */
  public boolean isClosed()
  {
    return _state.isClosed();
  }

  public final boolean isDestroyed()
  {
    return _state.isDestroyed();
  }

  @Override
  public boolean isCometActive()
  {
    TcpAsyncController async = _async;

    return (_state.isCometActive()
            && async != null 
            && ! async.isCompleteRequested());
  }

  public boolean isAsyncStarted()
  {
    return _state.isAsyncStarted();
  }

  @Override
  public boolean isCometSuspend()
  {
    return _state.isCometSuspend();
  }

  boolean isCometComplete()
  {
    return _state.isCometComplete();
  }

  @Override
  public boolean isDuplex()
  {
    return _state.isDuplex();
  }

  boolean isWakeRequested()
  {
    return _state.isCometWake();
  }

  //
  // port/socket information
  //

  /**
   * Returns the connection's socket
   */
  public QSocket getSocket()
  {
    return _socket;
  }

  /**
   * Returns the local address of the socket.
   */
  @Override
  public InetAddress getLocalAddress()
  {
    return _socket.getLocalAddress();
  }

  /**
   * Returns the local host name.
   */
  @Override
  public String getLocalHost()
  {
    return _socket.getLocalHost();
  }

  /**
   * Returns the socket's local TCP port.
   */
  @Override
  public int getLocalPort()
  {
    return _socket.getLocalPort();
  }

  /**
   * Returns the socket's remote address.
   */
  @Override
  public InetAddress getRemoteAddress()
  {
    return _socket.getRemoteAddress();
  }

  /**
   * Returns the socket's remote host name.
   */
  @Override
  public String getRemoteHost()
  {
    return _socket.getRemoteHost();
  }

  /**
   * Adds from the socket's remote address.
   */
  @Override
  public int getRemoteAddress(byte []buffer, int offset, int length)
  {
    return _socket.getRemoteAddress(buffer, offset, length);
  }

  /**
   * Returns the socket's remote port
   */
  @Override
  public int getRemotePort()
  {
    return _socket.getRemotePort();
  }

  /**
   * Returns true if the connection is secure, i.e. a SSL connection
   */
  @Override
  public boolean isSecure()
  {
    return _socket.isSecure() || _listener.isSecure();
  }

  /**
   * Returns the virtual host.
   */
  @Override
  public String getVirtualHost()
  {
    return getListener().getVirtualHost();
  }
  
  //
  // SSL api
  //
  
  /**
   * Returns the cipher suite
   */
  @Override
  public String getCipherSuite()
  {
    return _socket.getCipherSuite();
  }
  
  /***
   * Returns the key size.
   */
  @Override
  public int getKeySize()
  {
    return _socket.getCipherBits();
  }
  
  /**
   * Returns any client certificates.
   * @throws CertificateException 
   */
  @Override
  public X509Certificate []getClientCertificates()
    throws CertificateException
  {
    return _socket.getClientCertificates();
  }
  

  //
  // thread information
  //

  /**
   * Returns the thread id.
   */
  public final long getThreadId()
  {
    Thread thread = _thread;

    if (thread != null)
      return thread.getId();
    else
      return -1;
  }
  
  //
  // connection information

  /**
   * Returns the time the connection started
   */
  public final long getConnectionStartTime()
  {
    return _connectionStartTime;
  }

  //
  // request information
  //

  /**
   * Returns the time the request started
   */
  public final long getRequestStartTime()
  {
    return _requestStartTime;
  }

  /**
   * Returns the idle expire time (keepalive or suspend).
   */
  public long getIdleExpireTime()
  {
    return _idleExpireTime;
  }

  /**
   * Returns the idle start time (keepalive or suspend)
   */
  public long getIdleStartTime()
  {
    return _idleStartTime;
  }

  /**
   * Returns the current keepalive task
   */
  private Runnable getAcceptTask()
  {
    return _acceptTask;
  }

  /**
   * Returns the current keepalive task (request or duplex)
   */
  private ConnectionTask getKeepaliveTask()
  {
    if (_state.isDuplex())
      return _duplexReadTask;
    else
      return _keepaliveTask;
  }

  /**
   * Returns the comet resume task
   */
  private Runnable getResumeTask()
  {
    return _resumeTask;
  }

  //
  // statistics state
  //

  /**
   * Returns the user statistics state
   */
  public String getDisplayState()
  {
    return _displayState;
  }

  /**
   * Sets the user statistics state
   */
  private void setStatState(String state)
  {
    _displayState = state;
  }

  //
  // async/comet predicates
  //

  /**
   * Poll the socket to test for an end-of-file for a comet socket.
   */
  @Friend(TcpSocketLinkListener.class)
  boolean isReadEof()
  {
    QSocket socket = _socket;

    if (socket == null) {
      return true;
    }

    try {
      StreamImpl s = socket.getStream();

      int len = s.readNonBlock(_testBuffer, 0, 0);

      return len < 0;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return true;
    }
  }
  
  //
  // transition requests from external threads (thread-safe)
  
  /**
   * Start a request connection from the idle state.
   */
  void requestAccept()
  {
    if (_requestStateRef.get().toAccept(_requestStateRef)) {
      ThreadPool threadPool = _listener.getThreadPool();
    
      if (! threadPool.schedule(getAcceptTask())) {
        log.severe(L.l("Schedule failed for {0}", this));
      }
    }
  }
  
  /**
   * Wake a connection from a select/poll keepalive.
   */
  void requestWakeKeepalive()
  {
    if (_requestStateRef.get().toWakeKeepalive(_requestStateRef)) {
      ThreadPool threadPool = _listener.getThreadPool();
    
      if (! threadPool.schedule(getKeepaliveTask())) {
        log.severe(L.l("Schedule failed for {0}", this));
      }
    }
  }
  
  /**
   * Wake a connection from a comet suspend.
   */
  void requestWakeComet()
  {
    if (_requestStateRef.get().toAsyncWake(_requestStateRef)) {
      ThreadPool threadPool = _listener.getThreadPool();
    
      if (! threadPool.schedule(getResumeTask())) {
        log.severe(L.l("Schedule failed for {0}", this));
      }
    }
  }

  /**
   * Closes the controller.
   */
  void requestCometComplete()
  {
    TcpAsyncController async = _async;
    
    if (async != null) {
      async.setCompleteRequested();
    }

    requestWakeComet();
  }

  /**
   * Closes the controller.
   */
  void requestCometTimeout()
  {
    TcpAsyncController async = _async;
    
    if (async != null) {
      async.setTimeout();
    }

    requestWakeComet();
  }

  @Override
  public void requestShutdownBegin()
  {
    _listener.requestShutdownBegin();
  }

  @Override
  public void requestShutdownEnd()
  {
    _listener.requestShutdownEnd();
  }

  //
  // Callbacks from the request processing tasks
  //
  
  @Friend(ConnectionTask.class)
  void startThread(Thread thread)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer(this + " start thread " + thread.getName());
    }
    
    _thread = thread;
  }

  /**
   * Completion processing at the end of the thread
   */
  @Friend(ConnectionTask.class)
  void finishThread(RequestState requestState)
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " finish thread: " + Thread.currentThread().getName());
    
    _thread = null;
    
    SocketLinkState state = _state;

    if (! (state.isComet() || state.isDuplex())
        && ! requestState.isAsyncOrDuplex()) {
      try {
        closeAsync();
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
    
    if (requestState.isDetach() && ! state.isClosed()) {
      return;
    }
    
    getListener().closeConnection(this);

    if (state.isAllowIdle()) {
      _state = state.toIdle();
      
      _requestStateRef.get().toIdle(_requestStateRef);
        
      _listener.free(this);
    }
    else {
      System.out.println("NOFREE:" + requestState + " " + _requestStateRef.get() + " " + this);
    }
  }

  @Friend(AcceptTask.class)
  RequestState handleAcceptTask()
    throws IOException
  {
    TcpSocketLinkListener listener = getListener();
    
    RequestState result = RequestState.REQUEST_COMPLETE;
    SocketLinkThreadLauncher launcher = _listener.getLauncher();
    
    while (result == RequestState.REQUEST_COMPLETE
           && ! listener.isClosed()
           && ! getState().isDestroyed()) {
      setStatState("accept");
      _state = _state.toAccept();
      
      if (launcher.isIdleExpire())
        return RequestState.EXIT;

      if (! accept()) {
        close();

        continue;
      }

      toStartConnection();

      if (log.isLoggable(Level.FINER)) {
        log.finer(this + " accept from "
                  + getRemoteHost() + ":" + getRemotePort());
      }

      result = handleRequests(Task.ACCEPT);
    }

    return result;
  }

  private boolean accept()
  {
    SocketLinkThreadLauncher launcher = _listener.getLauncher();
    
    launcher.onChildIdleBegin();
    try {
      return getListener().accept(getSocket());
    } finally {
      launcher.onChildIdleEnd();
    }
  }

  @Friend(KeepaliveRequestTask.class)
  RequestState handleKeepaliveTask()
    throws IOException
  {
    return handleRequests(Task.KEEPALIVE);
  }

  /**
   * Handles the resume from ResumeTask. 
   * 
   * Called by the request thread only.
   */
  @Friend(CometResumeTask.class)
  RequestState handleResumeTask()
  {
    try {
      while (true) {
        _listener.cometDetach(this);
        
        _state = _state.toCometResume();
      
        TcpAsyncController async = getAsyncController();

        if (async == null) {
          return RequestState.EXIT;
        }
      
        // _state = _state.toCometWake();
        // _state = _state.toCometDispatch();
      
      
        if (async.isTimeout()) {
          async.timeout();
          return RequestState.EXIT;
        }

        async.toResume();

        getRequest().handleResume();

        if (_state.isComet()) {
          if (toSuspend())
            return RequestState.ASYNC;
          else
            continue;
        }
        else if (isKeepaliveAllocated()) {
          // server/1l81
          _state = _state.toKeepalive(this);
          _async = null;

          async.onClose();
        
          return getKeepaliveTask().doTask();
        }
        else {
          _async = null;

          async.onClose();
          
          close();
        
          return RequestState.REQUEST_COMPLETE;
        }
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    } catch (OutOfMemoryError e) {
      String msg = "TcpSocketLink OutOfMemory";

      ShutdownSystem.shutdownActive(ExitCode.MEMORY, msg);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    return RequestState.EXIT;
  }

  @Friend(DuplexReadTask.class)
  RequestState handleDuplexRead(SocketLinkDuplexController duplex)
    throws IOException
  {
    toDuplexActive();

    RequestState result;

    ReadStream readStream = getReadStream();

    while ((result = processKeepalive()) == RequestState.REQUEST_COMPLETE) {
      long position = readStream.getPosition();

      duplex.serviceRead();

      if (position == readStream.getPosition()) {
        log.warning(duplex + " was not processing any data. Shutting down.");
        
        close();

        return RequestState.EXIT;
      }
    }

    return result;
  }

  /**
   * Handles a new connection/socket from the client.
   */
  private RequestState handleRequests(Task taskType)
    throws IOException
  {
    Thread thread = Thread.currentThread();

    RequestState result = RequestState.EXIT;

    try {
      // clear the interrupted flag
      Thread.interrupted();

      result = handleRequestsImpl(taskType == Task.KEEPALIVE);
    } catch (ClientDisconnectException e) {
      _listener.addLifetimeClientDisconnectCount();

      if (log.isLoggable(Level.FINER)) {
        log.finer(dbgId() + e);
      }
    } catch (InterruptedIOException e) {
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, dbgId() + e, e);
      }
    } catch (IOException e) {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, dbgId() + e, e);
      }
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, dbgId() + e, e);
      }
    } finally {
      thread.setContextClassLoader(_loader);

      if (result == null) {
        result = RequestState.EXIT;
      }
    }

    switch (result) {
    case KEEPALIVE_SELECT:
    case ASYNC:
      return result;
      
    case DUPLEX:
      return _duplexReadTask.doTask();
      
    case EXIT:
      close();
      return result;
      
    case REQUEST_COMPLETE:
      // acceptTask significantly faster than finishing
      close();
      
      if (taskType == Task.ACCEPT) {
        return result;
      }
      else
        return _acceptTask.doTask();
      
    default:
      throw new IllegalStateException(String.valueOf(result));
    }
  }

  /**
   * Handles a new connection/socket from the client.
   */
  private RequestState handleRequestsImpl(boolean isKeepalive)
    throws IOException
  {
    RequestState result;
    
    do {
      result = RequestState.EXIT;
      
      if (_listener.isClosed()) {
        return RequestState.EXIT;
      }

      if (isKeepalive
          && (result = processKeepalive()) != RequestState.REQUEST_COMPLETE) {
        return result;
      }

      try {
        result = handleRequest(isKeepalive);
      } finally {
        if (! result.isAsyncOrDuplex()) {
          closeAsync();
        }
      }
      
      isKeepalive = true;
    } while (result.isRequestKeepalive() && _state.isKeepaliveAllocated());

    return result;
  }
  
  private RequestState handleRequest(boolean isKeepalive)
    throws IOException
  {
    dispatchRequest();

    if (_state == SocketLinkState.DUPLEX) {
      if (_duplexReadTask == null)
        throw new NullPointerException();
      // duplex (xmpp/hmtp) handling
      return RequestState.DUPLEX;
    }

    getWriteStream().flush();

    if (_state.isCometActive()) {
      if (toSuspend())
        return RequestState.ASYNC;
      else
        return handleResumeTask();
    }
   
    return RequestState.REQUEST_COMPLETE;
  }

  private void dispatchRequest()
    throws IOException
  {
    Thread thread = Thread.currentThread();

    try {
      thread.setContextClassLoader(_loader);

      _currentRequest.set(_request);
      RequestContext.begin();
      _requestStartTime = Alarm.getCurrentTime();
      
      _readBytes = _socket.getTotalReadBytes();
      _writeBytes = _socket.getTotalWriteBytes();

      _state = _state.toActive(this, _connectionStartTime);

      if (! getRequest().handleRequest()) {
        killKeepalive();
        
        if (log.isLoggable(Level.FINE)) {
          log.fine(this + " disabled keepalive because request failed "
                   + getRequest());
        }
      }
      
      _requestStartTime = 0;
      
      long readBytes = _socket.getTotalReadBytes();
      long writeBytes = _socket.getTotalWriteBytes();
      
      _listener.addLifetimeReadBytes(readBytes - _readBytes);
      _listener.addLifetimeReadBytes(writeBytes - _writeBytes);
      
      _readBytes = readBytes;
      _writeBytes = writeBytes;
    }
    finally {
      thread.setContextClassLoader(_loader);

      _currentRequest.set(null);
      RequestContext.end();
    }
  }

  /**
   * Starts a keepalive, either returning available data or
   * returning false to close the loop
   *
   * If keepaliveRead() returns true, data is available.
   * If it returns false, either the connection is closed,
   * or the connection has been registered with the select.
   */
  private RequestState processKeepalive()
    throws IOException
  {
    TcpSocketLinkListener port = _listener;

    // quick timed read to see if data is already available
    int available = port.keepaliveThreadRead(getReadStream());
    
    if (available > 0) {
      return RequestState.REQUEST_COMPLETE;
    }
    else if (available < 0) {
      if (log.isLoggable(Level.FINER))
        log.finer(this + " keepalive read failed: " + available);
      
      setStatState(null);
      close();
      
      return RequestState.EXIT;
    }

    _idleStartTime = Alarm.getCurrentTime();
    _idleExpireTime = _idleStartTime + _idleTimeout;

    _state = _state.toKeepalive(this);

    // use select manager if available
    if (_listener.getSelectManager() != null) {
      _requestStateRef.get().toKeepalive(_requestStateRef);
      
      // keepalive to select manager succeeds
      if (_listener.getSelectManager().keepalive(this)) {
        if (log.isLoggable(Level.FINE))
          log.fine(dbgId() + " keepalive (select)");

        return RequestState.KEEPALIVE_SELECT;
      }
      else {
        log.warning(dbgId() + " failed keepalive (select)");
        
        _requestStateRef.get().toWakeKeepalive(_requestStateRef);
      }
    }

    return threadKeepalive();
  }
  
  private RequestState threadKeepalive()
  {
    if (log.isLoggable(Level.FINE))
      log.fine(dbgId() + " keepalive (thread)");

    long timeout = getListener().getKeepaliveTimeout();
    long expires = timeout + Alarm.getCurrentTimeActual();

    do {
      try {
        long delta = expires - Alarm.getCurrentTimeActual();
        if (delta < 0)
          delta = 0;
        
        if (getReadStream().fillWithTimeout(delta) > 0) {
          return RequestState.REQUEST_COMPLETE;
        }
        break;
      } catch (SocketTimeoutException e) {
        log.log(Level.FINEST, e.toString(), e);
      } catch (IOException e) {
        log.log(Level.FINEST, e.toString(), e);
        break;
      }
    } while (Alarm.getCurrentTimeActual() < expires);

    close();

    return RequestState.EXIT;
  }

  //
  // state transitions
  //

  /**
   * Start a connection
   */
  private void toStartConnection()
    throws IOException
  {
    _connectionStartTime = Alarm.getCurrentTime();

    setStatState("read");
    initSocket();

    _request.onStartConnection();
  }
  
  /**  
   * Initialize the socket for a new connection
   */
  private void initSocket()
    throws IOException
  {
    _idleTimeout = _listener.getKeepaliveTimeout();
    _suspendTimeout = _listener.getSuspendTimeMax();

    getWriteStream().init(_socket.getStream());

    // ReadStream cannot use getWriteStream or auto-flush
    // because of duplex mode
    getReadStream().init(_socket.getStream(), null);

    if (log.isLoggable(Level.FINE)) {
      log.fine(dbgId() + "starting connection " + this
               + ", total=" + _listener.getConnectionCount());
    }
  }
  
  /**
   * Kills the keepalive, so the end of the request is the end of the
   * connection.
   */
  @Override
  public void killKeepalive()
  {
    Thread thread = Thread.currentThread();
    
    if (thread != _thread)
      throw new IllegalStateException(L.l("{0} killKeepalive called from invalid thread",
                                          this));
    
    SocketLinkState state = _state;
    
    _state = state.toKillKeepalive(this);
    
    if (log.isLoggable(Level.FINE))
      log.fine(this + " keepalive disabled from " + state);
  }

  //
  // async/comet state transitions
  //
  
  TcpAsyncController getAsyncController()
  {
    return _async;
  }
  
  /**
   * Starts a comet connection.
   * 
   * Called by the socketLink thread only.
   */
  @Override
  public AsyncController toComet(SocketLinkCometListener cometHandler)
  {
    Thread thread = Thread.currentThread();
    
    if (thread != _thread)
      throw new IllegalStateException(L.l("{0} toComet called from invalid thread",
                                          this));
    
    TcpAsyncController async = _async;
    
    // TCK
    if (async != null && async.isCompleteRequested())
      throw new IllegalStateException(L.l("Comet cannot be requested after complete()."));
    
    _requestStateRef.get().toAsyncStart(_requestStateRef);
    
    _state = _state.toComet();
    
    if (async == null)
      _async = async = new TcpAsyncController(this);
    
    async.initHandler(cometHandler);
    
    if (log.isLoggable(Level.FINER))
      log.finer(this + " starting comet " + cometHandler);
    
    return async;
  }

  private boolean toSuspend()
  {
    
    _idleStartTime = Alarm.getCurrentTime();
    _idleExpireTime = _idleStartTime + _suspendTimeout;

    if (log.isLoggable(Level.FINER))
      log.finer(this + " suspending comet");
    
    _state = _state.toCometSuspend();
    // XXX: request state
    
    _listener.cometSuspend(this);
    
    if (_requestStateRef.get().toAsyncSuspend(_requestStateRef)) {
      return true;
    }
    
    _listener.cometDetach(this);
    
    return false;
  }

  /**
   * Wakes the connection (comet-style).
   */
  private boolean wake()
  {
    // _state = _state.toCometWake();
    
    // comet
    if (getListener().cometResume(this)) {
      log.fine(dbgId() + "wake");
      return true;
    }
    else {
      return false;
    }
  }
  
  /**
   * Called to signal a comet suspend timeout by the TcpSocketLinkListener.
   */
  private void toCometTimeout()
  {
    TcpAsyncController async = _async;
    
    if (async != null) {
      async.setTimeout();
    }

    wake();
  }

  /**
   * Called to request the comet connectin complete.
   */
  private void toCometComplete()
  {
    TcpAsyncController async = _async;
    
    if (async != null) {
      async.setCompleteRequested();
    }
    
    wake();
  }

  //
  // duplex/websocket
  //

  /**
   * Starts a full duplex (tcp style) request for hmtp/xmpp
   */
  @Override
  public SocketLinkDuplexController startDuplex(SocketLinkDuplexListener handler)
  {
    Thread thread = Thread.currentThread();
    
    if (thread != _thread)
      throw new IllegalStateException(L.l("{0} toComet called from invalid thread",
                                          this));
    
    _state = _state.toDuplex(this);

    SocketLinkDuplexController duplex = new SocketLinkDuplexController(this, handler);
    
    _duplexReadTask = new DuplexReadTask(this, duplex);
    
    if (log.isLoggable(Level.FINER))
      log.finer(this + " starting duplex " + handler);

    try {
      handler.onStart(duplex);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return duplex;
  }
  
  private void toDuplexActive()
  {
    _state = _state.toDuplexActive(this);
  }
  
  //
  // close operations
  //

  /**
   * Called by HTTP for early close on client disconnect.
   * 
   * XXX: may want to revise this logic
   */
  /*
  public void requestEarlyClose()
  {
    if (log.isLoggable(Level.FINE))
      log.fine(this +" early close, most likely from client disconnect.");
    
    // close();
    killKeepalive();
  }
  */

  void close()
  {
    setStatState(null);
    
    closeConnection();
  }

  /**
   * Closes on shutdown.
   */
  public void closeOnShutdown()
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " closeOnShutdown");
    
  }

  /**
   * Destroys the connection()
   */
  public final void destroy()
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " destroying connection");
    
    try {
      _socket.forceShutdown();
    } catch (Throwable e) {
      
    }

    closeConnection();

    _state = _state.toDestroy(this);
  }
  
  /**
   * Closes the connection.
   */
  private void closeConnection()
  {
    SocketLinkState state = _state;
    _state = state.toClosed(this);

    if (state.isClosed() || state.isIdle()) {
      return;
    }

    TcpSocketLinkListener port = getListener();
    
    QSocket socket = _socket;
    
    // detach any comet
    if (state.isComet() || state.isCometSuspend())
      port.cometDetach(this);

    try {
      getRequest().onCloseConnection();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    if (log.isLoggable(Level.FINER)) {
      if (port != null)
        log.finer(dbgId() + "closing connection " + this
                  + ", total=" + port.getConnectionCount());
      else
        log.finer(dbgId() + "closing connection " + this);
    }

    try {
      getWriteStream().close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    try {
      getReadStream().close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    if (port != null) {
      port.closeSocket(socket);
    }

    try {
      socket.close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  private void closeAsync()
  {
    SocketLinkState state = _state;
    
    if (state.isComet() || state.isDuplex())
      return;
    
    DuplexReadTask duplexTask = _duplexReadTask;
    _duplexReadTask = null;

    AsyncController async = _async;
    _async = null;

    if (async != null)
      async.onClose();
    
    if (duplexTask != null)
      duplexTask.onClose();
    
  }

  private String dbgId()
  {
    if (_dbgId == null) {
      Object serverId = Environment.getAttribute("caucho.server-id");

      if (serverId != null)
        _dbgId = (getClass().getSimpleName() + "[id=" + getId()
                  + "," + serverId + "] ");
      else
        _dbgId = (getClass().getSimpleName() + "[id=" + getId() + "] ");
    }

    return _dbgId;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[id=" + _id + "," + _listener.toURL() + "," + _state + "]";
  }

  class Admin extends AbstractManagedObject implements TcpConnectionMXBean {
    Admin()
    {
      super(_systemClassLoader);
    }

    public String getName()
    {
      return _name;
    }

    @Override
    public long getThreadId()
    {
      return TcpSocketLink.this.getThreadId();
    }

    @Override
    public long getRequestActiveTime()
    {
      if (_requestStartTime > 0)
        return Alarm.getCurrentTime() - _requestStartTime;
      else
        return -1;
    }

    @Override
    public String getUrl()
    {
      ProtocolConnection request = TcpSocketLink.this.getRequest();

      String url = request.getProtocolRequestURL();
      
      if (url != null && ! "".equals(url))
        return url;
      
      TcpSocketLinkListener port = TcpSocketLink.this.getListener();

      if (port.getAddress() == null)
        return "request://*:" + port.getPort();
      else
        return "request://" + port.getAddress() + ":" + port.getPort();
    }

    public String getState()
    {
      return TcpSocketLink.this.getState().toString();
    }

    public String getDisplayState()
    {
      return TcpSocketLink.this.getDisplayState();
    }

    public String getRemoteAddress()
    {
      return TcpSocketLink.this.getRemoteHost();
    }

    void register()
    {
      registerSelf();
    }

    void unregister()
    {
      unregisterSelf();
    }
  }

  static {
    _systemClassLoader = ClassLoader.getSystemClassLoader();
  }
  
  enum Task {
    ACCEPT,
    KEEPALIVE;
  }
}
