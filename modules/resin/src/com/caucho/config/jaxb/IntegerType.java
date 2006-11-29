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

package com.caucho.config.jaxb;

import com.caucho.config.ConfigException;
import com.caucho.config.NodeBuilder;
import com.caucho.config.TypeStrategy;
import com.caucho.util.L10N;

import org.w3c.dom.Node;

/**
 * Strategy for configuring types.
 */
public class IntegerType extends TypeStrategy {
  protected static final L10N L = new L10N(IntegerType.class);

  public static final IntegerType TYPE = new IntegerType();

  private IntegerType()
  {
  }

  /**
   * Returns the type name.
   */
  public String getTypeName()
  {
    return getClass().getName();
  }

  /**
   * Returns the type's configured value
   *
   * @param builder the context builder
   * @param node the configuration node
   * @param parent
   */
  public Object configure(NodeBuilder builder, Node node, Object parent)
    throws ConfigException
  {
    String textValue = node.getTextContent().trim();
    
    long value = builder.evalLong(textValue);

    return new Integer((int) value);
  }
}
