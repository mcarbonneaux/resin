/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.mqueue.amqp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AMQP session-begin frame 
 */
public class AmqpSessionBegin extends AmqpAbstractPacket {
  public static final int CODE = AmqpConstants.FT_SESSION_OPEN;

  private int _remoteChannel;   // ushort
  private long _nextOutgoingId; // uint xfer-no/seq-no (required) RFC-1982
  private int _incomingWindow;  // uint (required)
  private int _outgoingWindow;  // uint (required)
  private int _handleMax;       // uint handle
  private List<String> _offeredCapabilities;
  private List<String> _desiredCapabilities;
  private Map<String,?> _properties;
  
  public void setRemoteChannel(int channel)
  {
    _remoteChannel = channel;
  }
  
  public void setNextOutgoingId(long id)
  {
    _nextOutgoingId = id;
  }
  
  public void setIncomingWindow(int window)
  {
    _incomingWindow = window;
  }
  
  public void setOutgoingWindow(int window)
  {
    _outgoingWindow = window;
  }
  
  public void setHandleMax(int max)
  {
    _handleMax = max;
  }
  
  @Override
  public void write(AmqpWriter out)
    throws IOException
  {
    out.writeDescriptor(FT_SESSION_OPEN);
    
    out.writeUshort(_remoteChannel);
    
    out.writeUlong(_nextOutgoingId);
    out.writeUint(_incomingWindow);
    out.writeUint(_outgoingWindow);
    out.writeUint(_handleMax);
    
    out.writeArray(_offeredCapabilities);
    out.writeArray(_desiredCapabilities);
    out.writeMap(_properties);
  }
}
