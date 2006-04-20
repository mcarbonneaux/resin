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

package com.caucho.jsp;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.http.*;
import javax.servlet.*;

import com.caucho.util.L10N;

import com.caucho.log.Log;

import com.caucho.vfs.Path;

import com.caucho.config.ConfigException;

import com.caucho.config.types.PathPatternType;

import com.caucho.server.webapp.Application;

import com.caucho.server.connection.StubServletRequest;
import com.caucho.server.connection.StubServletResponse;

/**
 * Precompiles jsp files.
 */
public class JspPrecompileListener extends JspPrecompileResource
  implements ServletContextListener {
  private static final L10N L = new L10N(JspPrecompileListener.class);
  private static final Logger log = Log.open(JspPrecompileListener.class);
  
  /**
   * Adds a new extension to precompile.
   */
  public void addExtension(String extension)
    throws ConfigException
  {
    if (extension.startsWith("."))
      extension = extension.substring(1);

    createFileset().addInclude(new PathPatternType("**/*." + extension));
  }
  
  public void contextInitialized(ServletContextEvent event)
  {
    try {
      setApplication((Application) event.getServletContext());
      
      init();
    
      start();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  public void contextDestroyed(ServletContextEvent event)
  {
  }
}
