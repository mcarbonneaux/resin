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
 * afloat with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.config.jaxb;

import java.util.*;
import java.lang.reflect.*;

import javax.el.*;
import javax.xml.bind.annotation.adapters.*;

import org.w3c.dom.Node;

import com.caucho.util.*;

import com.caucho.el.*;

import com.caucho.config.*;
import com.caucho.xml.*;

public class AdapterProperty extends JaxbProperty {
  private final TypeStrategy _typeMarshal;
  private final XmlAdapter _adapter;
  private final Method _setter;
  
  public AdapterProperty(Method setter,
			 TypeStrategy typeMarshal,
			 XmlAdapter adapter)
  {
    _typeMarshal = typeMarshal;
    _adapter = adapter;
    
    _setter = setter;

    setter.setAccessible(true);
  }
 
  /**
   * Configures the parent object with the given node.
   *
   * @param builder the calling node builder (context)
   * @param bean the bean to be configured
   * @param name the name of the property
   * @param node the configuration node for the value
   */
  public void configureAttribute(NodeBuilder builder,
				 Object bean,
				 QName name,
				 String value)
    throws ConfigException
  {
  }
 
  /**
   * Configures the parent object with the given node.
   *
   * @param builder the calling node builder (context)
   * @param bean the bean to be configured
   * @param name the name of the property
   * @param node the configuration node for the value
   */
  public void configureElement(NodeBuilder builder,
			       Object bean,
			       QName name,
			       Node node)
    throws ConfigException
  {
    try {
      Object value = _typeMarshal.configure(builder, node, bean);

      Object bound = _adapter.unmarshal(value);

      _setter.invoke(bean, bound);
    } catch (RuntimeException e) {
      throw e;
    } catch (InvocationTargetException e) {
      throw builder.error(e.getCause(), node);
    } catch (Exception e) {
      throw builder.error(e, node);
    }
  }
}
