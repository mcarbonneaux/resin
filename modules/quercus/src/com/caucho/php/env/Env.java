/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
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

package com.caucho.php.env;

import java.io.IOException;

import java.text.Collator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Iterator;
import java.util.Locale;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

import com.caucho.vfs.Vfs;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.ByteToChar;


import com.caucho.util.Log;
import com.caucho.util.L10N;
import com.caucho.util.IntMap;
import com.caucho.util.Alarm;

import com.caucho.php.Quercus;
import com.caucho.php.PhpRuntimeException;
import com.caucho.php.PhpExitException;

import com.caucho.php.expr.Expr;

import com.caucho.php.page.PhpPage;

import com.caucho.php.program.AbstractFunction;
import com.caucho.php.program.AbstractClassDef;
import com.caucho.php.program.PhpProgram;

import com.caucho.php.resources.StreamContextResource;

/**
 * Represents the PHP environment.
 */
public class Env {
  private static final L10N L = new L10N(Env.class);
  private static final Logger log = Log.open(Env.class);

  public static final int B_ERROR = 0;
  public static final int B_WARNING = 1;
  public static final int B_PARSE = 2;
  public static final int B_NOTICE = 3;
  public static final int B_CORE_ERROR = 4;
  public static final int B_CORE_WARNING = 5;
  public static final int B_COMPILE_ERROR = 6;
  public static final int B_COMPILE_WARNING = 7;
  public static final int B_USER_ERROR = 8;
  public static final int B_USER_WARNING = 9;
  public static final int B_USER_NOTICE = 10;
  public static final int B_STRICT = 11;

  public static final int E_ERROR = 1 << B_ERROR;
  public static final int E_WARNING = 1 << B_WARNING;
  public static final int E_PARSE = 1 << B_PARSE;
  public static final int E_NOTICE = 1 << B_NOTICE;
  public static final int E_CORE_ERROR = 1 << B_CORE_ERROR;
  public static final int E_CORE_WARNING = 1 << B_CORE_WARNING;
  public static final int E_COMPILE_ERROR = 1 << B_COMPILE_ERROR;
  public static final int E_COMPILE_WARNING = 1 << B_COMPILE_WARNING;
  public static final int E_USER_ERROR = 1 << B_USER_ERROR;
  public static final int E_USER_WARNING = 1 << B_USER_WARNING;
  public static final int E_USER_NOTICE = 1 << B_USER_NOTICE;
  public static final int E_ALL = 2048 - 1;
  public static final int E_STRICT = 1 << B_STRICT;

  public static final int E_DEFAULT = E_ALL & ~E_NOTICE;

  private static final int _SERVER = 1;
  private static final int _GET = 2;
  private static final int _POST = 3;
  private static final int _COOKIE = 4;
  private static final int _GLOBAL = 5;
  private static final int _REQUEST = 6;
  private static final int _SESSION = 7;
  private static final int HTTP_GET_VARS = 8;
  private static final int HTTP_POST_VARS = 9;

  private static final IntMap SPECIAL_VARS = new IntMap();
  
  private Quercus _quercus;
  private PhpPage _page;

  private Value _this = NullValue.NULL;

  private ArrayList<ResourceValue> _resourceList = new ArrayList<ResourceValue>();
  private HashMap<String,Var> _globalMap = new HashMap<String,Var>();
  private HashMap<String,Var> _staticMap = new HashMap<String,Var>();
  private HashMap<String,Var> _map = _globalMap;
  private VarMap<String,Value> _constMap;

  private HashMap<String,AbstractFunction> _funMap
    = new HashMap<String,AbstractFunction>();

  private HashMap<String,AbstractClassDef> _classDefMap
    = new HashMap<String,AbstractClassDef>();

  private HashMap<String,PhpClass> _classMap
    = new HashMap<String,PhpClass>();

  private HashMap<String,Value> _optionMap
    = new HashMap<String,Value>();
  
  private HashMap<String,StringValue> _iniMap;

  private HashMap<String,Value> _specialMap
    = new HashMap<String,Value>();

  private String _includePath;
  private ArrayList<Path> _includePathList;

  private HashSet<Path> _includeSet = new HashSet<Path>();

  private AbstractFunction _autoload;

  private long _startTime;
  private long _timeLimit = 60000L;

  private Expr []_callStack = new Expr[256];
  private int _callStackTop;

  private Value []_functionArgs;

  private Path _selfPath;
  private Path _pwd;

  private HttpServletRequest _request;
  private HttpServletResponse _response;

  private ArrayValue _post;
  private SessionArrayValue _session;
  
  private WriteStream _originalOut;
  private OutputBuffer _outputBuffer;
  
  private WriteStream _out;

  private Callback []_errorHandlers = new Callback[B_STRICT + 1];

  private StreamContextResource _defaultStreamContext;

  // XXX: need to look this up from the module itself
  private int _errorMask = E_DEFAULT;

  public Env(Quercus quercus,
	     PhpPage page,
	     WriteStream out,
	     HttpServletRequest request,
	     HttpServletResponse response)
  {
    _quercus = quercus;

    _page = page;

    _originalOut = out;
    _out = out;

    _request = request;
    _response = response;

    _constMap = new ChainedMap<String,Value>(_quercus.getConstMap());

    _page.init(this);

    setPwd(Vfs.lookup());

    _selfPath = _page.getSelfPath(null);

    if (_request.getMethod().equals("POST")) {
      _post = new ArrayValueImpl();
      Post.fillPost(_post, _request);
    }

    _startTime = Alarm.getCurrentTime();
  }

  /**
   * add resource to _resourceList
   */
  public void addResource(ResourceValue resource)
  {
    _resourceList.add(resource);
  }

  /**
   * remove resource from _resourceList
   * @param resource
   */
  public void removeResource(ResourceValue resource)
  {
    _resourceList.remove(resource);
  }

  /**
   * Returns the owning PHP engine.
   */
  public Quercus getPhp()
  {
    return _quercus;
  }

  /**
   * Returns the configured database.
   */
  public DataSource getDatabase()
  {
    return _quercus.getDatabase();
  }

  /**
   * Sets the time limit.
   */
  public void setTimeLimit(long ms)
  {
    // _timeLimit = ms;
  }
  
  /**
   * Checks for the program timeout.
   */
  public final void checkTimeout()
  {
    long now = Alarm.getCurrentTime();

    if (_startTime + _timeLimit < now)
      throw new PhpRuntimeException(L.l("script timed out"));
  }

  /**
   * Returns the writer.
   */
  public WriteStream getOut()
  {
    return _out;
  }

  /**
   * Returns the writer.
   */
  public WriteStream getOriginalOut()
  {
    return _originalOut;
  }

  /**
   * Returns the current output buffer.
   */
  public OutputBuffer getOutputBuffer()
  {
    return _outputBuffer;
  }

  /**
   * Returns the writer.
   */
  public void pushOutputBuffer(Callback callback, int chunkSize, boolean erase)
  {
    _outputBuffer = new OutputBuffer(_outputBuffer, this, callback);
    _out = _outputBuffer.getOut();
  }

  /**
   * Pops the output buffer
   */
  public boolean popOutputBuffer()
  {
    OutputBuffer outputBuffer = _outputBuffer;

    if (outputBuffer == null)
      return false;

    outputBuffer.close();
    
    _outputBuffer = outputBuffer.getNext();

    if (_outputBuffer != null)
      _out = _outputBuffer.getOut();
    else
      _out = _originalOut;

    return true;
  }

  /**
   * Returns the current directory.
   */
  public Path getPwd()
  {
    // return _pwd;
    return getSelfPath().getParent();
  }

  /**
   * Sets the current directory.
   */
  public void setPwd(Path path)
  {
    _pwd = path;
  }

  /**
   * Returns the initial directory.
   */
  public Path getSelfPath()
  {
    return _selfPath;
  }

  /**
   * Sets the initial directory.
   */
  public void setSelfPath(Path path)
  {
    _selfPath = path;
  }

  /**
   * Returns the request.
   */
  public HttpServletRequest getRequest()
  {
    return _request;
  }

  /**
   * Returns the response.
   */
  public HttpServletResponse getResponse()
  {
    return _response;
  }

  /**
   * Returns the session.
   */
  public SessionArrayValue getSession()
  {
    return _session;
  }

  /**
   * Sets the session.
   */
  public void setSession(SessionArrayValue session)
  {
    _session = session;

    setGlobalValue("_SESSION", session);
  }

  /**
   * Create the session.
   */
  public SessionArrayValue createSession(String sessionId)
  {
    SessionArrayValue session = _quercus.loadSession(this, sessionId);

    if (session == null)
      session = new SessionArrayValue(this, sessionId);
      
    setSession(session);

    return session;
  }

  /**
   * Returns an ini value.
   */
  public StringValue getIni(String var)
  {
    StringValue oldValue = null;
    
    if (_iniMap != null)
      oldValue = _iniMap.get(var);

    if (oldValue != null)
      return oldValue;
    else
      return _quercus.getIni(var);
  }

  /**
   * Returns an ini value.
   */
  public Value setIni(String var, String value)
  {
    StringValue oldValue = getIni(var);

    if (_iniMap == null)
      _iniMap = new HashMap<String,StringValue>();

    _iniMap.put(var, new StringValue(value));
    
    return oldValue;
  }

  /**
   * Returns an ini value.
   */
  public boolean getIniBoolean(String var)
  {
    Value value = getIni(var);

    if (value != null)
      return value.toBoolean();
    else
      return false;
  }

  /**
   * Returns an ini value as a long.
   */
  public long getIniLong(String var)
  {
    Value value = getIni(var);

    if (value != null)
      return value.toLong();
    else
      return 0;
  }

  /**
   * Returns an ini value as a string.
   */
  public String getIniString(String var)
  {
    Value value = getIni(var);

    if (value != null)
      return value.toString();
    else
      return null;
  }

  /**
   * Returns the ByteToChar converter.
   */
  public ByteToChar getByteToChar()
  {
    return ByteToChar.create();
  }

  /**
   * Returns the 'this' value.
   */
  public Value getThis()
  {
    return _this;
  }

  /**
   * Sets the 'this' value, returning the old value.
   */
  public Value setThis(Value value)
  {
    Value oldThis = _this;
    
    _this = value.toValue();
    
    return oldThis;
  }

  /**
   * Gets a value.
   */
  public Value getValue(String name)
  {
    Var var = getRef(name);

    if (var != null)
      return var.toValue();
    else
      return NullValue.NULL;
  }

  /**
   * Gets a special value.  Created to handle mySQL connection.
   */
  public Value getSpecialValue(String name)
  {
    Value value = (Value) _specialMap.get(name);

    return value;
  }
  /**
   * Gets a global
   */
  public Value getGlobalValue(String name)
  {
    Var var = getGlobalRef(name);

    if (var != null)
      return var.toValue();
    else
      return NullValue.NULL;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param var the current value of the variable
   */
  public final Var getVar(String name, Value var)
  {
    if (var != null)
      return (Var) var;

    var = getRef(name);

    if (var == null) {
      var = new Var();
      _map.put(name, (Var) var);
    }
    
    return (Var) var;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param var the current value of the variable
   */
  public final Var getGlobalVar(String name, Value value)
  {
    if (value != null)
      return (Var) value;

    value = getGlobalRef(name);

    if (value == null) {
      Var var = new Var();
      _globalMap.put(name, var);
      value = var;
    }
    
    return (Var) value;
  }

  /**
   * Gets a static variable name.
   */
  public final String createStaticName()
  {
    return _quercus.createStaticName();
  }

  /**
   * Gets a static variable
   *
   * @param name the variable name
   */
  public final Var getStaticVar(String name)
  {
    Var var = _staticMap.get(name);

    if (var == null) {
      var = new Var();
      _staticMap.put(name, var);
    }
    
    return var;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetVar(String name)
  {
    _map.remove(name);
    
    return null;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param value the current value of the variable
   */
  public final Var setVar(String name, Value value)
  {
    Var var;

    if (value instanceof Var)
      var = (Var) value;
    else
      var = new Var(value.toValue());
    
    _map.put(name, var);
    
    return var;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetLocalVar(String name)
  {
    _map.remove(name);
    
    return null;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetGlobalVar(String name)
  {
    _globalMap.remove(name);
    
    return null;
  }

  /**
   * Gets a local
   *
   * @param var the current value of the variable
   */
  public static final Value getLocalVar(Value var)
  {
    if (var == null)
      var = new Var();
    
    return var;
  }

  /**
   * Gets a local value
   *
   * @param var the current value of the variable
   */
  public static final Value getLocalValue(Value var)
  {
    if (var != null)
      return var;
    else
      return NullValue.NULL;
  }

  /**
   * Gets a local
   *
   * @param var the current value of the variable
   */
  public static final Value setLocalVar(Value var, Value value)
  {
    value = value.toValue();
    
    if (var instanceof Var)
      var.set(value);
      
    return value;
  }

  /**
   * Gets a value.
   */
  public Var getRef(String name)
  {
    Var var = _map.get(name);

    if (var == null)
      return getSpecialRef(name);

    return var;
  }

  /**
   * Gets a global value.
   */
  public Var getGlobalRef(String name)
  {
    Var var = _globalMap.get(name);

    if (var == null)
      return getSpecialRef(name);

    return var;
  }

  /**
   * Gets a value.
   */
  public Var getSpecialRef(String name)
  {
    Var var = null;

    switch (SPECIAL_VARS.get(name)) {
    case HTTP_POST_VARS:
    case _POST:
      {
	var = new Var();
	
	_globalMap.put(name, var);

	ArrayValue post = new ArrayValueImpl();
	
	if (_post == null)
	  _post = post;
      
	var.set(post);

	Iterator iter = _request.getParameterMap().entrySet().iterator();
	while (iter.hasNext()) {
	  Map.Entry entry = (Map.Entry) iter.next();
      
	  String key = (String) entry.getKey();
	    
	  String []value = (String []) entry.getValue();

	  addFormValue(post, key, value);
	}
      }
      break;
      
    case _GET:
    case _REQUEST:
    case HTTP_GET_VARS:
      {
	var = new Var();

	ArrayValue array = new ArrayValueImpl();

	var.set(array);
	
	_globalMap.put(name, var);

	Iterator iter = _request.getParameterMap().entrySet().iterator();
	while (iter.hasNext()) {
	  Map.Entry entry = (Map.Entry) iter.next();
      
	  String key = (String) entry.getKey();
	    
	  String []value = (String []) entry.getValue();

	  addFormValue(array, key, value);
	}

	if (name.equals("_REQUEST") && _post != null) {
	  for (Map.Entry<Value,Value> entry : _post.entrySet()) {
	    array.put(entry.getKey(), entry.getValue().copy());
	  }
	}

	return var;
      }
	
    case _SERVER:
      {
	var = new Var();
	  
	var.set(new ServerArrayValue(this));

	return var;
      }
	
    case _GLOBAL:
      {
	var = new Var();
	  
	var.set(new GlobalArrayValue(this));

	return var;
      }
    
    case _COOKIE:
      {
	var = new Var();

	ArrayValue array = new ArrayValueImpl();

	Cookie []cookies = _request.getCookies();
	if (cookies != null) {
	  for (int i = 0; i < cookies.length; i++) {
	    Cookie cookie = cookies[i];

	    String value = decodeValue(cookie.getValue());
	  
	    array.append(new StringValue(cookie.getName()),
			 new StringValue(value));
	  }
	}

	var.set(array);
	
	_globalMap.put(name, var);

	return var;
      }
    }

    return var;
  }

  private void addFormValue(ArrayValue array,
			    String key,
			    String []formValue)
  {
    if (key.endsWith("[]")) {
      key = key.substring(0, key.length() - 2);

      Value keyValue = new StringValue(key);
      Value value = array.get(keyValue);
      if (value == null || ! value.isset())
	value = new ArrayValueImpl();

      for (int i = 0; i < formValue.length; i++) {
	value.put(new StringValue(formValue[i]));
      }

      array.put(keyValue, value);
    }
    else {
      array.put(new StringValue(key), new StringValue(formValue[0]));
    }
  }

  private static String decodeValue(String s)
  {
    int len = s.length();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);

      if (ch == '%' && i + 2 < len) {
	int d1 = s.charAt(i + 1);
	int d2 = s.charAt(i + 2);

	int v = 0;

	if ('0' <= d1 && d1 <= '9')
	  v = 16 * (d1 - '0');
	else if ('a' <= d1 && d1 <= 'f')
	  v = 16 * (d1 - 'a' + 10);
	else if ('A' <= d1 && d1 <= 'F')
	  v = 16 * (d1 - 'A' + 10);
	else {
	  sb.append('%');
	  continue;
	}

	if ('0' <= d2 && d2 <= '9')
	  v += (d2 - '0');
	else if ('a' <= d2 && d2 <= 'f')
	  v += (d2 - 'a' + 10);
	else if ('A' <= d2 && d2 <= 'F')
	  v += (d2 - 'A' + 10);
	else {
	  sb.append('%');
	  continue;
	}

	i += 2;
	sb.append((char) v);
      }
      else
	sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Gets a value.
   */
  public Var getVar(String name)
  {
    Var var = getRef(name);

    if (var == null) {
      var = new Var();
      _map.put(name, var);
    }
    
    return var;
  }

  /**
   * Gets a value.
   */
  public Var getGlobalVar(String name)
  {
    Var var = getGlobalRef(name);

    if (var == null) {
      var = new Var();
      _globalMap.put(name, var);
    }
    
    return var;
  }

  /**
   * Sets a value.
   */
  public Value setValue(String name, Value value)
  {
    if (value instanceof Var)
      _map.put(name, (Var) value);
    else {
      Var var = getVar(name);
      var.set(value);
    }

    return value;
  }
  /**
   * Sets a value.  Created at first to hold mysql connection.
   */
  public Value setSpecialValue(String name, Value value)
  {
    _specialMap.put(name,value);

    return value;
  }

  /**
   * Sets a value.
   */
  public Value setGlobalValue(String name, Value value)
  {
    if (value instanceof Var)
      _globalMap.put(name, (Var) value);
    else {
      Var var = getGlobalVar(name);
      var.set(value);
    }

    return value;
  }

  /**
   * Sets the calling function expression.
   */
  public void pushCall(Expr call)
  {
    if (_callStack.length <= _callStackTop) {
      Expr []newStack = new Expr[2 * _callStack.length];
      System.arraycopy(_callStack, 0, newStack, 0, _callStack.length);
      _callStack = newStack;
    }

    _callStack[_callStackTop++] = call;
  }

  /**
   * Pops the top call.
   */
  public Expr popCall()
  {
    return _callStack[--_callStackTop];
  }

  /**
   * Pops the top call.
   */
  public Expr peekCall(int depth)
  {
    if (_callStackTop - depth > 0)
      return _callStack[_callStackTop - depth - 1];
    else
      return null;
  }

  /**
   * Pushes a new environment.
   */
  public HashMap<String,Var> pushEnv(HashMap<String,Var> map)
  {
    HashMap<String,Var> oldEnv = _map;

    _map = map;

    return oldEnv;
  }

  /**
   * Restores the old environment.
   */
  public void popEnv(HashMap<String,Var> oldEnv)
  {
    _map = oldEnv;
  }

  /**
   * Returns the current environment.
   */
  public HashMap<String,Var> getEnv()
  {
    return _map;
  }

  /**
   * Returns the current environment.
   */
  public HashMap<String,Var> getGlobalEnv()
  {
    return _globalMap;
  }

  /**
   * Pushes a new environment.
   */
  public final Value []setFunctionArgs(Value []args)
  {
    Value []oldArgs = _functionArgs;

    _functionArgs = args;

    return oldArgs;
  }

  /**
   * Returns the function args.
   */
  public final Value []getFunctionArgs()
  {
    return _functionArgs;
  }

  /**
   * Returns a constant.
   */
  public Value getConstant(String name)
  {
    Value value = _constMap.get(name);

    if (value != null)
      return value;
    else {
      /* XXX:
      notice(L.l("Converting undefined constant '{0}' to string.",
		 name));
      */

      value = new StringValue(name);

      // XXX:
      _constMap.put(name, value);

      return value;
    }
  }

  /**
   * Returns true if the constant is defined.
   */
  public boolean isDefined(String name)
  {
    return _constMap.get(name) != null;
  }

  /**
   * Removes a constant.
   */
  public Value removeConstant(String name)
  {
    return _constMap.remove(name);
  }

  /**
   * Removes a specialValue
   */
  public Value removeSpecialValue(String name)
  {
    return _specialMap.remove(name);
  }
  
  /**
   * Sets a constant.
   */
  public Value addConstant(String name, Value value)
  {
    Value oldValue = _constMap.get(name);

    if (oldValue != null)
      return oldValue;
    
    _constMap.put(name, value);

    return value;
  }

  /**
   * Returns true if an extension is loaded.
   */
  public boolean isExtensionLoaded(String name)
  {
    return getPhp().isExtensionLoaded(name);
  }

  /**
   * Returns true if an extension is loaded.
   */
  public Value getExtensionFuncs(String name)
  {
    return getPhp().getExtensionFuncs(name);
  }

  /**
   * Returns an option.
   */
  public Value getOption(String name)
  {
    Value value = _optionMap.get(name);

    if (value != null)
      return value;
    else
      return NullValue.NULL;
  }

  /**
   * Sets an option.
   */
  public Value setOption(String name, Value value)
  {
    Value oldValue = _optionMap.put(name, value);

    if (oldValue != null)
      return oldValue;
    else
      return NullValue.NULL;
  }

  /**
   * Returns the default stream resource.
   */
  public StreamContextResource getDefaultStreamContext()
  {
    if (_defaultStreamContext == null)
      _defaultStreamContext = new StreamContextResource();
    
    return _defaultStreamContext;
  }

  /**
   * Finds the java reflection method for the function with
   * the given name.
   *
   * @param name the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction findFunction(String name)
  {
    AbstractFunction fun = _funMap.get(name);

    if (fun != null)
      return fun;

    fun = findFunctionImpl(name);

    if (fun != null) {
      _funMap.put(name, fun);
	
      return fun;
    }
    else
      return _funMap.get(name.toLowerCase());
  }

  /**
   * Finds the java reflection method for the function with
   * the given name.
   *
   * @param name the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction getFunction(String name)
  {
    AbstractFunction fun = findFunction(name);

    if (fun != null)
      return fun;
    
    throw errorException(L.l("'{0}' is an unknown function.", name));
  }

  /**
   * Finds the java reflection method for the function with
   * the given name.
   *
   * @param name the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction getFunction(Value name)
  {
    name = name.toValue();

    if (name instanceof CallbackFunction)
      return ((CallbackFunction) name).getFunction();
      
    AbstractFunction fun = findFunction(name.toString());

    if (fun != null)
      return fun;
    
    throw errorException(L.l("'{0}' is an unknown function.", name));
  }

  /**
   * Finds the java reflection method for the function with
   * the given name.
   *
   * @param name the method name
   * @return the found method or null if no method found.
   */
  private AbstractFunction findFunctionImpl(String name)
  {
    AbstractFunction fun = null;
    
    if (_page != null) {
      fun = _page.findFunction(name);

      if (fun != null)
	return fun;
    }
    
    fun = _quercus.findFunction(name);
    if (fun != null) {
      return fun;
    }

    return fun;
  }

  /**
   * Adds a function, e.g. from an include.
   */
  public Value addFunction(String name, AbstractFunction fun)
    throws Throwable
  {
    AbstractFunction oldFun = findFunction(name);

    if (oldFun != null) {
      throw new Exception(L.l("can't redefine function {0}", name));
    }

    _funMap.put(name, fun);
    _funMap.put(name.toLowerCase(), fun);

    return BooleanValue.TRUE;
  }

  /**
   * Finds the java reflection method for the function with
   * the given name.
   *
   * @param className the class name
   * @param methodName the method name
   *
   * @return the found method or null if no method found.
   */
  public AbstractFunction findMethod(String className, String methodName)
  {
    PhpClass cl = findClass(className);

    if (cl == null) {
      error(L.l("'{0}' is an unknown class.", className));
      return null;
    }
      
    AbstractFunction fun = cl.findFunction(methodName);

    if (fun == null)
      fun = cl.findFunctionLowerCase(methodName.toLowerCase());

    if (fun == null) {
      error(L.l("'{0}::{1}' is an unknown method.",
		className, methodName));
      return null;
    }

    return fun;
  }

  /**
   * Compiles and evaluates the given code
   *
   * @param code the code to evaluate
   *
   * @return the result
   */
  public Value evalCode(String code)
    throws Throwable 
  {
    if (log.isLoggable(Level.FINER))
      log.finer(code);

    Quercus quercus = getPhp();

    PhpProgram program = quercus.parseEvalExpr(code);

    Value value = program.execute(this);

    return value;
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   *
   * @return the function value
   */
  public Value eval(String name)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.eval(this);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   *
   * @return the function value
   */
  public Value eval(String name, Value a0)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.eval(this, a0);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   *
   * @return the function value
   */
  public Value eval(String name, Value a0, Value a1)
    throws Throwable 
  {
    return getFunction(name).eval(this, a0, a1);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   *
   * @return the function value
   */
  public Value eval(String name, Value a0, Value a1, Value a2)
    throws Throwable 
  {
    return getFunction(name).eval(this, a0, a1, a2);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   *
   * @return the function value
   */
  public Value eval(String name, Value a0, Value a1, Value a2, Value a3)
    throws Throwable 
  {
    return getFunction(name).eval(this, a0, a1, a2, a3);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @param a4 the fifth argument
   *
   * @return the function value
   */
  public Value eval(String name, Value a0, Value a1,
		    Value a2, Value a3, Value a4)
    throws Throwable 
  {
    return getFunction(name).eval(this, a0, a1, a2, a3, a4);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param args the arguments
   *
   * @return the function value
   */
  public Value eval(String name, Value []args)
    throws Throwable 
  {
    return getFunction(name).eval(this, args);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   *
   * @return the function value
   */
  public Value evalRef(String name)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   *
   * @return the function value
   */
  public Value evalRef(String name, Value a0)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, a0);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   *
   * @return the function value
   */
  public Value evalRef(String name, Value a0, Value a1)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, a0, a1);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   *
   * @return the function value
   */
  public Value evalRef(String name, Value a0, Value a1, Value a2)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, a0, a1, a2);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   *
   * @return the function value
   */
  public Value evalRef(String name, Value a0, Value a1, Value a2, Value a3)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, a0, a1, a2, a3);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @param a4 the fifth argument
   *
   * @return the function value
   */
  public Value evalRef(String name, Value a0, Value a1,
		    Value a2, Value a3, Value a4)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, a0, a1, a2, a3, a4);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param args the arguments
   *
   * @return the function value
   */
  public Value evalRef(String name, Value []args)
    throws Throwable 
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.evalRef(this, args);
  }

  /**
   * Adds a class, e.g. from an include.
   */
  public void addClass(String name, PhpClass cl)
    throws Throwable
  {
    /*
    PhpClass oldClass = findClass(name);

    if (oldClass != null) {
      throw new Exception(L.l("can't redefine function {0}", name));
    }
    */

    _classMap.put(name, cl);
  }

  /**
   * Adds a class, e.g. from an include.
   */
  public void addClassDef(String name, AbstractClassDef cl)
    throws Throwable
  {
    _classDefMap.put(name, cl);
  }

  /**
   * Creates a stdClass object.
   */
  public Value createObject()
  {
    try {
      return _quercus.getStdClass().newInstance(this);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a Java class defintion.
   */
  public JavaClassDefinition getJavaClassDefinition(String className)
  {
    return _quercus.getJavaClassDefinition(className);
  }

  /**
   * Returns a Java class defintion.
   */
  public Value wrapJava(Object obj)
  {
    if (obj == null)
      return NullValue.NULL;
    else if (obj instanceof Value)
      return (Value) obj;

    JavaClassDefinition def = getJavaClassDefinition(obj.getClass().getName());

    return new JavaValue(obj, def);
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   *
   * @return the found class or null if no class found.
   */
  public PhpClass findClass(String name)
  {
    PhpClass cl = _classMap.get(name);

    if (cl != null)
      return cl;

    cl = findClassImpl(name);

    if (cl != null) {
      _classMap.put(name, cl);
	
      return cl;
    }
    else
      return null;
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   *
   * @return the found class or null if no class found.
   */
  public PhpClass getClass(String name)
  {
    PhpClass cl = findClass(name);

    if (cl != null)
      return cl;
    else
      throw new PhpRuntimeException(L.l("'{0}' is an unknown class.",
					name));
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   *
   * @return the found class or null if no class found.
   */
  private PhpClass findClassImpl(String name)
  {
    /*
    PhpClass cl = null;

    cl = _quercus.findClass(name);
    if (cl != null)
      return cl;

    if (_page != null) {
      cl = _page.findClass(name);

      if (cl != null)
	return cl;

      if (_autoload == null)
	_autoload = findFunction("__autoload");

      if (_autoload != null) {
	try {
	  _autoload.eval(this, new StringValue(name));
	} catch (Throwable e) {
	  throw new RuntimeException(e);
	}
      }
      
      return _classMap.get(name);
    }
    */
    AbstractClassDef classDef = _classDefMap.get(name);

    if (classDef == null)
      classDef = _page.findClass(name);

    if (classDef != null) {
      String parentName	= classDef.getParentName();
      
      PhpClass parent = null;

      if (parentName != null)
	parent = getClass(parentName);

      return new PhpClass(classDef, parent);
    }

    if (_autoload == null)
      _autoload = findFunction("__autoload");

    if (_autoload != null) {
      try {
	_autoload.eval(this, new StringValue(name));
      } catch (Throwable e) {
	throw new RuntimeException(e);
      }
    }

    return null;
  }

  /**
   * Finds the class and method.
   *
   * @param className the class name
   * @param methodName the method name
   *
   * @return the found method or null if no method found.
   */
  public AbstractFunction findFunction(String className, String methodName)
  {
    PhpClass cl = findClass(className);

    if (cl == null)
      throw new PhpRuntimeException(L.l("'{0}' is an unknown class",
					className));

    return cl.findFunction(methodName);
  }

  /**
   * Returns the appropriate callback.
   */
  public Callback createCallback(Value value)
  {
    if (value == null || value.isNull())
      return null;
    
    value = value.toValue();

    if (value instanceof Callback)
      return (Callback) value;
    
    else if (value instanceof StringValue)
      return new CallbackFunction(this, value.toString());

    else if (value instanceof ArrayValue) {
      Value obj = value.get(LongValue.ZERO);
      Value name = value.get(LongValue.ONE);

      if (! (name instanceof StringValue))
	throw new IllegalStateException(L.l("unknown callback name {0}", name));

      if (obj instanceof StringValue) {
	PhpClass cl = findClass(obj.toString());

	if (cl == null)
	  throw new IllegalStateException(L.l("can't find class {0}",
					      obj.toString()));

	return new CallbackFunction(cl.getFunction(name.toString()));
      }
      else {
	return new CallbackObjectMethod(obj, name.toString());
      }
    }
    else
      throw new IllegalStateException(L.l("unknown callback {0}", value));
  }

  /**
   * Evaluates an included file.
   */
  public Value require_once(String include)
    throws Throwable
  {
    return include(getPwd(), include, true);
  }

  /**
   * Evaluates an included file.
   */
  public Value require(String include)
    throws Throwable
  {
    return include(getPwd(), include, false);
  }

  /**
   * Evaluates an included file.
   */
  public Value include(String include)
    throws Throwable
  {
    return include(getPwd(), include, false);
  }

  /**
   * Evaluates an included file.
   */
  public Value include_once(String include)
    throws Throwable
  {
    return include(getPwd(), include, true);
  }

  /**
   * Evaluates an included file.
   */
  public Value include(Path pwd, String include, boolean isOnce)
    throws Throwable
  {
    Path path = lookupInclude(pwd, include);

    if (path == null)
      throw errorException(L.l("'{0}' is not a valid path", include));

    if (isOnce && _includeSet.contains(path))
      return NullValue.NULL;

    _includeSet.add(path);

    PhpPage page = _quercus.parse(path);

    page.importDefinitions(this);

    return page.execute(this);
  }

  /**
   * Looks up the path.
   */
  private Path lookupInclude(Path pwd, String relPath)
  {
    ArrayList<Path> pathList = getIncludePath(pwd);

    for (int i = 0; i < pathList.size(); i++) {
      Path path = pathList.get(i).lookup(relPath);
      
      if (path.canRead())
	return path;
    }

    return null;
  }

  /**
   * Returns the include path.
   */
  private ArrayList<Path> getIncludePath(Path pwd)
  {
    String includePath = getIniString("include_path");

    if (includePath == null)
      includePath = ".";

    if (! includePath.equals(_includePath)) {
      _includePathList = new ArrayList<Path>();

      int head = 0;
      int length = includePath.length();
      int tail;
      while ((tail = includePath.indexOf(':', head)) >= 0) {
	String subpath = includePath.substring(head, tail);
	
	_includePathList.add(pwd.lookup(subpath));

	head = tail + 1;
      }

      String subpath = includePath.substring(head);
	
      _includePathList.add(pwd.lookup(subpath));
      // XXX: can't change
      // _includePath = includePath;
    }

    return _includePathList;
  }

  /**
   * Handles error suppression.
   */
  public Value suppress(int errorMask, Value value)
  {
    setErrorMask(errorMask);
    
    return value;
  }

  /**
   * Handles exit/die
   */
  public Value exit(String msg)
  {
    throw new PhpExitException(msg);
  }

  /**
   * Handles exit/die
   */
  public Value exit()
  {
    throw new PhpExitException();
  }

  /**
   * Handles exit/die
   */
  public Value cast(Class cl, Value value)
  {
    if (value.isNull())
      return null;
    else if (cl.isAssignableFrom(value.getClass()))
      return value;
    else {
      error(L.l("{0} is not assignable to {1}",
		value, cl.getName()));

      return value;
    }
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3,
			    Value a4)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3,
			    Value a4, Value a5)
  {
    return value;
  }

  /**
   * A fatal runtime error.
   */
  public Value error(String msg)
  {
    return error(B_ERROR, msg + getFunctionLocation());
  }

  /**
   * A warning with an exception.
   */
  public Value error(String msg, Throwable e)
  {
    log.log(Level.WARNING, e.toString(), e);
    
    return error(msg);
  }

  /**
   * A fatal runtime error.
   */
  public RuntimeException errorException(String msg)
  {
    String fullMsg = msg + getFunctionLocation();
    
    error(B_ERROR, fullMsg);

    throw new PhpRuntimeException(fullMsg);
  }

  /**
   * A runtime warning.
   */
  public Value warning(String msg)
  {
    return error(B_WARNING, msg + getFunctionLocation());
  }

  /**
   * A warning with an exception.
   */
  public Value warning(String msg, Throwable e)
  {
    log.log(Level.FINE, e.toString(), e);
    
    return warning(msg);
  }

  /**
   * A notice.
   */
  public Value notice(String msg)
  {
    return error(B_NOTICE, msg + getFunctionLocation());
  }

  /**
   * A notice with an exception.
   */
  public Value notice(String msg, Throwable e)
  {
    log.log(Level.FINE, e.toString(), e);
    
    return notice(msg);
  }

  /**
   * A parse error
   */
  public Value parse(String msg)
    throws Exception
  {
    return error(B_PARSE, msg);
  }

  /**
   * A parse error
   */
  public Value compileError(String msg)
  {
    return error(B_COMPILE_ERROR, msg);
  }

  /**
   * A parse warning
   */
  public Value compileWarning(String msg)
  {
    return error(B_COMPILE_WARNING, msg);
  }

  /**
   * Returns the error mask.
   */
  public int getErrorMask()
  {
    return _errorMask;
  }

  /**
   * Sets the error mask.
   */
  public int setErrorMask(int mask)
  {
    int oldMask = _errorMask;

    _errorMask = mask;
    
    return oldMask;
  }

  /**
   * Sets an error handler
   */
  public void setErrorHandler(int mask, Callback fun)
  {
    if ((mask & E_ERROR) != 0)
      _errorHandlers[B_ERROR] = fun;
    
    if ((mask & E_WARNING) != 0)
      _errorHandlers[B_WARNING] = fun;
    
    if ((mask & E_PARSE) != 0)
      _errorHandlers[B_PARSE] = fun;
    
    if ((mask & E_NOTICE) != 0)
      _errorHandlers[B_NOTICE] = fun;
    
    if ((mask & E_USER_ERROR) != 0)
      _errorHandlers[B_USER_ERROR] = fun;
    
    if ((mask & E_USER_WARNING) != 0)
      _errorHandlers[B_USER_WARNING] = fun;
    
    if ((mask & E_USER_NOTICE) != 0)
      _errorHandlers[B_USER_NOTICE] = fun;
    
    if ((mask & E_STRICT) != 0)
      _errorHandlers[B_STRICT] = fun;
  }

  /**
   * Writes an error.
   */
  public Value error(int code, String msg)
  {
    int mask = 1 << code;

      if (code >= 0 && code < _errorHandlers.length &&
	  _errorHandlers[code] != null) {
	try {
	  _errorHandlers[code].eval(this,
				    new LongValue(mask),
				    new StringValue(msg));

	  return NullValue.NULL;
	} catch (RuntimeException e) {
	  throw e;
	} catch (Throwable e) {
	  throw new RuntimeException(e);
	}
      }
      
    if ((_errorMask & mask) != 0) {
      try {
	getOut().println(getLocation() + getCodeName(mask) + msg);
      } catch (IOException e) {
	log.log(Level.FINE, e.toString(), e);
      }
    }

    if ((mask & (E_ERROR|E_CORE_ERROR|E_COMPILE_ERROR|E_USER_ERROR)) != 0) {
      throw new PhpRuntimeException(msg);
    }

    return NullValue.NULL;
  }

  /**
   * Returns the error code name.
   */
  private String getCodeName(int code)
  {
    switch (code) {
    case E_ERROR: return "Fatal Error: ";
    case E_WARNING: return "Warning: ";
    case E_PARSE: return "Parse Error: ";
    case E_NOTICE: return "Notice: ";
    case E_CORE_ERROR: return "Fatal Error: ";
    case E_CORE_WARNING: return "Warning: ";
    case E_COMPILE_ERROR: return "Fatal Error: ";
    case E_COMPILE_WARNING: return "Warning : ";
    case E_USER_ERROR: return "Fatal Error: ";
    case E_USER_WARNING: return "Warning: ";
    case E_USER_NOTICE: return "Notice: ";
    case E_STRICT: return "Notice: ";

    default:
      return String.valueOf("ErrorCode(" + code + ")");
    }
  }

  /**
   * Returns the source of an error line.
   */
  public static String []getSourceLine(Path path, int sourceLine, int length)
  {
    if (path == null)
      return null;
    
    ReadStream is = null;
    
    try {
      is = path.openRead();
      int ch;
      boolean hasCr = false;
      int line = 1;

      while (line < sourceLine) {
	ch = is.read();

	if (ch < 0)
	  return null;
	else if (ch == '\r') {
	  hasCr = true;
	  line++;
	}
	else if (ch == '\n') {
	  if (! hasCr)
	    line++;
	  hasCr = false;
	}
	else
	  hasCr = false;
      }

      String []result = new String[length];

      int i = 0;
      StringBuilder sb = new StringBuilder();
      while (i < length && (ch = is.read()) > 0) {
	if (ch == '\n' && hasCr) {
	  hasCr = false;
	  continue;
	}
	else if (ch == '\r') {
	  hasCr = true;
	  result[i++] = sb.toString();
	  sb.setLength(0);
	}
	else if (ch == '\n') {
	  hasCr = false;
	  result[i++] = sb.toString();
	  sb.setLength(0);
	}
	else {
	  hasCr = false;
	  sb.append((char) ch);
	}
      }

      if (i < length)
	result[i] = sb.toString();

      return result;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      try {
	if (is != null)
	  is.close();
      } catch (IOException e) {
      }
    }    

    return null;
  }

  /**
   * Returns the current location.
   */
  public String getLocation()
  {
    // XXX: need to work with compiled code, too
    Expr call = peekCall(0);

    if (call != null)
      return call.getLocation();
    else
      return "";
  }

  /**
   * Returns the current function.
   */
  public String getFunctionLocation()
  {
    // XXX: need to work with compiled code, too
    Expr call = peekCall(0);

    if (call != null)
      return call.getFunctionLocation();
    else
      return "";
  }

  /**
   * Converts a boolean to the boolean value
   */
  public static Value toValue(boolean value)
  {
    return value ? BooleanValue.TRUE : BooleanValue.FALSE;
  }

  /**
   * Converts a boolean to the boolean value
   */
  public static Value toValue(long value)
  {
    return new LongValue(value);
  }

  /**
   * Converts to a variable
   */
  public static Var toVar(Value value)
  {
    if (value instanceof Var)
      return (Var) value;
    else if (value == null)
      return new Var();
    else
      return new Var(value);
  }

  /**
   * Sets a vield variable
   */
  public static Value setFieldVar(Value oldValue, Value value)
  {
    if (value instanceof Var)
      return value;
    else if (oldValue instanceof Var)
      return new Var(value);
    else
      return value;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2)
  {
    return a2;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2, Value a3)
  {
    return a3;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2, Value a3, Value a4)
  {
    return a4;
  }

  public String toString()
  {
    return "Env[]";
  }
  
  // XXX: 
  public Locale getLocale()
  {
    return Locale.getDefault();
  }

  /**
   * closes all elements in _resourceList
   */
  public void close()
  {
    while (_outputBuffer != null)
      popOutputBuffer();

    if (_session != null && _session.getSize() > 0)
      _quercus.saveSession(this, _session.getId(), _session);
    
    for (ResourceValue resource : _resourceList) {
      try {
	resource.close();
      } catch (Throwable e) {
	log.log(Level.FINER, e.toString(), e);
      }
    }
  }

  static {
    SPECIAL_VARS.put("GLOBALS", _GLOBAL);
    SPECIAL_VARS.put("_SERVER", _SERVER);
    SPECIAL_VARS.put("_GET", _GET);
    SPECIAL_VARS.put("HTTP_GET_VARS", HTTP_GET_VARS);
    SPECIAL_VARS.put("_POST", _POST);
    SPECIAL_VARS.put("HTTP_POST_VARS", HTTP_POST_VARS);
    SPECIAL_VARS.put("_REQUEST", _REQUEST);
    SPECIAL_VARS.put("_COOKIE", _COOKIE);
    SPECIAL_VARS.put("_SESSION", _SESSION);
  }
}

