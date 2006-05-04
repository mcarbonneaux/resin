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

package com.caucho.quercus.expr;

import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.ArrayValue;
import com.caucho.quercus.Location;

/**
 * Represents a PHP each expression.
 */
public class EachExpr extends UnaryExpr {

  public EachExpr(Location location, Expr expr)
    throws IOException
  {
    super(location, expr);
  }

  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value eval(Env env)
    throws Throwable
  {
    Value value = getExpr().eval(env);

    if (value instanceof ArrayValue) {
      ArrayValue array = (ArrayValue) value;

      return array.each();
    }
    else
      return BooleanValue.FALSE;
  }

  //
  // Java code generation
  //

  /**
   * Generates code to evaluate the expression
   *
   * @param out the writer to the Java source code.
   */
  /*
  public void generate(PhpWriter out)
    throws IOException
  {
    String var = "_quercus_list";
    
    out.print("env.first(" + var + " = ");
    _value.generate(out);

    out.print(")");
  }
  */
}

