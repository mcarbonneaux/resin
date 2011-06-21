/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.db.block;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.util.FreeList;
import com.caucho.util.L10N;
import com.caucho.util.SyncCacheListener;

/**
 * Represents a versioned row
 */
public final class Block implements SyncCacheListener {
  private static final L10N L = new L10N(Block.class);
  
  private static final Logger log
    = Logger.getLogger(Block.class.getName());
  
  private static final long INIT_DIRTY = BlockStore.BLOCK_SIZE;

  private static final FreeList<byte[]> _freeBuffers
    = new FreeList<byte[]>(256);
    
  private final BlockStore _store;
  private final long _blockId;

  private final Lock _readLock;
  private final Lock _writeLock;
  
  private final AtomicReference<BlockState> _state
    = new AtomicReference<BlockState>(BlockState.INIT);
  
  // _useCount tracks the Block's use in the LRU map. When the useCount is
  // zero, the Block no longer belongs to the LRU.
  private final AtomicInteger _useCount = new AtomicInteger(1);

  private final AtomicLong _dirtyRange = new AtomicLong(INIT_DIRTY);
  
  private boolean _isFlushDirtyOnCommit;
  
  private byte []_buffer;

  Block(BlockStore store, long blockId)
  {
    store.validateBlockId(blockId);

    _store = store;
    _blockId = blockId;

    // _lock = new Lock("block:" + store.getName() + ":" + Long.toHexString(_blockId));
    // ReadWriteLock rwLock = new ReentrantReadWriteLock();
    ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    _readLock = rwLock.readLock();
    _writeLock = rwLock.writeLock();

    _isFlushDirtyOnCommit = _store.isFlushDirtyBlocksOnCommit();

    _buffer = allocateBuffer();
    
    if (log.isLoggable(Level.ALL))
      log.log(Level.ALL, this + " create");
  }

  /**
   * Returns true if the block should be flushed on a commit.
   */
  public boolean isFlushDirtyOnCommit()
  {
    return _isFlushDirtyOnCommit;
  }

  /**
   * True if the block should be flushed on a commit.
   */
  public void setFlushDirtyOnCommit(boolean isFlush)
  {
    _isFlushDirtyOnCommit = isFlush;
  }

  public boolean isIndex()
  {
    long blockIndex = BlockStore.blockIdToIndex(getBlockId());
    
    return getStore().getAllocation(blockIndex) == BlockStore.ALLOC_INDEX;
  }
  
  public void validateIsIndex()
  {
    long blockIndex = BlockStore.blockIdToIndex(getBlockId());
    
    int allocCode = getStore().getAllocation(blockIndex);
    
    if (allocCode != BlockStore.ALLOC_INDEX)
      throw new IllegalStateException(L.l("block {0} is not an index code={1}",
                                          this, allocCode));
  }

  /**
   * Returns the block's table.
   */
  public BlockStore getStore()
  {
    return _store;
  }

  /**
   * Returns the block's id.
   */
  public long getBlockId()
  {
    return _blockId;
  }

  public final Lock getReadLock()
  {
    return _readLock;
  }

  public final Lock getWriteLock()
  {
    return _writeLock;
  }

  public final boolean isValid()
  {
    return _state.get().isValid();
  }

  /**
   * Returns true if the block needs writing
   */
  public boolean isDirty()
  {
    return _dirtyRange.get() != INIT_DIRTY;
  }

  /**
   * Allocates the block for a query.
   */
  public final boolean allocate()
  {
    int useCount;

    do {
      useCount = _useCount.get();

      if (useCount < 1) {
        // The block might be LRU'd just as we're about to allocated it.
        // in that case, we need to allocate a new block, not reuse
        // the old one.
        return false;
      }
    } while (! _useCount.compareAndSet(useCount, useCount + 1));

    if (getBuffer() == null) {
      _useCount.decrementAndGet();
      Thread.dumpStack();
      log.fine(this + " null buffer " + this + " " + _useCount.get());
      return false;
    }

    if (log.isLoggable(Level.ALL))
      log.log(Level.ALL, this + " allocate (" + useCount + ")");

    //System.out.println(this + " ALLOCATE " + _useCount);

    if (useCount > 32 && log.isLoggable(Level.FINE)) {
      Thread.dumpStack();
      log.fine("using " + this + " " + useCount + " times");
    }

    return true;
  }

  /**
   * Reads into the block.
   */
  public void read()
    throws IOException
  {
    if (_state.get().isValid())
      return;

    synchronized (this) {
      if (_state.get().isValid()) {
      } else if (_store.getBlockManager().copyDirtyBlock(this)) {
        clearDirty();
        toValid();
      } else {
        if (log.isLoggable(Level.ALL))
          log.log(Level.ALL, "read db-block " + this);
        
        BlockReadWrite readWrite = _store.getReadWrite();
        
        readWrite.readBlock(_blockId & BlockStore.BLOCK_MASK,
                            getBuffer(), 0, BlockStore.BLOCK_SIZE);
        
        clearDirty();
        toValid();
      }
    }
  }

  /**
   * Returns the block's buffer.
   */
  public final byte []getBuffer()
  {
    return _buffer;
  }

  /**
   * Marks the data as valid.
   */
  void toValid()
  {
    toState(BlockState.VALID);
  }

  /**
   * Marks the block's data as dirty
   */
  public void setDirty(int min, int max)
  {
    if (BlockStore.BLOCK_SIZE < max || min < 0 || max < min)
      throw new IllegalStateException("min=" + min + ", max=" + max);

    long oldDirty;
    long newDirty;
    
    do {
      oldDirty = _dirtyRange.get();
      
      int dirtyMax = (int) (oldDirty >> 32);
      int dirtyMin = (int) oldDirty;
      
      if (min < dirtyMin)
        dirtyMin = min;
      
      if (dirtyMax < max)
        dirtyMax = max;
      
      newDirty = ((long) dirtyMax << 32) + dirtyMin;
    } while (! _dirtyRange.compareAndSet(oldDirty, newDirty));
  }

  /**
   * Callable only by the block itself, and must synchronize the Block.
   */
  private void clearDirty()
  {
    _dirtyRange.set(INIT_DIRTY);
  }

  /**
   * Handle any database writes necessary at commit time.  If
   * isFlushDirtyOnCommit() is true, this will write the data to
   * the backing file.
   */
  public void commit()
    throws IOException
  {
    if (! _isFlushDirtyOnCommit)
      return;
    else
      save();
  }

  public int getUseCount()
  {
    return _useCount.get();
  }
  
  public void deallocate()
    throws IOException
  {
    try {
      getStore().deallocateBlock(getBlockId());
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public void saveAllocation()
    throws IOException
  {
    getStore().saveAllocation();
  }

  /**
   * Frees a block from a query.
   */
  public final void free()
  {
    if (log.isLoggable(Level.ALL))
      log.log(Level.ALL, this + " free (" + _useCount.get() + ")");
    
    releaseUse();
  }
  
  /**
   * Called by the LRU cache before removing to see if the item can
   * actually be removed.
   */
  @Override
  public boolean startLruRemove()
  {
    save();

    if (_useCount.compareAndSet(1, 0)) {
      save();
      return true;
    }
    else {
      // if in use, can't remove
      return false;
    }
  }

  /**
   * Called when the block is removed from the cache.
   */
  @Override
  public final void syncLruRemoveEvent()
  {
    releaseUse();
  }
  
  /**
   * Called when the block is removed deliberately from the cache.
   */
  @Override
  public final void syncRemoveEvent()
  {
    releaseUse();
  }

  /**
   * Forces a write of the data.
   */
  private boolean save()
  {
    if (_dirtyRange.get() == INIT_DIRTY) {
      return false;
    }
    
    if (toWriteQueued()) {
      _store.getWriter().addDirtyBlock(this);
    }
    
    return true;
  }

  /**
   * Called by BlockWriter to actually write the block.
   */
  void writeFromBlockWriter()
    throws IOException
  {
    do {
      long dirty = _dirtyRange.getAndSet(INIT_DIRTY);

      int dirtyMax = (int) (dirty >> 32);
      int dirtyMin = (int) dirty;

      if (dirtyMin < dirtyMax) {
        if (log.isLoggable(Level.ALL))
          log.log(Level.ALL, "write db-block " + this + " [" + dirtyMin + ", " + dirtyMax + "]");

        boolean isPriority = false;

        writeImpl(dirtyMin, dirtyMax - dirtyMin, isPriority);
      }

      if (_dirtyRange.get() == INIT_DIRTY) {
        toState(BlockState.VALID);
      }
    } while (_dirtyRange.get() != INIT_DIRTY);

    if (_useCount.get() <= 0) {
      freeImpl();
    }
  }

  /**
   * Write the dirty block.
   */
  private void writeImpl(int offset, int length, boolean isPriority)
    throws IOException
  {
    BlockReadWrite readWrite = _store.getReadWrite();
    
    readWrite.writeBlock((_blockId & BlockStore.BLOCK_MASK) + offset,
                         getBuffer(), offset, length,
                         isPriority);
  }

  /**
   * Copies the contents to a target block. Used by the BlockWriter
   * for blocks being written
   */
  boolean copyToBlock(Block block)
  {
    if (block == this)
      return true;
      
    byte []buffer = _buffer;

    // XXX: need to allocate state
    boolean isValid = isValid();

    if (isValid) {
      System.arraycopy(buffer, 0, block.getBuffer(), 0, buffer.length);
      block.toValid();
    }
    
    return isValid;
  }
  
  private void releaseUse()
  {
    if (_useCount.decrementAndGet() <= 0) {
      freeImpl();
    }
  }

  /**
   * Called when the block is removed from the cache.
   */
  private void freeImpl()
  {
    if (_useCount.get() > 0)
      throw new IllegalStateException("freeImpl");

    //save();
    
    if (toDestroy()) {
      byte []buffer = _buffer;
      _buffer = null;
      
      if (buffer != null) {
        _freeBuffers.free(buffer);
      }
    }
  }
  
  public boolean isDestroyed()
  {
    return _state.get().isDestroyed();
  }

  private boolean toDestroy()
  {
    BlockState oldState;
    BlockState newState;
    
    do {
      oldState = _state.get();
      newState = oldState.toDestroy();
    } while (! _state.compareAndSet(oldState, newState));
    
    return newState.isDestroyed() && ! oldState.isDestroyed();
  }

  private boolean toWriteQueued()
  {
    BlockState oldState;
    BlockState newState;
    
    do {
      oldState = _state.get();
      newState = oldState.toWrite();
    } while (! _state.compareAndSet(oldState, newState));
    
    return newState.isWrite() && ! oldState.isWrite();
  }

  private static byte []allocateBuffer()
  {
    byte []buffer = _freeBuffers.allocate();

    if (buffer == null) {
      buffer = new byte[BlockStore.BLOCK_SIZE];
    }

    return buffer;
  }
  
  private BlockState toState(BlockState toState)
  {
    BlockState oldState;
    BlockState newState;
    
    do {
      oldState = _state.get();
      
      newState = oldState.toState(toState);
    } while (! _state.compareAndSet(oldState, newState));
    
    return oldState;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _store + "," + Long.toHexString(_blockId) + "]");
  }
}
