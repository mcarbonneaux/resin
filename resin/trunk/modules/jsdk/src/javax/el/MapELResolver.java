/*
 * Copyright (c) 1998-2003 Caucho Technology -- all rights reserved
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

package javax.el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.*;

/**
 * Resolves properties based on maps.
 */
public class MapELResolver extends ELResolver {
  private final static Logger log
    = Logger.getLogger(MapELResolver.class.getName());
  
  private final boolean _isReadOnly;
  
  public MapELResolver()
  {
    _isReadOnly = false;
  }
  
  public MapELResolver(boolean isReadOnly)
  {
    _isReadOnly = isReadOnly;
  }

  @Override
  public Class<?> getCommonPropertyType(ELContext context, Object base)
  {
    if (base instanceof Map) {
      context.setPropertyResolved(true);
      
      return Object.class;
    }
    else
      return null;
  }

  @Override
  public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context,
							   Object base)
  {
    if (base instanceof Map) {
      context.setPropertyResolved(true);

      ArrayList<FeatureDescriptor> keys = new ArrayList<FeatureDescriptor>();

      for (Object key : ((Map) base).keySet()) {
	String name = String.valueOf(key);

	FeatureDescriptor desc = new FeatureDescriptor();
	desc.setName(name);
	desc.setDisplayName(name);
	desc.setShortDescription("");
	desc.setExpert(false);
	desc.setHidden(false);
	desc.setPreferred(true);

	if (key == null)
	  desc.setValue(ELResolver.TYPE, null);
	else
	  desc.setValue(ELResolver.TYPE, key.getClass());
	
	desc.setValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
	
	keys.add(desc);
      }

      return keys.iterator();
    }
    else {
      return null;
    }
  }

  @Override
  public Class<?> getType(ELContext context,
			  Object base,
			  Object property)
  {
    if (base instanceof Map) {
      Object value = getValue(context, base, property);

      if (value != null)
	return value.getClass();
      else
	return null;
    }
    else
      return null;
  }

  @Override
  public Object getValue(ELContext context,
			 Object base,
			 Object property)
  {
    if (base instanceof Map && property != null) {
      Map map = (Map) base;
      
      context.setPropertyResolved(true);

      return map.get(property);
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isReadOnly(ELContext context,
			    Object base,
			    Object property)
  {
    if (base instanceof Map) {
      context.setPropertyResolved(true);
      
      return _isReadOnly;
    }
    else
      return true;
  }

  @Override
  public void setValue(ELContext context,
		       Object base,
		       Object property,
		       Object value)
  {
    if (base instanceof Map && property != null) {
      Map map = (Map) base;
      
      context.setPropertyResolved(true);

      map.put(property, value);
    }
  }
}
