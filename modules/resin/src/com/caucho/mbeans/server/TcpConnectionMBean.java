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

package com.caucho.mbeans.server;

import com.caucho.jmx.Description;

/**
 * Represents a tcp-connection
 */
@Description("A TCP connection")
public interface TcpConnectionMBean {
  /**
   * Returns the ObjectName.
   */
  @Description("The TCP connection's JMX ObjectName")
  public String getObjectName();

  /**
   * Returns the thread-id.  Management applications will use the
   * thread-id in conjunction with the JDK's ThreadMXBean to get more
   * Thread information.
   */
  @Description("The connections thread id.  If no thread is attached, returns -1")
  public long getThreadId();

  /**
   * Returns the connection state.
   */
  public String getState();

  /**
   * Returns the time in the active state.
   */
  public long getActiveTime();
}
