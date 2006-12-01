/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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

package com.caucho.quercus.servlet;

import com.caucho.config.ConfigException;
import com.caucho.quercus.Quercus;
import com.caucho.quercus.QuercusDieException;
import com.caucho.quercus.QuercusExitException;
import com.caucho.quercus.QuercusLineRuntimeException;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.QuercusValueException;
import com.caucho.quercus.module.QuercusModule;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.server.connection.CauchoResponse;
import com.caucho.server.session.SessionManager;
import com.caucho.server.webapp.Application;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;
import com.caucho.vfs.WriteStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet to call PHP through javax.script.
 */
public class QuercusServlet
  extends HttpServlet
{
  private static final L10N L = new L10N(QuercusServlet.class);
  private static final Logger log
    = Logger.getLogger(QuercusServlet.class.getName());

  private Quercus _quercus;

  private boolean _isCompileSet;

  /**
   * Set true if quercus should be compiled into Java.
   */
  public void setCompile(String isCompile)
    throws ConfigException
  {
    _isCompileSet = true;

    if ("false".equals(isCompile) && _quercus == null)
      _quercus = new Quercus();

    Quercus quercus = getQuercus();

    if ("true".equals(isCompile) || "".equals(isCompile)) {
      quercus.setCompile(true);
      quercus.setLazyCompile(false);
    } else if ("false".equals(isCompile)) {
      quercus.setCompile(false);
      quercus.setLazyCompile(false);
    } else if ("lazy".equals(isCompile)) {
      quercus.setLazyCompile(true);
    } else
      throw new ConfigException(L.l(
        "'{0}' is an unknown compile value.  Values are 'true', 'false', or 'lazy'.",
        isCompile));
  }

  /**
   * Set the default data source.
   */
  public void setDatabase(DataSource database)
    throws ConfigException
  {
    if (database == null)
      throw new ConfigException(L.l("invalid database"));

    getQuercus().setDatabase(database);
  }

  /**
   * Sets the strict mode.
   */
  public void setStrict(boolean isStrict)
  {
    getQuercus().setStrict(isStrict);
  }

  /**
   * Adds a quercus module.
   */
  public void addModule(QuercusModule module)
    throws ConfigException
  {
    getQuercus().addModule(module);
  }

  /**
   * Adds a quercus class.
   */
  public void addClass(PhpClassConfig classConfig)
    throws ConfigException
  {
    getQuercus().addJavaClass(classConfig.getName(), classConfig.getType());
  }

  /**
   * Adds a quercus class.
   */
  public void addImplClass(PhpClassConfig classConfig)
    throws ConfigException
  {
    getQuercus().addImplClass(classConfig.getName(), classConfig.getType());
  }

  /**
   * Adds a quercus.ini configuration
   */
  public PhpIni createPhpIni()
    throws ConfigException
  {
    return new PhpIni(getQuercus());
  }

  /**
   * Adds a $_SERVER configuration
   */
  public ServerEnv createServerEnv()
    throws ConfigException
  {
    return new ServerEnv(getQuercus());
  }

  /**
   * Adds a quercus.ini configuration
   */
  public void setIniFile(Path path)
  {
    getQuercus().setIniFile(path);
  }

  /**
   * Sets the script encoding.
   */
  public void setScriptEncoding(String encoding)
    throws ConfigException
  {
    getQuercus().setScriptEncoding(encoding);
  }

  /**
   * Gets the script manager.
   */
  public void init()
    throws ServletException
  {
    initImpl();
  }

  private void initImpl()
   // throws ServletException
  {
    getQuercus();

    if (! _isCompileSet) {
      getQuercus().setLazyCompile(true);

      // XXX: for validating QA
      // throw new ServletException("compile must be set.");
    }
  }

  /**
   * Service.
   */
  public void service(HttpServletRequest request,
                      HttpServletResponse response)
    throws ServletException, IOException
  {
    try {
      Path path = getPath(request);

      QuercusPage page = getQuercus().parse(path);

      WriteStream ws;
      
      // XXX: check if correct.  PHP doesn't expect the lower levels
      // to deal with the encoding, so this may be okay
      if (response instanceof CauchoResponse) {
	ws = Vfs.openWrite(((CauchoResponse) response).getResponseStream());
      } else {
	OutputStream out = response.getOutputStream();

	ws = Vfs.openWrite(out);
      }

      Env env = new Env(getQuercus(), page, ws, request, response);
      try {
        env.setGlobalValue("request", env.wrapJava(request));
        env.setGlobalValue("response", env.wrapJava(request));

        env.start();

	String prepend = env.getIniString("auto_prepend_file");
	if (prepend != null) {
	  QuercusPage prependPage = getQuercus().parse(env.lookup(prepend));
	  prependPage.executeTop(env);
	}

        page.executeTop(env);

	String append = env.getIniString("auto_append_file");
	if (append != null) {
	  QuercusPage appendPage = getQuercus().parse(env.lookup(append));
	  appendPage.executeTop(env);
	}
     //   return;
      }
      catch (QuercusExitException e) {
        throw e;
      }
      catch (QuercusLineRuntimeException e) {
        log.log(Level.FINE, e.toString(), e);

      //  return;
      }
      catch (QuercusValueException e) {
        log.log(Level.FINE, e.toString(), e);
	
	ws.println(e.toString());

      //  return;
      }
      catch (Throwable e) {
        if (response.isCommitted())
          e.printStackTrace(ws.getPrintWriter());

        ws = null;

        throw e;
      }
      finally {
        env.close();

        // don't want a flush for an exception
        if (ws != null)
          ws.close();
      }
    }
    catch (QuercusDieException e) {
      log.log(Level.FINE, e.toString(), e);
      // normal exit
    }
    catch (QuercusExitException e) {
      log.log(Level.FINER, e.toString(), e);
      // normal exit
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new ServletException(e);
    }
  }

  Path getPath(HttpServletRequest req)
  {
    String scriptPath = req.getServletPath();
    String pathInfo = req.getPathInfo();

    Path pwd = Vfs.lookup();

    Path path = pwd.lookup(req.getRealPath(scriptPath));

    if (path.isFile())
      return path;

    // XXX: include

    String fullPath;
    if (pathInfo != null)
      fullPath = scriptPath + pathInfo;
    else
      fullPath = scriptPath;

    return Vfs.lookup().lookup(req.getRealPath(fullPath));
  }

  /**
   * Returns the Quercus instance.
   */
  private Quercus getQuercus()
  {
    synchronized (this) {
      if (_quercus == null) {
	try {
	  Class cl = Class.forName("com.caucho.quercus.ProQuercus");
	  _quercus = (Quercus) cl.newInstance();
	} catch (Exception e) {
	  log.log(Level.FINEST, e.toString(), e);
	}

	if (_quercus == null)
	  _quercus = new Quercus();
      }
    }

    if (_quercus.getQuercusSessionManager().getSessionManager() == null) {
      Application app = (Application) getServletContext();

      if (app != null) {
        SessionManager sm = app.getSessionManager();

        _quercus.getQuercusSessionManager().setSessionManager(sm, app);
      }
    }

    return _quercus;
  }

  /**
   * Gets the script manager.
   */
  public void destroy()
  {
    _quercus.close();
  }

  public static class PhpIni {
    private Quercus _quercus;

    PhpIni(Quercus quercus)
    {
      _quercus = quercus;
    }

    /**
     * Sets an arbitrary property.
     */
    public void put(String key, String value)
    {
      _quercus.setIni(key, value);
    }
  }

  public static class ServerEnv {
    private Quercus _quercus;

    ServerEnv(Quercus quercus)
    {
      _quercus = quercus;
    }

    /**
     * Sets an arbitrary property.
     */
    public void put(String key, String value)
    {
      _quercus.setServerEnv(key, value);
    }
  }
}

