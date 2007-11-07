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
 * @author Rodrigo Westrupp
 */

package com.caucho.amber.type;

import com.caucho.amber.field.*;
import com.caucho.amber.manager.AmberPersistenceUnit;
import com.caucho.amber.table.Column;
import com.caucho.amber.table.Table;
import com.caucho.bytecode.JClass;
import com.caucho.bytecode.JClassDependency;
import com.caucho.bytecode.JField;
import com.caucho.bytecode.JMethod;
import com.caucho.config.ConfigException;
import com.caucho.java.JavaWriter;
import com.caucho.make.ClassDependency;
import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;
import com.caucho.vfs.PersistentDependency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Represents a stateful type:
 * embeddable, entity or mapped-superclass.
 */
abstract public class AbstractStatefulType extends AbstractEnhancedType {
  private static final Logger log = Logger.getLogger(AbstractStatefulType.class.getName());
  private static final L10N L = new L10N(AbstractStatefulType.class);

  private boolean _isFieldAccess;

  private ArrayList<AmberField> _fields = new ArrayList<AmberField>();

  private ArrayList<AmberField> _mappedSuperclassFields
    = new ArrayList<AmberField>();

  private volatile boolean _isConfigured;

  private ArrayList<PersistentDependency> _dependencies
    = new ArrayList<PersistentDependency>();

  private HashMap<String,String> _completionFields
    = new HashMap<String,String>();

  private Column _discriminator;

  public AbstractStatefulType(AmberPersistenceUnit amberPersistenceUnit)
  {
    super(amberPersistenceUnit);
  }

  /**
   * Set true for field-access.
   */
  public void setFieldAccess(boolean isFieldAccess)
  {
    _isFieldAccess = isFieldAccess;
  }

  /**
   * Set true for field-access.
   */
  public boolean isFieldAccess()
  {
    return _isFieldAccess;
  }

  /**
   * Returns true for an embeddable
   */
  public boolean isEmbeddable()
  {
    return false;
  }

  /**
   * Returns the discriminator.
   */
  public Column getDiscriminator()
  {
    return _discriminator;
  }

  /**
   * Sets the discriminator.
   */
  public void setDiscriminator(Column discriminator)
  {
    _discriminator = discriminator;
  }

  /**
   * Returns the java type.
   */
  public String getJavaTypeName()
  {
    return getInstanceClassName();
  }

  /**
   * Adds a new field.
   */
  public void addField(AmberField field)
  {
    _fields.add(field);
    Collections.sort(_fields, new AmberFieldCompare());
  }

  /**
   * Returns the fields.
   */
  public ArrayList<AmberField> getFields()
  {
    return _fields;
  }

  /**
   * Returns the field with a given name.
   */
  public AmberField getField(String name)
  {
    for (int i = 0; i < _fields.size(); i++) {
      AmberField field = _fields.get(i);

      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  /**
   * Adds a mapped superclass field.
   */
  public void addMappedSuperclassField(AmberField field)
  {
    if (_mappedSuperclassFields.contains(field))
      return;

    _mappedSuperclassFields.add(field);
    Collections.sort(_mappedSuperclassFields, new AmberFieldCompare());
  }

  /**
   * Returns the mapped superclass fields.
   */
  public ArrayList<AmberField> getMappedSuperclassFields()
  {
    return _mappedSuperclassFields;
  }

  /**
   * Returns the mapped superclass field with a given name.
   */
  public AmberField getMappedSuperclassField(String name)
  {
    for (int i = 0; i < _mappedSuperclassFields.size(); i++) {
      AmberField field = _mappedSuperclassFields.get(i);

      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  /**
   * Sets the bean class.
   */
  public void setBeanClass(JClass beanClass)
  {
    super.setBeanClass(beanClass);

    addDependency(_beanClass);
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(Class cl)
  {
    addDependency(new ClassDependency(cl));
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(JClass cl)
  {
    addDependency(new JClassDependency(cl));
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(PersistentDependency depend)
  {
    if (! _dependencies.contains(depend))
      _dependencies.add(depend);
  }

  /**
   * Gets the dependency.
   */
  public ArrayList<PersistentDependency> getDependencies()
  {
    return _dependencies;
  }

  /**
   * Adds a new completion field.
   */
  public void addCompletionField(String name)
  {
    _completionFields.put(name, name);
  }

  /**
   * Returns true if and only if it has the completion field.
   */
  public boolean containsCompletionField(String completionField)
  {
    return _completionFields.containsKey(completionField);
  }

  /**
   * Remove all completion fields.
   */
  public void removeAllCompletionFields()
  {
    _completionFields.clear();
  }

  /**
   * Set true if configured.
   */
  public boolean startConfigure()
  {
    synchronized (this) {
      if (_isConfigured)
        return false;

      _isConfigured = true;

      return true;
    }
  }

  /**
   * Initialize the type.
   */
  public void init()
    throws ConfigException
  {
  }

  /**
   * Converts the value.
   */
  public String generateCastFromObject(String value)
  {
    return "((" + getInstanceClassName() + ") " + value + ")";
  }

  /**
   * Generates a string to load the field.
   */
  public int generateLoad(JavaWriter out,
                          String rs,
                          String indexVar,
                          int index,
                          int loadGroupIndex,
                          ArrayList<AmberField> overriddenFields)
    throws IOException
  {
    if (overriddenFields == null && getDiscriminator() != null) {
      RelatedType parent = null;

      if (this instanceof RelatedType)
        parent = ((RelatedType) this).getParentType();

      boolean isAbstractParent = getPersistenceUnit().isJPA()
        && (parent == null || parent.getBeanClass().isAbstract());

      if (loadGroupIndex == 0 || isAbstractParent)
        index++;
    }

    ArrayList<AmberField> fields = null;

    if (this instanceof RelatedType) {
      fields = getMappedSuperclassFields();

      RelatedType parent = ((RelatedType) this).getParentType();

      if (parent != null) {
        // jpa/0l14
        index = parent.generateLoad(out, rs, indexVar, index,
                                    loadGroupIndex, fields);
      }
    }

    for (int i = 0; i < 2; i++) {
      if (fields == null) {
        fields = getFields();
        continue;
      }

      for (int j = 0; j < fields.size(); j++) {
        AmberField field = fields.get(j);

        // jpa/0l14, jpa/0ge3
        if (overriddenFields != null) {
          boolean isOverridden = false;

          for (AmberField amberField : overriddenFields) {
            if (amberField.getName().equals(field.getName()))
              isOverridden = true;
          }

          if (isOverridden)
            continue;
        }

        // ejb/0602
        if (getPersistenceUnit().isJPA()) {
          if (field instanceof EntityManyToOneField) {
            ((EntityManyToOneField) field).init((RelatedType) this);
          }
        }

        // jpa/0gg3
        if (field.getLoadGroupIndex() == loadGroupIndex)
          index = field.generateLoad(out, rs, indexVar, index);
      }

      fields = getFields();
    }

    return index;
  }

  /**
   * Generates the select clause for a load.
   */
  abstract public String generateLoadSelect(Table table, String id);

  /**
   * Generates the select clause for a load.
   */
  public String generateLoadSelect(Table table,
                                   String id,
                                   int loadGroup)
  {
    return generateLoadSelect(table, id, loadGroup, false);
  }

  /**
   * Generates the select clause for a load.
   */
  public String generateLoadSelect(Table table,
                                   String id,
                                   int loadGroup,
                                   boolean hasSelect)
  {
    CharBuffer cb = CharBuffer.allocate();

    // jpa/0ge2
    // jpa/0l14
    /*
    ArrayList<AmberField> fields = null;

    if (this instanceof RelatedType) {
      fields = getMappedSuperclassFields();

      RelatedType parent = ((RelatedType) this).getParentType();

      if (parent != null) {
        String parentSelect =
          parent.generateLoadSelect(table, id, loadGroup,
                                    hasSelect, fields);

        if (parentSelect != null) {
          hasSelect = true;

          cb.append(parentSelect);
        }
      }
    }
    */

    // jpa/0l14, jpa/0ge3
    for (AmberField field : getFields()) {
      // ejb/0602
      if (getPersistenceUnit().isJPA()) {
	if (field instanceof EntityManyToOneField)
	  ((EntityManyToOneField) field).init((RelatedType) this);
      }

      // jpa/0gg3
      if (field.getLoadGroupIndex() == loadGroup) {
	String propSelect = field.generateLoadSelect(table, id);

	if (propSelect == null)
	  continue;

	if (hasSelect)
	  cb.append(", ");
	hasSelect = true;

	cb.append(propSelect);
      }
    }

    if (cb.length() == 0)
      return null;
    else
      return cb.close();
  }

  /**
   * Generates the foreign delete
   */
  public void generateInvalidateForeign(JavaWriter out)
    throws IOException
  {
    for (AmberField field : getFields()) {
      field.generateInvalidateForeign(out);
    }
  }

  /**
   * Generates any expiration code.
   */
  public void generateExpire(JavaWriter out)
    throws IOException
  {
    for (AmberField field : getFields()) {
      field.generateExpire(out);
    }
  }

  /**
   * Gets a matching getter.
   */
  public JMethod getGetter(String name)
  {
    return getGetter(_beanClass, name);
  }

  /**
   * Gets a matching getter.
   */
  public static JMethod getGetter(JClass cl, String name)
  {
    JMethod []methods = cl.getMethods();

    for (int i = 0; i < methods.length; i++) {
      JClass []param = methods[i].getParameterTypes();
      String methodName = methods[i].getName();

      if (name.equals(methodName) && param.length == 0)
        return methods[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getGetter(cl, name);
    else
      return null;
  }

  /**
   * Gets a matching getter.
   */
  public static JField getField(JClass cl, String name)
  {
    JField []fields = cl.getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
      if (name.equals(fields[i].getName()))
        return fields[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getField(cl, name);
    else
      return null;
  }

  /**
   * Gets a matching getter.
   */
  public static JMethod getSetter(JClass cl, String name)
  {
    JMethod []methods = cl.getMethods();

    for (int i = 0; i < methods.length; i++) {
      JClass []param = methods[i].getParameterTypes();
      String methodName = methods[i].getName();

      if (name.equals(methodName) && param.length == 1)
        return methods[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getSetter(cl, name);
    else
      return null;
  }

  /**
   * Returns the load mask generated on create.
   */
  public long getCreateLoadMask(int group)
  {
    long mask = 0;

    for (int i = 0; i < _fields.size(); i++) {
      mask |= _fields.get(i).getCreateLoadMask(group);
    }

    return mask;
  }

  /**
   * Printable version of the entity.
   */
  public String toString()
  {
    return "AbstractStatefulType[" + _beanClass.getName() + "]";
  }
}
