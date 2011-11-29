/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.remote.websocket;

import java.io.IOException;
import java.io.Reader;

/**
 * WebSocketReader reads a single WebSocket packet.
 *
 * <code><pre>
 * 0x00 utf-8 data 0xff
 * </pre></code>
 */
public class WebSocketReader extends Reader
  implements WebSocketConstants
{
  private FrameInputStream _is;

  private boolean _isFinal;
  private long _length;

  public WebSocketReader(FrameInputStream is)
    throws IOException
  {
    _is = is;
  }

  public void init()
    throws IOException
  {
    _isFinal = _is.isFinal();
    _length = _is.getLength();
  }

  public long getLength()
  {
    return _length;
  }

  @Override
  public int read()
    throws IOException
  {
    int d1 = readByte();
    
    if (d1 < 0x80)
      return d1;
    
    if ((d1 & 0xe0) == 0xc0) {
      int d2 = readByte();
      
      return ((d1 & 0x1f) << 6) + (d2 & 0x3f);
    }
    else {
      int d2 = readByte();
      int d3 = readByte();
      
      return ((d2 & 0xf) << 12) + ((d2 & 0x3f) << 6) + (d3 & 0x3f);
    }
  }
  
  @Override
  public int read(char []buffer, int offset, int length)
    throws IOException
  {
    int i = 0;
    
    int d1;
    
    while (length-- > 0 && (d1 = readByte()) >= 0) {
      char ch;
      
      if (d1 < 0x80) {
        ch = (char) d1;
      }
      else if ((d1 & 0xe0) == 0xc0) {
        int d2 = readByte();
        
        ch = (char) (((d1 & 0x1f) << 6) + (d2 & 0x3f));
      }
      else {
        int d2 = readByte();
        int d3 = readByte();
        
        ch = (char) (((d2 & 0xf) << 12) + ((d2 & 0x3f) << 6) + (d3 & 0x3f));
      }
      
      buffer[offset + i++] = ch;
    }
    
    if (i == 0)
      return -1;
    else
      return i;
  }
  
  private int readByte()
    throws IOException
  {
    FrameInputStream is = _is;

    while (_length == 0 && ! _isFinal) {
      if (! is.readFrameHeader())
        return -1;
      
      _isFinal = is.isFinal();
      _length = is.getLength();
    }

    if (_length > 0) {
      int ch = is.read();
      _length--;
      return ch;
    }
    else
      return -1;
  }

  @Override
  public void close()
  throws IOException
  {
    while (_length > 0 && !_isFinal) {
      skip(_length);
    }
  }
}
