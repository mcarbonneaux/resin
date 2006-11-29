/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Sam
 */


package com.caucho.widget;

import com.caucho.util.L10N;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 */
public class WidgetContainerState
  extends WidgetState
  implements Map<String,WidgetState>
{
  private static L10N L = new L10N( WidgetContainerState.class );

  private Map<String, WidgetState> _childMap;

  public Set<Map.Entry<String,WidgetState>> entrySet()
  {
    if ( _childMap == null )
      return super.entrySet();
    else
      return _childMap.entrySet();
  }

  public boolean isEmpty()
  {
    return _childMap == null ? true :  _childMap.isEmpty();
  }

  public WidgetState put( String id, WidgetState value ) 
  {
    if ( _childMap == null )
      _childMap = new HashMap<String,WidgetState>();

    return _childMap.put( id, value );
  }

  public void decode( String[] data )
    throws WidgetException
  {
  }

  public String[] encode()
    throws WidgetException
  {
    return null;
  }

  public void reset()
  {
    _childMap = null;
  }
}
