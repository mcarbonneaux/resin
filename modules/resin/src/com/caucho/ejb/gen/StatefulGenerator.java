/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.ejb.gen;

import java.io.IOException;
import java.util.ArrayList;

import com.caucho.config.gen.ApiClass;
import com.caucho.config.gen.View;
import com.caucho.inject.Module;
import com.caucho.java.JavaWriter;
import javax.ejb.Stateful;

/**
 * Generates the skeleton for a stateful bean.
 */
@Module
public class StatefulGenerator extends SessionGenerator {
  public StatefulGenerator(String ejbName, ApiClass ejbClass,
                           ArrayList<ApiClass> localApi,
                           ArrayList<ApiClass> remoteApi)
  {
    super(ejbName, ejbClass, localApi, remoteApi, 
          Stateful.class.getSimpleName());
  }

  @Override
  public boolean isStateless()
  {
    return false;
  }

  @Override
  protected View createLocalView(ApiClass api)
  {
    return new StatefulView(this, api);
  }

  @Override
  protected View createRemoteView(ApiClass api)
  {
    return new StatefulView(this, api);
  }

  /**
   * Scans for the @Local interfaces
   */
  @Override
  protected ArrayList<ApiClass> introspectLocalDefault() 
  {
    ArrayList<ApiClass> apiClass = new ArrayList<ApiClass>();

    apiClass.add(getBeanClass());

    return apiClass;
  }

  /**
   * Generates the stateful session bean
   */
  @Override
  public void generate(JavaWriter out) throws IOException
  {
    generateTopComment(out);

    out.println();
    out.println("package " + getPackageName() + ";");

    out.println();
    out.println("import com.caucho.config.*;");
    out.println("import com.caucho.ejb.*;");
    out.println("import com.caucho.ejb.session.*;");
    out.println();
    out.println("import javax.ejb.*;");
    out.println("import javax.transaction.*;");

    out.println();
    out.println("public class " + getClassName());
    out.println("  extends StatefulContext");
    out.println("{");
    out.pushDepth();
    
    // XXX: temp for JSR-299 TCK
    out.println("static java.lang.reflect.Constructor _ctor;");
    out.println("static {");
    out.println("try {");
    out.print("Class cl = Class.forName(\"");
    out.print(getBeanClass().getName());
    out.println("\");");
    // out.println("cl.setAccessible(true);");
    out.println("java.lang.reflect.Constructor ctor = null;");
    out.println("for (java.lang.reflect.Constructor tCtor : cl.getDeclaredConstructors()) {");
    out.println("  if (tCtor.getParameterTypes().length == 0)");
    out.println("    ctor = tCtor;");
    out.println("}");
    out.println("_ctor = ctor;");
    out.println("_ctor.setAccessible(true);");
    out.println("} catch (Exception e) {");
    out.println("  e.printStackTrace();");
    out.println("}");
    out.println("}");

    out.println();
    out.println("public " + getClassName() + "(StatefulManager server)");
    out.println("{");
    out.pushDepth();

    out.println("super(server);");

    for (View view : getViews()) {
      view.generateContextHomeConstructor(out);
    }

    out.popDepth();
    out.println("}");

    out.println();
    out.println("public " + getClassName() + "(" + getClassName()
                + " context)");
    out.println("{");
    out.pushDepth();

    out.println("super(context.getStatefulManager());");

    generateContextObjectConstructor(out);

    out.popDepth();
    out.println("}");

    for (View view : getViews()) {
      view.generateContextPrologue(out);
    }

    generateCreateProvider(out);
    generateViews(out);

    generateDependency(out);

    out.popDepth();
    out.println("}");
  }

  protected void generateCreateProvider(JavaWriter out) throws IOException
  {
    out.println();
    out.println("@Override");
    out.println("public StatefulProvider getProvider(Class api)");
    out.println("{");
    out.pushDepth();

    for (View view : getViews()) {
      StatefulView sView = (StatefulView) view;

      sView.generateCreateProvider(out, "api");
    }

    out.println();
    out.println("return super.getProvider(api);");

    out.popDepth();
    out.println("}");
  }

  /**
   * Creates any additional code in the constructor
   */
  public void generateContextObjectConstructor(JavaWriter out)
      throws IOException
  {
    for (View view : getViews()) {
      view.generateContextObjectConstructor(out);
    }
  }

  @Override
  protected void generateContext(JavaWriter out)
  {
  }
}
