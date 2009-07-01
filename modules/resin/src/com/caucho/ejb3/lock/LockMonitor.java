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
 * @author Reza Rahman
 */
package com.caucho.ejb3.lock;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Monitors EJB locks in the current thread.
 * 
 * @author Reza Rahman
 */
public class LockMonitor {
  private static final ThreadLocal<ReentrantReadWriteLock> threadLocal = new ThreadLocal<ReentrantReadWriteLock>();

  /**
   * Setting the lock to be monitored.
   * 
   * @param lock
   *          The lock to be monitored.
   */
  synchronized static void setLock(ReentrantReadWriteLock lock)
  {
    threadLocal.set(lock);
  }

  /**
   * Gets the number of threads waiting for a read or write lock.
   * 
   * @return Number of threads waiting for a read or write lock.
   */
  public synchronized static int getNumberOfWaitingThreads()
  {
    return threadLocal.get().getQueueLength();
  }

  /**
   * Gets the number of read locks held.
   * 
   * @return Number of read locks held.
   */
  public synchronized static int getNumberOfReadLocks()
  {
    return threadLocal.get().getReadLockCount();
  }

  /**
   * Checks if the write lock is being held.
   * 
   * @return true if the write lock is being held, false if not.
   */
  public synchronized static boolean isWriteLocked()
  {
    return threadLocal.get().isWriteLocked();
  }
}