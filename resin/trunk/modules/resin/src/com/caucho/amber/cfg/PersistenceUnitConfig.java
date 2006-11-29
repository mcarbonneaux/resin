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

package com.caucho.amber.cfg;

import com.caucho.amber.manager.AmberContainer;
import com.caucho.amber.manager.AmberPersistenceUnit;
import com.caucho.bytecode.JClass;
import com.caucho.vfs.Path;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * <persistence-unit> tag in the persistence.xml
 */
public class PersistenceUnitConfig {
  private String _name;
  private String _provider;
  private DataSource _jtaDataSource;
  private DataSource _nonJtaDataSource;
  private boolean _isExcludeUnlistedClasses;

  // className -> type
  private HashMap<String, JClass> _classMap
    = new HashMap<String, JClass>();

  /**
   * Returns the unit name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Sets the unit name.
   */
  public void setName(String name)
  {
    _name = name;
  }

  /**
   * Sets the transaction type.
   */
  public void setTransactionType(String type)
  {
  }

  /**
   * Sets the description.
   */
  public void setDescription(String description)
  {
  }

  /**
   * Sets the provider class name.
   */
  public void setProvider(String provider)
  {
    _provider = provider;
  }

  /**
   * Sets the transactional data source.
   */
  public void setJtaDataSource(DataSource ds)
  {
    _jtaDataSource = ds;
  }

  /**
   * Sets the non-transactional data source.
   */
  public void setNonJtaDataSource(DataSource ds)
  {
    _nonJtaDataSource = ds;
  }

  /**
   * Sets the mapping file.
   */
  public void addMappingFile(Path file)
  {
  }

  /**
   * Sets the jars with classes.
   */
  public void addJarFile(Path file)
  {
  }

  /**
   * Adds a configured class.
   */
  public void addClass(String cl)
  {
    // null means the class is not yet verified as:
    // Entity | Embeddable | MappedSuperclass

    _classMap.put(cl, null);
  }

  /**
   * Adds a map of configured classes.
   */
  public void addAllClasses(Map<String, JClass> classMap)
  {
    for (Map.Entry<String, JClass> entry : classMap.entrySet()) {
      String k = entry.getKey();
      JClass v = entry.getValue();

      if (! _classMap.containsKey(k))
        _classMap.put(k, v);
    }
  }

  /**
   * Sets true if only listed classes should be used.
   */
  public void setExcludeUnlistedClasses(boolean isExclude)
  {
    _isExcludeUnlistedClasses = isExclude;
  }

  /**
   * Adds the properties.
   */
  public PropertiesConfig createProperties()
  {
    return new PropertiesConfig();
  }

  public AmberPersistenceUnit init(AmberContainer container,
                                   EntityMappingsConfig entityMappings)
    throws Exception
  {
    AmberPersistenceUnit unit
      = new AmberPersistenceUnit(container, _name);

    unit.setEntityMappingsConfig(entityMappings);

    unit.init();

    for (Map.Entry<String, JClass> entry : _classMap.entrySet()) {
      String className = entry.getKey();
      JClass type = entry.getValue();

      unit.addEntityClass(className, type);
    }

    unit.generate();

    return unit;
  }

  public String toString()
  {
    return "PersistenceUnitConfig[" + _name + "]";
  }

  public class PropertiesConfig {
    public PropertyConfig createProperty()
    {
      return new PropertyConfig();
    }
  }

  public class PropertyConfig {
    public void setName(String name)
    {
    }

    public void setValue(String name)
    {
    }
  }
}
