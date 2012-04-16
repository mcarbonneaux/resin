/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.server.distcache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.cache.Cache;
import javax.cache.CacheLoader;
import javax.cache.CacheWriter;

import com.caucho.cloud.topology.TriadOwner;
import com.caucho.distcache.ExtCacheEntry;
import com.caucho.server.distcache.LocalDataManager.DataItem;
import com.caucho.util.CurrentTime;
import com.caucho.util.HashKey;
import com.caucho.util.Hex;
import com.caucho.util.IoUtil;
import com.caucho.vfs.StreamSource;

/**
 * An entry in the cache map
 */
public class DistCacheEntry {
  private static final Logger log
    = Logger.getLogger(DistCacheEntry.class.getName());
  
  private final CacheStoreManager _cacheService;
  private final HashKey _keyHash;
  private final TriadOwner _owner;

  private Object _key;

  private final AtomicBoolean _isReadUpdate = new AtomicBoolean();

  private final AtomicReference<MnodeEntry> _mnodeEntry
    = new AtomicReference<MnodeEntry>(MnodeEntry.NULL);
  
  private final AtomicInteger _loadCount = new AtomicInteger();

  DistCacheEntry(CacheStoreManager engine,
                 HashKey keyHash,
                 TriadOwner owner)
  {
    _cacheService = engine;
    _keyHash = keyHash;
    
    _owner = TriadOwner.getHashOwner(keyHash.getHash());
  }

  /**
   * Returns the key for this entry in the Cache.
   */
  public final Object getKey()
  {
    return _key;
  }
  
  public final void setKey(Object key)
  {
    if (_key == null)
      _key = key;
  }
  
  private LocalDataManager getLocalDataManager()
  {
    return _cacheService.getLocalDataManager();
  }

  /**
   * Returns the keyHash
   */
  public final HashKey getKeyHash()
  {
    return _keyHash;
  }

  /**
   * Returns the owner
   */
  public final TriadOwner getOwner()
  {
    return _owner;
  }

  /**
   * Returns the value section of the entry.
   */
  public final MnodeEntry getMnodeEntry()
  {
    return _mnodeEntry.get();
  }

  /**
   * Returns the object for the given key, checking the backing if necessary.
   * If it is not found, the optional cacheLoader is invoked, if present.
   */
  public Object get(CacheConfig config)
  {
    long now = CurrentTime.getCurrentTime();

    return get(config, now);
  }

  /**
   * Returns the object for the given key, checking the backing if necessary.
   * If it is not found, the optional cacheLoader is invoked, if present.
   */
  /*
  public Object getExact(CacheConfig config)
  {
    long now = CurrentTime.getCurrentTime();

    return get(config, now, true);
  }
  */

  /**
   * Returns the object for the given key, checking the backing if necessary
   */
  public MnodeEntry loadMnodeValue(CacheConfig config)
  {
    long now = CurrentTime.getCurrentTime();

    return loadMnodeValue(config, now); // , false);
  }

  /**
   * Gets a cache entry as a stream
   */
  final public StreamSource getValueStream()
  {
    MnodeEntry mnodeValue = getMnodeEntry();

    updateAccessTime();

    return getLocalDataManager().createDataSource(mnodeValue.getValueDataId());
  }

  public long getValueHash(Object value, CacheConfig config)
  {
    if (value == null)
      return 0;

    return _cacheService.calculateValueHash(value, config);
  }
  
  public CacheUpdateWithSource loadCacheStream(long requestVersion,
                                               boolean isValueStream)
  {
    MnodeEntry mnodeEntry = getMnodeEntry();

    if (mnodeEntry.getVersion() <= requestVersion) {
      return new CacheUpdateWithSource(mnodeEntry, null, 
                                       mnodeEntry.getLeaseOwner());
    }
    else if (mnodeEntry.isImplicitNull()) {
      return new CacheUpdateWithSource(mnodeEntry, null, 
                                       mnodeEntry.getLeaseOwner());
    }

    StreamSource source = null;
      
    if (isValueStream) {
      long valueDataId = mnodeEntry.getValueDataId();
      
      DataStreamSource dataSource
          = getLocalDataManager().createDataSource(valueDataId);

      if (dataSource != null) {
        source = new StreamSource(dataSource);
      }

      // XXX: updateLease(entryKey, mnodeEntry, leaseOwner);
    }

    return new CacheUpdateWithSource(mnodeEntry, source, mnodeEntry.getLeaseOwner());
  }

 /**
   * Sets a cache entry
   */
  final public void put(Object value,
                        CacheConfig config)
  {
    long now = CurrentTime.getCurrentTime();

    // server/60a0 - on server '4', need to read update from triad
    MnodeEntry mnodeValue = loadMnodeValue(config, now); // , false);

    put(value, config, now, mnodeValue);
  }

  /**
   * Sets the value by an input stream
   */
  public void put(InputStream is,
                  CacheConfig config,
                  long accessedExpireTimeout,
                  long modifiedExpireTimeout)
    throws IOException
  {
    putStream(is, config, 
              accessedExpireTimeout,
              modifiedExpireTimeout, 
              0);
  }

  /**
   * Sets the value by an input stream
   */
  public void put(InputStream is,
                  CacheConfig config,
                  long accessedExpireTimeout,
                  long modifiedExpireTimeout,
                  int flags)
    throws IOException
  {
    putStream(is, config, 
              accessedExpireTimeout, 
              modifiedExpireTimeout,
              flags);
  }

  private final void putStream(InputStream is,
                               CacheConfig config,
                               long accessedExpireTime,
                               long modifiedExpireTime,
                               int userFlags)
    throws IOException
  {
    loadLocalMnodeValue();

    DataItem valueItem = getLocalDataManager().writeData(is);
    
    long valueHash = valueItem.getValueHash();
    long valueDataId = valueItem.getValueDataId();
    
    long valueLength = valueItem.getLength();
    long newVersion = getNewVersion(getMnodeEntry());
    
    long flags = config.getFlags() | ((long) userFlags) << 32;
    
    if (accessedExpireTime < 0)
      accessedExpireTime = config.getAccessedExpireTimeout();
    
    if (modifiedExpireTime < 0)
      modifiedExpireTime = config.getModifiedExpireTimeout();

    
    int leaseOwner = getMnodeEntry().getLeaseOwner();
    long leaseExpireTimeout = config.getLeaseExpireTimeout();
    
    MnodeUpdate mnodeUpdate = new MnodeUpdate(valueHash,
                                              valueDataId,
                                              valueLength,
                                              newVersion,
                                              HashKey.getHash(config.getCacheKey()),
                                              flags,
                                              accessedExpireTime,
                                              modifiedExpireTime,
                                              leaseExpireTimeout,
                                              leaseOwner);

    // add 25% window for update efficiency
    // idleTimeout = idleTimeout * 5L / 4;
    
    putLocalValue(mnodeUpdate, null);
    
    config.getEngine().put(getKeyHash(), mnodeUpdate);
  }

  /**
   * Sets a cache entry
   */
  public final boolean remove(CacheConfig config)
  {
    HashKey key = getKeyHash();

    MnodeEntry mnodeEntry = loadLocalMnodeValue();
    long oldValueHash = mnodeEntry != null ? mnodeEntry.getValueHash() : 0;

    long newVersion = getNewVersion(mnodeEntry);

    /*
    long leaseTimeout = (mnodeEntry != null
                         ? mnodeEntry.getLeaseTimeout()
                         : config.getLeaseExpireTimeout());
    int leaseOwner = (mnodeEntry != null ? mnodeEntry.getLeaseOwner() : -1);
    */
    
    MnodeUpdate mnodeUpdate;
    
    if (mnodeEntry != null)
      mnodeUpdate = MnodeUpdate.createNull(newVersion, mnodeEntry);
    else
      mnodeUpdate = MnodeUpdate.createNull(newVersion, config);

    putLocalValueImpl(mnodeUpdate, null);

    if (mnodeEntry == null)
      return oldValueHash != 0;

    config.getEngine().remove(key, mnodeUpdate, mnodeEntry);
    
    CacheWriter writer = config.getCacheWriter();
    
    if (writer != null && config.isWriteThrough()) {
      writer.delete(getKey());
    }

    return oldValueHash != 0;
  }
  
  //
  // atomic operations
  //

  /**
   * Sets the value by an input stream
   */
  public boolean putIfNew(MnodeUpdate update,
                          InputStream is)
    throws IOException
  {
    MnodeValue newValue = putLocalValue(update, is);

    return newValue.getValueHash() == update.getValueHash();
  }
  

  //
  // compare and put
  //
  
  public boolean compareAndPut(long testValue,
                               Object value, 
                               CacheConfig config)
  {
    MnodeUpdate update
      = getLocalDataManager().writeValue(getMnodeEntry(), value, config);
    
    try {
      return compareAndPutLocal(testValue, update, value);
    } finally {
      MnodeValue newMnodeValue = getMnodeEntry();
      
      if (newMnodeValue == null
          || newMnodeValue.getValueDataId() != update.getValueDataId()) {
        getLocalDataManager().removeData(update.getValueDataId());
      }
    }
  }
  
  public final boolean compareAndPutLocal(long testValue,
                                          MnodeUpdate update,
                                          StreamSource source)
  {
    long version = getNewVersion(getMnodeEntry());
    
    MnodeUpdate newUpdate
      = getLocalDataManager().writeData(update, version, source);
    
    Object value = null;
    
    try {
      return compareAndPutLocal(testValue, newUpdate, value);
    } finally {
      MnodeValue newMnodeValue = getMnodeEntry();
      
      if (newMnodeValue.getValueDataId() != newUpdate.getValueDataId()) {
        getLocalDataManager().removeData(newUpdate.getValueDataId());
      }
    }
  }

  protected boolean compareAndPut(DistCacheEntry entry,
                                  long testValue,
                                  MnodeUpdate mnodeUpdate,
                                  Object value,
                                  CacheConfig config)
  {
    CacheEngine engine = config.getEngine();
    
    return engine.compareAndPut(entry, testValue, mnodeUpdate, value);
  }
  
  //
  // get and put
  //

  /**
   * Remove the value
   */
  public Object getAndRemove(CacheConfig config)
  {
    return getAndPut(null, config);
  }

  public Object getAndReplace(long testValue,
                              Object value, 
                              CacheConfig config)
  {
    if (compareAndPut(testValue, value, config)) {
      long result = -1;
      
      return getLocalDataManager().readData(getKeyHash(),
                                            result,
                                            getMnodeEntry().getValueDataId(),
                                            config.getValueSerializer(),
                                            config);
    }
    else {
      return null;
    }
  }

  /**
   * Sets the current value
   */
  public Object getAndPut(Object value, CacheConfig config)
  {
    long now = CurrentTime.getCurrentTime();

    // server/60a0 - on server '4', need to read update from triad
    MnodeEntry mnodeValue = loadMnodeValue(config, now); // , false);

    return getAndPut(value, config, now, mnodeValue);
  }

  /**
   * Sets a cache entry
   */
  protected final Object getAndPut(Object value,
                                   CacheConfig config,
                                   long now,
                                   MnodeEntry mnodeValue)
  {
    MnodeUpdate mnodeUpdate
      = getLocalDataManager().writeValue(mnodeValue, value, config);
    
    Object oldValue = mnodeValue != null ? mnodeValue.getValue() : null;

    int leaseOwner = mnodeValue != null ? mnodeValue.getLeaseOwner() : -1;

    long oldHash = getAndPut(mnodeUpdate, value,
                             config.getLeaseExpireTimeout(),
                             leaseOwner,
                             config);

    if (oldHash == 0)
      return null;
    
    if (oldHash == mnodeUpdate.getValueHash() && oldValue != null)
      return oldValue;
    
    oldValue = getLocalDataManager().readData(getKeyHash(),
                                              oldHash,
                                              mnodeValue.getValueDataId(),
                                              config.getValueSerializer(),
                                              config);

    return oldValue;
  }
  
  public long getAndPutLocal(DistCacheEntry entry,
                             MnodeUpdate mnodeUpdate,
                             Object value)
  {
    long oldValueHash = entry.getMnodeEntry().getValueHash();

    entry.putLocalValue(mnodeUpdate, value);

    return oldValueHash;
  }

  /**
   * Sets a cache entry
   */
  private long getAndPut(MnodeUpdate mnodeUpdate,
                         Object value,
                         long leaseTimeout,
                         int leaseOwner,
                         CacheConfig config)
  {
    return config.getEngine().getAndPut(this, mnodeUpdate, value);
  }
  
  //
  // utility
  //

  /**
   * Sets the current value.
   */
  public final boolean compareAndSetEntry(MnodeEntry oldMnodeValue,
                                          MnodeEntry mnodeValue)
  {
    if (mnodeValue == null)
      throw new NullPointerException();
    
    return _mnodeEntry.compareAndSet(oldMnodeValue, mnodeValue);
  }
  
  /**
   * Writes the data to a stream.
   */
  public boolean readData(OutputStream os, CacheConfig config)
    throws IOException
  {
    return getLocalDataManager().readData(getKeyHash(), getMnodeEntry(),
                                          os, config);
  }

  public boolean isModified(MnodeValue newValue)
  {
    MnodeEntry oldValue = getMnodeEntry();
    
    if (oldValue.getVersion() < newValue.getVersion()) {
      return true;
    }
    else if (newValue.getVersion() < oldValue.getVersion()) {
      return false;
    }
    else {
      // XXX: need to check hash.
      return true;
    }
  }
  
  private long getNewVersion(MnodeValue mnodeValue)
  {
    long version = mnodeValue != null ? mnodeValue.getVersion() : 0;
    
    return getNewVersion(version);
  }
  
  private long getNewVersion(long version)
  {
    long newVersion = version + 1;

    long now = CurrentTime.getCurrentTime();
  
    if (newVersion < now)
      return now;
    else
      return newVersion;
  }

  public void clearLease()
  {
    getMnodeEntry().clearLease();
  }
  
  public boolean isLeaseExpired()
  {
    return getMnodeEntry().isLeaseExpired(CurrentTime.getCurrentTime());
  }
  
  public void updateLease(int leaseOwner)
  {
    long now = CurrentTime.getCurrentTime();

    getMnodeEntry().setLeaseOwner(leaseOwner, now);
  }

  public long getCost()
  {
    return 0;
  }
  
  //
  // get/load operations
  //

  private Object get(CacheConfig config,
                     long now)
  {
    MnodeEntry mnodeValue = loadMnodeValue(config, now);

    if (mnodeValue == null) {
      return null;
    }

    Object value = mnodeValue.getValue();

    if (value != null) {
      return value;
    }

    long valueHash = mnodeValue.getValueHash();

    if (valueHash == 0) {
      return null;
    }

    updateAccessTime(mnodeValue, now);

    value = _cacheService.getLocalDataManager().readData(getKeyHash(),
                                                         valueHash,
                                                         mnodeValue.getValueDataId(),
                                                         config.getValueSerializer(),
                                                         config);
    
    if (value == null) {
      // Recovery from dropped or corrupted data
      log.warning("Missing or corrupted data in get for " 
                  + mnodeValue + " " + this);
      remove(config);
    }

    mnodeValue.setObjectValue(value);

    return value;
  }

  final private MnodeEntry loadMnodeValue(CacheConfig config, long now)
  {
    MnodeEntry mnodeValue = loadLocalMnodeValue();

    if (mnodeValue == null
        || isLocalExpired(config, getKeyHash(), mnodeValue, now)) {
      reloadValue(config, now);
    }

    // server/016q
    updateAccessTime();

    mnodeValue = getMnodeEntry();

    return mnodeValue;
  }

  protected boolean isLocalExpired(CacheConfig config,
                                   HashKey key,
                                   MnodeEntry mnodeValue,
                                   long now)
  {
    return config.getEngine().isLocalExpired(config, key, mnodeValue, now);
  }

  private void reloadValue(CacheConfig config,
                           long now)
  {
    // only one thread may update the expired data
    if (startReadUpdate()) {
      try {
        loadExpiredValue(config, now);
      } finally {
        finishReadUpdate();
      }
    }
  }
  
  private void loadExpiredValue(CacheConfig config,
                                long now)
  {
    MnodeEntry mnodeEntry = getMnodeEntry();
    
    _loadCount.incrementAndGet();
    
    CacheEngine engine = config.getEngine();
    
    engine.get(this, config);
    
    mnodeEntry = getMnodeEntry();

    if (mnodeEntry != null && ! mnodeEntry.isExpired(now)) {
      mnodeEntry.setLastAccessTime(now);
    }
    else if (loadFromCacheLoader(config, now)) {
      mnodeEntry.setLastAccessTime(now);
    }
    else {
      MnodeEntry nullMnodeValue = new MnodeEntry(0, 0, 0, 0, null, null,
                                                 0,
                                                 config.getAccessedExpireTimeout(),
                                                 config.getModifiedExpireTimeout(),
                                                 config.getLeaseExpireTimeout(),
                                                 now, now,
                                                 true, true);

      compareAndSetEntry(mnodeEntry, nullMnodeValue);
    }
  }
  
  private boolean loadFromCacheLoader(CacheConfig config, long now)
  {
    CacheLoader loader = config.getCacheLoader();

    if (loader != null && config.isReadThrough() && getKey() != null) {
      Object arg = null;
      
      Cache.Entry loaderEntry = loader.load(getKey());
      
      MnodeEntry mnodeEntry = getMnodeEntry();

      if (loaderEntry != null) {
        put(loaderEntry.getValue(), config);

        return true;
      }
    }
    
    return false;
  }

  final void loadLocalEntry()
  {
    long now = CurrentTime.getCurrentTime();

    if (getMnodeEntry().isExpired(now)) {
      forceLoadMnodeValue();
    }
  }

  /**
   * Gets a cache entry
   */
  private MnodeEntry forceLoadMnodeValue()
  {
    HashKey key = getKeyHash();
    MnodeEntry mnodeValue = getMnodeEntry();

    MnodeEntry newMnodeValue
      = _cacheService.getDataBacking().loadLocalEntryValue(key);
    
    if (newMnodeValue != null) {
      compareAndSetEntry(mnodeValue, newMnodeValue);
    }

    return getMnodeEntry();
  }

  /**
   * Gets a cache entry as a stream
   */
  final public boolean getStream(OutputStream os,
                                 CacheConfig config)
    throws IOException
  {
    long now = CurrentTime.getCurrentTime();

    MnodeEntry mnodeValue = loadMnodeValue(config); // , false);

    if (mnodeValue == null)
      return false;

    updateAccessTime(mnodeValue, now);

    long valueHash = mnodeValue.getValueHash();

    if (valueHash == 0) {
      return false;
    }

    getLocalDataManager().readData(getKeyHash(), mnodeValue, os, config);
    
    return true;
  }
  
  //
  // put methods
  //

  public MnodeUpdate localUpdate(MnodeUpdate update,
                                 InputStream is)
  {
    MnodeEntry oldEntryValue = getMnodeEntry();
    
    long oldEntryHash = oldEntryValue.getValueHash();
    
    if (update.getValueHash() == 0) {
    }
    else if (oldEntryValue == null
             || (oldEntryValue.getVersion() <= update.getVersion()
                 && update.getValueHash() != oldEntryHash)) {
      try {
        if (is != null) {
          update = _cacheService.getLocalDataManager().writeData(update,
                                                                 update.getVersion(),
                                                                 is);
        }
      } finally {
        IoUtil.close(is);
      }
    }
    
    putLocalValueImpl(update, null);
    
    return getMnodeEntry().getRemoteUpdate();
  }

  /**
   * Sets a cache entry
   */
  public final MnodeValue putLocalValue(MnodeUpdate mnodeUpdate,
                                        InputStream is)
  {
    MnodeValue mnodeValue = localUpdate(mnodeUpdate, is);

    _cacheService.notifyPutListeners(getKeyHash(), mnodeUpdate, mnodeValue);
    
    return mnodeValue;
  }

  /**
   * Sets a cache entry
   */
  public final MnodeEntry putLocalValue(MnodeUpdate mnodeUpdate,
                                        Object value)
  {
    // long valueHash = mnodeUpdate.getValueHash();
    // long version = mnodeUpdate.getVersion();
    
    MnodeEntry mnodeValue = putLocalValueImpl(mnodeUpdate, value);
    
    _cacheService.notifyPutListeners(getKeyHash(), mnodeUpdate, mnodeValue);
    
    return mnodeValue;
  }

  /**
   * Sets a cache entry
   */
  private final MnodeEntry putLocalValueImpl(MnodeUpdate mnodeUpdate,
                                             Object value)
  {
    HashKey key = getKeyHash();
    
    long valueHash = mnodeUpdate.getValueHash();
    long version = mnodeUpdate.getVersion();
    
    MnodeEntry oldEntryValue;
    MnodeEntry mnodeValue;

    do {
      oldEntryValue = loadLocalMnodeValue();
    
      long oldValueHash
        = oldEntryValue != null ? oldEntryValue.getValueHash() : 0;

      long oldVersion = oldEntryValue != null ? oldEntryValue.getVersion() : 0;
      long now = CurrentTime.getCurrentTime();
      
      if (version < oldVersion
          || (version == oldVersion
              && valueHash != 0
              && valueHash <= oldValueHash)) {
        // lease ownership updates even if value doesn't
        if (oldEntryValue != null) {
          oldEntryValue.setLeaseOwner(mnodeUpdate.getLeaseOwner(), now);

          // XXX: access time?
          oldEntryValue.setLastAccessTime(now);
        }

        return oldEntryValue;
      }

      long accessTime = now;
      long updateTime = accessTime;

      mnodeValue = new MnodeEntry(mnodeUpdate,
                                  value,
                                  accessTime,
                                  updateTime,
                                  true,
                                  false,
                                  mnodeUpdate.getLeaseOwner());
    } while (! compareAndSetEntry(oldEntryValue, mnodeValue));

    //MnodeValue newValue
    _cacheService.getDataBacking().putLocalValue(mnodeValue, key,  
                                                 oldEntryValue,
                                                 mnodeUpdate);
    
    return mnodeValue;
  }

  /**
   * Sets a cache entry
   */
  protected final void put(Object value,
                           CacheConfig config,
                           long now,
                           MnodeEntry mnodeValue)
  {
    // long idleTimeout = config.getIdleTimeout() * 5L / 4;
    HashKey key = getKeyHash();

    MnodeUpdate update
      = _cacheService.getLocalDataManager().writeValue(mnodeValue, value, config);
    
    mnodeValue = putLocalValueImpl(update, value);

    if (mnodeValue == null)
      return;

    config.getEngine().put(key, update);
    
    CacheWriter writer = config.getCacheWriter();
    
    if (writer != null && config.isWriteThrough()) {
      // XXX: save facade?
      writer.write(new ExtCacheEntryFacade(this));
    }

    return;
  }

  /**
   * Sets a cache entry
   */
  final MnodeEntry putLocalValue(MnodeEntry mnodeValue)
  {
    MnodeEntry oldEntryValue = getMnodeEntry();

    if (oldEntryValue != null && mnodeValue.compareTo(oldEntryValue) <= 0) {
      return oldEntryValue;
    }

    // the failure cases are not errors because this put() could
    // be immediately followed by an overwriting put()
    if (! compareAndSetEntry(oldEntryValue, mnodeValue)) {
      log.fine(this + " mnodeValue update failed due to timing conflict"
        + " (key=" + getKeyHash() + ")");

      return getMnodeEntry();
    }

    _cacheService.getDataBacking().insertLocalValue(getKeyHash(), mnodeValue,
                                                    oldEntryValue);
    
    return getMnodeEntry();
  }
  
  public boolean compareAndPutLocal(long testValueHash,
                                    MnodeUpdate update,
                                    Object value)
  {
    MnodeEntry mnodeValue = loadLocalMnodeValue();

    long oldValueHash = mnodeValue.getValueHash();
    
    if (oldValueHash != testValueHash) {
      return false;
    }
    
    // add 25% window for update efficiency
    // idleTimeout = idleTimeout * 5L / 4;

    mnodeValue = putLocalValueImpl(update, value);
    
    return (mnodeValue != null);
  }

  /**
   * Loads the value from the local store.
   */
  final MnodeEntry loadLocalMnodeValue()
  {
    HashKey key = getKeyHash();
    MnodeEntry mnodeValue = getMnodeEntry();

    if (mnodeValue.isImplicitNull()) {
      // MnodeEntry newMnodeValue = _cacheSystem.getDataBacking().loadLocalEntryValue(key);
      MnodeEntry newMnodeValue
        = _cacheService.getDataBacking().loadLocalEntryValue(key);
      
      if (newMnodeValue == null) {
        newMnodeValue = MnodeEntry.NULL;
      }
      
      // cloud/6811
      compareAndSetEntry(mnodeValue, newMnodeValue);

      mnodeValue = getMnodeEntry();
    }

    return mnodeValue;
  }

  void updateAccessTime(MnodeEntry mnodeValue,
                                long now)
  {
    if (mnodeValue != null) {
      long idleTimeout = mnodeValue.getAccessedExpireTimeout();
      long updateTime = mnodeValue.getLastModifiedTime();

      if (idleTimeout < CacheConfig.TIME_INFINITY
          && updateTime + mnodeValue.getAccessExpireTimeoutWindow() < now) {
        // XXX:
        mnodeValue.setLastAccessTime(now);

        saveUpdateTime(mnodeValue);
      }
    }
  }

  final protected void updateAccessTime()
  {
    MnodeEntry mnodeValue = getMnodeEntry();
    
    long accessedExpireTimeout = mnodeValue.getAccessedExpireTimeout();
    long accessedTime = mnodeValue.getLastAccessedTime();

    long now = CurrentTime.getCurrentTime();
                       
    if (accessedExpireTimeout < CacheConfig.TIME_INFINITY
        && accessedTime + mnodeValue.getAccessExpireTimeoutWindow() < now) {
      mnodeValue.setLastAccessTime(now);

      saveUpdateTime(mnodeValue);
    }
  }

  /**
   * Sets a cache entry
   */
  final MnodeEntry saveUpdateTime(MnodeEntry mnodeValue)
  {
    MnodeEntry newEntryValue = saveLocalUpdateTime(mnodeValue);

    if (newEntryValue.getVersion() != mnodeValue.getVersion())
      return newEntryValue;

    _cacheService.getCacheEngine().updateTime(getKeyHash(), mnodeValue);

    return mnodeValue;
  }

  /**
   * Sets a cache entry
   */
  final MnodeEntry saveLocalUpdateTime(MnodeEntry mnodeValue)
  {
    MnodeEntry oldEntryValue = getMnodeEntry();

    if (oldEntryValue != null
        && mnodeValue.getVersion() < oldEntryValue.getVersion()) {
      return oldEntryValue;
    }
    
    if (oldEntryValue != null
        && mnodeValue.getLastAccessedTime() == oldEntryValue.getLastAccessedTime()
        && mnodeValue.getLastModifiedTime() == oldEntryValue.getLastModifiedTime()) {
      return oldEntryValue;
    }

    // the failure cases are not errors because this put() could
    // be immediately followed by an overwriting put()

    if (! compareAndSetEntry(oldEntryValue, mnodeValue)) {
      log.fine(this + " mnodeValue updateTime failed due to timing conflict"
               + " (key=" + getKeyHash() + ")");

      return getMnodeEntry();
    }
    
    _cacheService.getDataBacking().saveLocalUpdateTime(getKeyHash(),
                                                       mnodeValue,
                                                       oldEntryValue);

    return getMnodeEntry();
  }

  /**
   * Conditionally starts an update of a cache item, allowing only a
   * single thread to update the data.
   *
   * @return true if the thread is allowed to update
   */
  private final boolean startReadUpdate()
  {
    return _isReadUpdate.compareAndSet(false, true);
  }

  /**
   * Completes an update of a cache item.
   */
  private final void finishReadUpdate()
  {
    _isReadUpdate.set(false);
  }

  //
  // statistics
  //
  

  public int getLoadCount()
  {
    return _loadCount.get();
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[key=" + _key
            + ",keyHash=" + Hex.toHex(_keyHash.getHash(), 0, 4)
            + ",owner=" + _owner
            + "]");
  }
}
