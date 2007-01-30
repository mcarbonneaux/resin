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
 * @author Scott Ferguson;
 */

package com.caucho.jsf.cfg;

import com.caucho.config.*;
import com.caucho.util.L10N;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

import javax.faces.context.*;

public class ListPropertyBeanProgram extends BeanProgram
{
  private static final Logger log
    = Logger.getLogger(ListPropertyBeanProgram.class.getName());
  private static final L10N L = new L10N(ListPropertyBeanProgram.class);

  private Method _getter;
  private Method _setter;
  private AbstractValue _value;

  public ListPropertyBeanProgram(Method getter, Method setter,
				AbstractValue value)
  {
    _getter = getter;
    _setter = setter;
    _value = value;
  }

  /**
   * Configures the object.
   */
  public void configure(FacesContext context, Object bean)
    throws ConfigException
  {
    try {
      List list = null;

      if (_getter != null)
	list = (List) _getter.invoke(bean);

      if (list == null && _setter != null) {
	list = new ArrayList();
	_setter.invoke(bean, list);
      }

      if (list != null)
	list.add(_value.getValue(context));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }
}
