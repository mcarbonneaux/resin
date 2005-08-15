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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.config;

import java.util.HashMap;

import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.el.ELException;

import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentClassLoader;

import com.caucho.el.EL;
import com.caucho.el.AbstractVariableResolver;

/**
 * Creates a variable resolver based on the classloader.
 */
public class ConfigVariableResolver extends AbstractVariableResolver {
  private HashMap<String,Object> _varMap = new HashMap<String,Object>();
  
  private ClassLoader _originalLoader;
  private ClassLoader _configureClassLoader;
  /**
   * Creates the resolver
   */
  public ConfigVariableResolver()
  {
    this(Thread.currentThread().getContextClassLoader());
  }
  
  /**
   * Creates the resolver
   */
  public ConfigVariableResolver(ClassLoader loader)
  {
    super(EL.getEnvironment(loader));

    _originalLoader = loader;
  }

  /**
   * Gets the configure environment class loader.
   */
  ClassLoader getConfigLoader()
  {
    return _configureClassLoader;
  }

  /**
   * Sets the configure environment class loader.
   */
  void setConfigLoader(ClassLoader loader)
  {
    // server/13co
    if (_configureClassLoader == null)
      _configureClassLoader = loader;
  }
  
  /**
   * Returns the named variable value.
   */
  public Object resolveVariable(String var)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    for (;
	 loader != _originalLoader && loader != null;
	 loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	Object value = EL.getLevelVar(var, loader);

	if (value == EL.NULL)
	  return null;
	else if (value != null)
	  return value;
      }
    }

    if (loader == _configureClassLoader) {
      Object value = EL.getLevelVar(var, loader);

      if (value == EL.NULL)
	return null;
      else if (value != null)
	return value;
    }
    
    Object value = _varMap.get(var);

    if (value == EL.NULL)
      return null;
    else if (value != null)
      return value;

    value = Environment.getAttribute(var, _originalLoader);

    if (value == EL.NULL)
      return null;
    else if (value != null)
      return value;
    else {
      return super.resolveVariable(var);
    }
  }
  
  /**
   * Sets the value for the named variable.
   */
  public Object put(String name, Object value)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    for (;
	 loader != _originalLoader && loader != null;
	 loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	return EL.putVar(name, value, loader);
      }
    }

    if (loader == _configureClassLoader) {
      return EL.putVar(name, value, loader);
    }
    
    return _varMap.put(name, value);
  }

  public String toString()
  {
    return "ConfigVariableResolver[]";
  }
}
