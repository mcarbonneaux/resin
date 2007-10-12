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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.ejb.session;

import com.caucho.ejb.AbstractContext;
import com.caucho.ejb.AbstractServer;

import javax.ejb.EJBObject;
import javax.ejb.Handle;
import javax.ejb.SessionContext;

import javax.xml.rpc.handler.MessageContext;

/**
 * Abstract base class for an session context
 */
abstract public class AbstractSessionContext extends AbstractContext
  implements SessionContext
{
  protected final SessionServer _server;

  private String _primaryKey;
  private EJBObject _remote;

  protected AbstractSessionContext(SessionServer server)
  {
    _server = server;
  }

  /**
   * Returns the owning server.
   */
  public AbstractServer getServer()
  {
    return _server;
  }

  /**
   * Returns the owning server.
   */
  public SessionServer getSessionServer()
  {
    return _server;
  }

  /**
   * Returns the object's handle.
   */
  public Handle getHandle()
  {
    return getSessionServer().createHandle(this);
  }

  /**
   * For session beans, returns the object.  For the home, return an
   * illegal state exception.
   */
  public EJBObject getEJBObject()
    throws IllegalStateException
  {
    return getRemoteView();
  }

  public String getPrimaryKey()
  {
    if (_primaryKey == null)
      _primaryKey = getSessionServer().createSessionKey(this);

    return _primaryKey;
  }

  public <T> T getBusinessObject(Class<T> businessInterface)
  {
    // XXX: it needs to check multiple business interfaces.
    return (T) getSessionServer().getRemoteObject30();
  }

  public Class getInvokedBusinessInterface()
  {
    throw new UnsupportedOperationException();
  }

  public MessageContext getMessageContext()
  {
    throw new UnsupportedOperationException();
  }
}
