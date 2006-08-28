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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.el;

import java.beans.FeatureDescriptor;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.el.*;

import com.caucho.util.NullIterator;

import com.caucho.log.Log;

/**
 * Abstract variable resolver.  Supports chaining and the "Var"
 * special variable.
 */
public class AbstractVariableResolver extends ELResolver
{
  private static final Logger log
    = Logger.getLogger(AbstractVariableResolver.class.getName());
  
  private ELResolver _next;
  
  /**
   * Creates the resolver
   */
  public AbstractVariableResolver()
  {
  }
  
  /**
   * Creates the resolver
   */
  public AbstractVariableResolver(ELResolver next)
  {
    _next = next;
  }

  /**
   * Returns the next resolver.
   */
  public ELResolver getNext()
  {
    return _next;
  }
  
  /**
   * Returns the named variable value.
   */
  @Override
  public Object getValue(ELContext context,
			 Object base,
			 Object property)
  {
    return null;
  }

  //
  // ELResolver stubs
  //


  public Class<?> getCommonPropertyType(ELContext context,
					Object base)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Iterator<FeatureDescriptor>
    getFeatureDescriptors(ELContext context, Object base)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Class<?> getType(ELContext context,
			  Object base,
			  Object property)
  {
    Object value = getValue(context, base, property);

    if (value == null)
      return null;
    else
      return value.getClass();
  }

  public boolean isReadOnly(ELContext context,
			    Object base,
			    Object property)
    throws PropertyNotFoundException,
	   ELException
  {
    return true;
  }

  public void setValue(ELContext context,
		       Object base,
		       Object property,
		       Object value)
    throws PropertyNotFoundException,
	   PropertyNotWritableException,
	   ELException
  {
  }

  public String toString()
  {
    return "AbstractVariableResolver[]";
  }
}
