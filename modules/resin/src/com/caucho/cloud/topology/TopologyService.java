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

package com.caucho.cloud.topology;

import com.caucho.network.server.NetworkService;

/**
 * Interface for a service registered with the Resin Server.
 */
public class TopologyService implements NetworkService
{
  public static final int START_PRIORITY_CLASSLOADER = 999;
  public static final int START_PRIORITY_DEFAULT = 1000;
  
  public static final int STOP_PRIORITY_DEFAULT = 1000;
  public static final int STOP_PRIORITY_CLASSLOADER = 1001;
  
  private final CloudSystem _system;
  
  public TopologyService(String systemId)
  {
    _system = new CloudSystem(systemId);
  }
  
  public CloudSystem getSystem()
  {
    return _system;
  }

  @Override
  public int getStartPriority()
  {
    return START_PRIORITY_DEFAULT;
  }

  @Override
  public void start()
    throws Exception
  {
  }

  @Override
  public int getStopPriority()
  {
    return STOP_PRIORITY_DEFAULT;
  }

  @Override
  public void stop() 
    throws Exception
  {
  }

  @Override
  public void destroy()
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _system + "]";
  }
}
