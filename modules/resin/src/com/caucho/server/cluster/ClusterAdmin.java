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
 * @author Sam
 */


package com.caucho.server.cluster;

import java.io.Serializable;

import javax.management.ObjectName;

import com.caucho.mbeans.server.ClusterMBean;
import com.caucho.mbeans.server.ClusterServerMBean;
import com.caucho.mbeans.server.HostMBean;
import com.caucho.mbeans.server.PersistentStoreMBean;
import com.caucho.mbeans.server.PortMBean;

public class ClusterAdmin
  implements ClusterMBean, Serializable
{
  private final Cluster _cluster;

  public ClusterAdmin(Cluster cluster)
  {
    _cluster = cluster;
  }

  public ObjectName getObjectName()
  {
    return _cluster.getObjectName();
  }

  public PortMBean getPort()
  {
    ClusterServer clusterServer = _cluster.getSelfServer();

    if (clusterServer == null)
      return null;

     return clusterServer.getClusterPort().getAdmin();
  }

  public PersistentStoreMBean getPersistentStore()
  {
    return null;
  }

  public HostMBean []getHosts()
  {
    return new HostMBean[0];
  }

  public ClusterServerMBean []getServers()
  {
    ClusterServer selfServer = _cluster.getSelfServer();

    ClusterServer[] serverList = _cluster.getServerList();

    int len = serverList.length;

    if (selfServer != null)
      len--;

    ClusterServerMBean []serverMBeans = new ClusterServerMBean[len];

    int j = 0;

    for (int i = 0; i < serverList.length; i++) {
      ClusterServer server = serverList[i];

      if (server != selfServer)
        serverMBeans[j++] = server.getAdmin();
    }

    return serverMBeans;
  }

  public String toString()
  {
    return "ClusterAdmin[" + getObjectName() + "]";
  }
}
