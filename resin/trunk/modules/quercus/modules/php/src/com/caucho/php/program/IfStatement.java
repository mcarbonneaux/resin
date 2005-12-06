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

package com.caucho.php.program;

import java.io.IOException;

import java.util.HashSet;

import com.caucho.php.env.Env;
import com.caucho.php.env.Value;

import com.caucho.php.expr.Expr;
import com.caucho.php.expr.VarExpr;

import com.caucho.php.gen.PhpWriter;

/**
 * Represents an if statement.
 */
public class IfStatement extends Statement {
  private final Expr _test;
  private final Statement _trueBlock;
  private final Statement _falseBlock;

  public IfStatement(Expr test, Statement trueBlock, Statement falseBlock)
  {
    _test = test;
    _trueBlock = trueBlock;
    _falseBlock = falseBlock;
  }

  /**
   * Executes the 'if' statement, returning any value.
   */
  public Value execute(Env env)
    throws Throwable
  {
    if (_test.evalBoolean(env)) {
      return _trueBlock.execute(env);
    }
    else if (_falseBlock != null) {
      return _falseBlock.execute(env);
    }
    else
      return null;
  }

  //
  // Java code generation
  //

  /**
   * Analyze the statement
   */
  public boolean analyze(AnalyzeInfo info)
  {
    _test.analyze(info);

    AnalyzeInfo trueBlockInfo = info.copy();
    AnalyzeInfo falseBlockInfo = info.copy();
    
    boolean isComplete = false;

    if (_trueBlock.analyze(trueBlockInfo)) {
      info.merge(trueBlockInfo);
      isComplete = true;
    }
    
    if (_falseBlock != null) {
      if (_falseBlock.analyze(falseBlockInfo)) {
	info.merge(falseBlockInfo);
	isComplete = true;
      }
    }
    else
      isComplete = true;

    return isComplete;
  }

  /**
   * Returns true if the statement can fallthrough.
   */
  public int fallThrough()
  {
    if (_falseBlock == null)
      return FALL_THROUGH;
    else
      return (_trueBlock.fallThrough() & _falseBlock.fallThrough());
  }

  /**
   * Generates the Java code for the statement.
   *
   * @param out the writer to the generated Java source.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    out.print("if (");
    _test.generateBoolean(out);
    out.println(") {");
    out.pushDepth();
    _trueBlock.generate(out);
    out.popDepth();
    out.println("}");

    if (_falseBlock != null) {
      out.println("else {");
      out.pushDepth();
      _falseBlock.generate(out);
      out.popDepth();
      out.println("}");
    }
  }
  
  public String toString()
  {
    return "Statement[]";
  }
}

