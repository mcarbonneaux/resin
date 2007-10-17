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

package com.caucho.sql;

import com.caucho.loader.EnvironmentLocal;
import com.caucho.util.L10N;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Manages databases in a local environment, e.g. for PHP dynamic
 * database lookup.
 */
public class DatabaseManager {
  protected static final Logger log
    = Logger.getLogger(DatabaseManager.class.getName());
  private static final L10N L = new L10N(DatabaseManager.class);

  private static final EnvironmentLocal<DatabaseManager> _localManager
    = new EnvironmentLocal<DatabaseManager>();

  private final HashMap<String,DBPool> _databaseMap
    = new HashMap<String,DBPool>();

  private static int _gId;

  /**
   * The manager is never instantiated.
   */
  private DatabaseManager()
  {
  }

  /**
   * Returns the database manager for the local environment.
   */
  private static DatabaseManager getLocalManager()
  {
    synchronized (_localManager) {
      DatabaseManager manager = _localManager.getLevel();

      if (manager == null) {
	manager = new DatabaseManager();

	_localManager.set(manager);
      }

      return manager;
    }
  }

  /**
   * Returns a matching dbpool.
   */
  public static DataSource findDatabase(String driver,
					String url)
    throws Exception
  {
    return getLocalManager().findDatabaseImpl(driver, url);
  }

  /**
   * Looks up the local database, creating if necessary.
   */
  private DataSource findDatabaseImpl(String driverName,
				      String url)
    throws Exception
  {
    synchronized (_databaseMap) {
      DBPool db = _databaseMap.get(url);

      if (db == null) {
	db = new DBPool();

	db.setVar(url + "-" + _gId++);
	
	DriverConfig driver = db.createDriver();

	ClassLoader loader = Thread.currentThread().getContextClassLoader();
	
	Class driverClass = Class.forName(driverName, false, loader);

	driver.setType(driverClass);
	driver.setURL(url);

	db.init();

	_databaseMap.put(url, db);
      }

      return db;
    }
  }

  static class DatabaseKey {
    private String _url;
    private String _catalog;

    DatabaseKey(String url, String catalog)
    {
      _url = url;
      _catalog = catalog;
    }

    public int hashCode()
    {
      int hash = 37;

      hash = 65521 * hash + _url.hashCode();

      if (_catalog != null)
	hash = 65521 * hash + _catalog.hashCode();

      return hash;
    }

    public boolean equals(Object o)
    {
      if (! (o instanceof DatabaseKey))
	return false;

      DatabaseKey key = (DatabaseKey) o;

      if (! _url.equals(key._url))
	return false;

      return (_catalog == key._catalog
	      || _catalog != null && _catalog.equals(key._catalog));
    }
  }
}

