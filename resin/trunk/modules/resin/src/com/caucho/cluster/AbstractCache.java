/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.cluster;

import com.caucho.config.ConfigException;
import com.caucho.config.types.Period;
import com.caucho.loader.Environment;
import com.caucho.server.cluster.Server;
import com.caucho.server.distcache.CacheConfig;
import com.caucho.server.distcache.DistributedCacheManager;
import com.caucho.server.distcache.HashKey;
import com.caucho.server.distcache.HashManager;
import com.caucho.util.LruCache;
import com.caucho.util.L10N;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import javax.annotation.PostConstruct;

/**
 * Implements the distributed cache
 */
abstract public class AbstractCache implements Cache, ByteStreamCache
{
  private static final L10N L = new L10N(AbstractCache.class);

  private String _contextId;
  
  private String _name = "";

  private String _id;

  private CacheConfig _config = new CacheConfig();

  private LruCache<Object,HashKey> _keyHashCache
    = new LruCache<Object,HashKey>(512);

  private boolean _isInit;

  private DistributedCacheManager _distributedCacheManager;

  /**
   * Assign the name.  The name is mandatory and must be unique.
   */
  public void setName(String name)
  {
    _name = name;
  }

  /**
   * Assign the CacheLoader to populate the cache on a miss.
   */
  public void setCacheLoader(CacheLoader loader)
  {
    _config.setCacheLoader(loader);
  }

  /**
   * Assign the serializer
   */
  public void setSerializer(CacheSerializer serializer)
  {
    _config.setValueSerializer(serializer);
  }

  /**
   * Sets the backup mode.  If backups are enabled, copies of the
   * cache item will be sent to the owning triad server.
   *
   * Defaults to true.
   */
  public void setBackup(boolean isBackup)
  {
    if (isBackup)
      _config.setFlags(_config.getFlags() | CacheConfig.FLAG_BACKUP);
    else
      _config.setFlags(_config.getFlags() & ~CacheConfig.FLAG_BACKUP);
  }

  /**
   * Sets the triplicate backup mode.  If triplicate backups is set,
   * all triad servers have a copy of the cache item.
   *
   * Defaults to true.
   */
  public void setTriplicate(boolean isTriplicate)
  {
    if (isTriplicate)
      _config.setFlags(_config.getFlags() | CacheConfig.FLAG_TRIPLICATE);
    else
      _config.setFlags(_config.getFlags() & ~CacheConfig.FLAG_TRIPLICATE);
  }

  /**
   * The maximum valid time for an item.  Items stored in the cache
   * for longer than the expire time are no longer valid and will
   * return null from a get.
   *
   * Default is infinite.
   */
  public long getExpireTimeout()
  {
    return _config.getExpireTimeout();
  }

  /**
   * The maximum valid time for a cached item before it expires.
   * Items stored in the cache for longer than the expire time are
   * no longer valid and will return null from a get.
   *
   * Default is infinite.
   */
  public void setExpireTimeout(Period expireTimeout)
  {
    setExpireTimeoutMillis(expireTimeout.getPeriod());
  }

  /**
   * The maximum valid time for an item.  Items stored in the cache
   * for longer than the expire time are no longer valid and will
   * return null from a get.
   *
   * Default is infinite.
   */
  public void setExpireTimeoutMillis(long expireTimeout)
  {
    _config.setExpireTimeout(expireTimeout);
  }

  /**
   * The maximum idle time for an item, which is typically used for
   * temporary data like sessions.  For example, session
   * data might be removed if idle over 30 minutes.
   *
   * Cached data would have infinite idle time because
   * it doesn't depend on how often it's accessed.
   *
   * Default is infinite.
   */
  public void setIdleTimeout(Period period)
  {
    setIdleTimeoutMillis(period.getPeriod());
  }


  /**
   * The maximum idle time for an item, which is typically used for
   * temporary data like sessions.  For example, session
   * data might be removed if idle over 30 minutes.
   *
   * Cached data would have infinite idle time because
   * it doesn't depend on how often it's accessed.
   *
   * Default is infinite.
   */
  public long getIdleTimeout()
  {
    return _config.getIdleTimeout();
  }

  /**
   * Sets the idle timeout in milliseconds
   */
  public void setIdleTimeoutMillis(long timeout)
  {
    _config.setIdleTimeout(timeout);
  }

  /**
   * Returns the idle check window, used to minimize traffic when
   * updating access times.
   */
  public long getIdleCheckWindow()
  {
    return _config.getIdleCheckWindow();
  }

  /**
   * The local read timeout sets how long a local copy of
   * a cache item can be reused before checking with the master copy.
   *
   * A read-only item could be infinite (-1).  A slow changing item
   * like a list of bulletin-board comments could be 10s.  Even a relatively
   * quicky changing item can be 10ms or 100ms.
   *
   * The default is 10ms
   */
  public void setLocalReadTimeout(Period period)
  {
    setLocalReadTimeoutMillis(period.getPeriod());
  }

  /**
   * The local read timeout sets how long a local copy of
   * a cache item can be reused before checking with the master copy.
   *
   * A read-only item could be infinite (-1).  A slow changing item
   * like a list of bulletin-board comments could be 10s.  Even a relatively
   * quicky changing item can be 10ms or 100ms.
   *
   * The default is 10ms
   */
  public void setLocalReadTimeoutMillis(long period)
  {
    _config.setLocalReadTimeout(period);
  }

  /**
   * The local read timeout is how long a local copy of
   * a cache item can be reused before checking with the master copy.
   *
   * A read-only item could be infinite (-1).  A slow changing item
   * like a list of bulletin-board comments could be 10s.  Even a relatively
   * quicky changing item can be 10ms or 100ms.
   *
   * The default is 10ms
   */
  public long getLocalReadTimeout()
  {
    return _config.getLocalReadTimeout();
  }

  /**
   * Initialize the cache
   */
  @PostConstruct
  public void init()
  {
    synchronized (this) {
      if (_isInit)
	return;
      _isInit = true;

      if (_name == null)
	throw new ConfigException(L.l("'name' is a require attribute for any Cache"));
    
      _contextId = Environment.getEnvironmentName();

      _id = _contextId + ":" + _name;

      _config.init();

      Server server = Server.getCurrent();

      if (server == null)
	throw new ConfigException(L.l("'{0}' cannot be initialized because it is not in a clustered environment",
				      getClass().getSimpleName()));

      _distributedCacheManager = server.getDistributedCacheManager();
    }
  }
  
  /**
   * Returns the object with the given key.
   */
  public Object get(Object key)
  {
    HashKey hashKey = getHashKey(key);

    return _distributedCacheManager.get(hashKey, _config);
  }
  
  /**
   * Fills an output stream with the value for a key.
   */
  public boolean get(Object key, OutputStream os)
    throws IOException
  {
    HashKey hashKey = getHashKey(key);

    return _distributedCacheManager.get(hashKey, os, _config);
  }
  
  /**
   * Returns the cache entry for the object with the given key.
   */
  public CacheEntry<Object> getEntry(Object key)
  {
    HashKey hashKey = getHashKey(key);
    
    return _distributedCacheManager.getEntry(hashKey, _config);
  }
  
  /**
   * Returns the object with the given key.
   */
  public Object peek(Object key)
  {
    HashKey hashKey = getHashKey(key);

    return _distributedCacheManager.peek(hashKey, _config);
  }
  
  /**
   * Puts a new item in the cache.
   *
   * @param key the key of the item to put
   * @param value the value of the item to put
   */
  public Object put(Object key, Object value)
  {
    HashKey hashKey = getHashKey(key);

    _distributedCacheManager.put(hashKey, value, _config);

    return null;
  }
  
  /**
   * Puts a new item in the cache with a custom idle
   * timeout (used for sessions).
   *
   * @param key the key of the item to put
   * @param value the value of the item to put
   * @param idleTimeout the idle timeout for the item
   */
  public CacheEntry put(Object key,
			InputStream is,
			long idleTimeout)
    throws IOException
  {
    HashKey hashKey = getHashKey(key);

    return _distributedCacheManager.put(hashKey, is, idleTimeout, _config);
  }
  
  /**
   * Updates the cache if the old version matches the current version.
   * A zero value for the old value hash only adds the entry if it's new
   *
   * @param key the key to compare
   * @param version the version of the old value, returned by getEntry
   * @param value the new value
   *
   * @return true if the update succeeds, false if it fails
   */
  public boolean compareAndPut(Object key,
			       long version,
			       Object value)
  {
    put(key, value);
    
    return true;
  }
  
  /**
   * Updates the cache if the old version matches the current value.
   * A zero value for the old version only adds the entry if it's new
   *
   * @param key the key to compare
   * @param version the hash of the old version, returned by getEntry
   * @param value the new value
   *
   * @return true if the update succeeds, false if it fails
   */
  public boolean compareAndPut(Object key,
			       long version,
			       InputStream is)
    throws IOException
  {
    put(key, is);
    
    return true;
  }

  /**
   * Removes the entry from the cache
   *
   * @return true if the object existed
   */
  public Object remove(Object key)
  {
    HashKey hashKey = getHashKey(key);
    
    _distributedCacheManager.remove(hashKey);

    return null;
  }

  /**
   * Removes the entry from the cache if the current entry matches the version
   */
  public boolean compareAndRemove(Object key, long version)
  {
    HashKey hashKey = getHashKey(key);
    
    return false;
  }

  /**
   * Returns the key hash
   */
  protected HashKey getHashKey(Object key)
  {
    HashKey hashKey = _keyHashCache.get(key);

    if (hashKey != null)
      return hashKey;

    try {
      MessageDigest digest
	= MessageDigest.getInstance(HashManager.HASH_ALGORITHM);

      NullDigestOutputStream dOut = new NullDigestOutputStream(digest);

      Object []fullKey = new Object[] { _id, key };

      _config.getKeySerializer().serialize(fullKey, dOut);

      hashKey = new HashKey(dOut.digest());

      _keyHashCache.put(key, hashKey);

      return hashKey;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }

  static class NullDigestOutputStream extends OutputStream {
    private MessageDigest _digest;

    NullDigestOutputStream(MessageDigest digest)
    {
      _digest = digest;
    }

    public void write(int value)
    {
      _digest.update((byte) value);
    }

    public void write(byte []buffer, int offset, int length)
    {
      _digest.update(buffer, offset, length);
    }

    public byte []digest()
    {
      return _digest.digest();
    }

    public void flush()
    {
    }

    public void close()
    {
    }
  }
}
