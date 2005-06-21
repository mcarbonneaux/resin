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

package com.caucho.amber.type;

import java.io.IOException;

import java.sql.Types;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.caucho.util.L10N;

import com.caucho.java.JavaWriter;

import com.caucho.amber.AmberManager;

/**
 * The primitive int type.
 */
public class PrimitiveIntType extends PrimitiveType {
  private static final L10N L = new L10N(PrimitiveIntType.class);

  private static final PrimitiveIntType INT_TYPE = new PrimitiveIntType();

  private PrimitiveIntType()
  {
  }

  /**
   * Returns the boolean type.
   */
  public static PrimitiveIntType create()
  {
    return INT_TYPE;
  }

  /**
   * Returns the type name.
   */
  public String getName()
  {
    return "int";
  }

  /**
   * Returns the type as a foreign key.
   */
  public Type getForeignType()
  {
    return IntegerType.create();
  }

  /**
   * Generates the type for the table.
   */
  public String generateCreateTableSQL(AmberManager manager, int length, int precision, int scale)
  {
    return manager.getCreateTableSQL(Types.INTEGER, length, precision, scale);
  }

  /**
   * Generates a string to load the property.
   */
  public int generateLoad(JavaWriter out, String rs,
			  String indexVar, int index)
    throws IOException
  {
    out.print(rs + ".getInt(" + indexVar + " + " + index + ")");

    return index + 1;
  }

  /**
   * Generates a string to load the property.
   */
  public int generateLoadForeign(JavaWriter out, String rs,
				 String indexVar, int index)
    throws IOException
  {
    out.print("com.caucho.amber.type.PrimitiveIntType.toForeignInt(" +
	      rs + ".getInt(" + indexVar + " + " + index + "), " +
	      rs + ".wasNull())");

    return index + 1;
  }

  /**
   * Generates a string to set the property.
   */
  public void generateSet(JavaWriter out, String pstmt,
			  String index, String value)
    throws IOException
  {
    out.println(pstmt + ".setInt(" + index + "++, " + value + ");");
  }

  /**
   * Generates a string to set the property.
   */
  public void generateSetNull(JavaWriter out, String pstmt, String index)
    throws IOException
  {
    out.println(pstmt + ".setNull(" + index + "++, java.sql.Types.INTEGER);");
  }

  /**
   * Converts to an object.
   */
  public String toObject(String value)
  {
    return "new Integer(" + value + ")";
  }
  
  /**
   * Converts the value.
   */
  public String generateCastFromObject(String value)
  {
    return "((Number) " + value + ").intValue()";
  }

  /**
   * Converts a value to a int.
   */
  public static Integer toForeignInt(int value, boolean wasNull)
  {
    // XXX: backwards compat
    if (wasNull || value == 0)
      return null;
    else
      return new Integer(value);
  }

  /**
   * Gets the value.
   */
  public Object getObject(ResultSet rs, int index)
    throws SQLException
  {
    int v = rs.getInt(index);
    
    return rs.wasNull() ? null : new Integer(v);
  }

  /**
   * Converts to an object.
   */
  public Object toObject(long value)
  {
    return new Integer((int) value);
  }
}
