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

package com.caucho.hemp.pubsub;

import java.io.Serializable;
import java.util.*;

/**
 * Publish query
 */
public class PubSubPublish extends PubSubQuery {
  private String node;
  
  private ArrayList<PubSubItem> items;
  private String subid;

  public PubSubPublish()
  {
  }

  public PubSubPublish(String node)
  {
    this.node = node;
  }

  public String getNode()
  {
    return this.node;
  }

  public String getSubId()
  {
    return this.subid;
  }

  public void addItem(PubSubItem item)
  {
    if (this.items == null)
      this.items = new ArrayList<PubSubItem>();

    this.items.add(item);
  }

  public Iterator<PubSubItem> iterator()
  {
    if (this.items != null)
      return this.items.iterator();
    else
      return Collections.EMPTY_LIST.iterator();
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + this.node + "]";
  }
}
