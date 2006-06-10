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
package javax.jcr;

import java.io.InputStream;

import java.util.Calendar;

import javax.jcr.lock.*;
import javax.jcr.nodetype.*;
import javax.jcr.version.*;

public interface Property extends Item {
  public void setValue(Value value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(Value[] values)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(String value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(String[] values)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(InputStream value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(long value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(double value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(Calendar value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(boolean value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public void setValue(Node value)
    throws ValueFormatException,
	   VersionException,
	   LockException,
	   ConstraintViolationException,
	   RepositoryException;
  
  public Value getValue()
    throws ValueFormatException,
	   RepositoryException;
  
  public Value[] getValues()
    throws ValueFormatException,
	   RepositoryException;
  
  public String getString()
    throws ValueFormatException,
	   RepositoryException;
  
  public InputStream getStream()
    throws ValueFormatException,
	   RepositoryException;
  
  public long getLong()
    throws ValueFormatException,
	   RepositoryException;
  
  public double getDouble()
    throws ValueFormatException,
	   RepositoryException;
  
  public Calendar getDate()
    throws ValueFormatException,
	   RepositoryException;
  
  public boolean getBoolean()
    throws ValueFormatException,
	   RepositoryException;
  
  public Node getNode()
    throws ValueFormatException,
	   RepositoryException;
  
  public long getLength()
    throws ValueFormatException,
	   RepositoryException;
  
  public long[] getLengths()
    throws ValueFormatException,
	   RepositoryException;
  
  public PropertyDefinition getDefinition()
    throws RepositoryException;
  
  public int getType()
    throws RepositoryException;
}
