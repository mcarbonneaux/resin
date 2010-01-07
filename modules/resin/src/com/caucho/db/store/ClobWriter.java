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

package com.caucho.db.store;

import com.caucho.vfs.TempBuffer;

import java.io.IOException;
import java.io.Writer;

class ClobWriter extends Writer {
  private Store _store;

  private StoreTransaction _xa;
  
  private TempBuffer _tempBuffer;
  private byte []_buffer;
  private int _offset;
  private int _bufferEnd;

  private long _length;

  private Inode _inode;

  private byte []_inodeBuffer;
  private int _inodeOffset;

  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  ClobWriter(StoreTransaction xa, Store store, byte []inode, int inodeOffset)
  {
    init(xa, store, inode, inodeOffset);
  }
  
  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  ClobWriter(Inode inode)
  {
    init(null, inode.getStore(), inode.getBuffer(), 0);

    _inode = inode;
  }

  /**
   * Initialize the output stream.
   */
  public void init(StoreTransaction xa, Store store,
		   byte []inode, int inodeOffset)
  {
    if (xa == null)
      xa = RawTransaction.create();
      
    _xa = xa;
    
    _store = store;

    _length = 0;

    _inodeBuffer = inode;
    _inodeOffset = inodeOffset;

    Inode.clear(_inodeBuffer, _inodeOffset);

    _offset = 0;
    
    _tempBuffer = TempBuffer.allocate();
    _buffer = _tempBuffer.getBuffer();
    _bufferEnd = _buffer.length;
  }

  /**
   * Writes a byte.
   */
  @Override
  public void write(int v)
    throws IOException
  {
    if (_bufferEnd <= _offset) {
      flushBlock();
    }

    _buffer[_offset++] = (byte) (v >> 8);
    _buffer[_offset++] = (byte) (v);
    
    _length += 2;
  }

  /**
   * Writes a buffer.
   */
  public void write(char []buffer, int offset, int length)
    throws IOException
  {
    byte []byteBuffer = _buffer;
    int byteOffset = _offset;
    
    while (length > 0) {
      if (_bufferEnd <= byteOffset) {
	_offset = byteOffset;
	flushBlock();
	byteOffset = _offset;
      }

      int sublen = (_bufferEnd - byteOffset) >> 1;
      if (length < sublen)
	sublen = length;

      for (int i = 0; i < sublen; i++) {
	char ch = buffer[offset + i];
	
	byteBuffer[byteOffset++] = (byte) (ch >> 8);
	byteBuffer[byteOffset++] = (byte) (ch);
      }

      offset += sublen;

      length -= sublen;
      _length += 2 * sublen;
    }

    _offset = byteOffset;
  }

  /**
   * Updates the buffer.
   */
  private void flushBlock()
    throws IOException
  {
    int length = _offset;
    _offset = 0;

    Inode.append(_inodeBuffer, _inodeOffset, _store, _xa, _buffer, 0, length);
  }

  /**
   * Flushes the stream.
   */
  public void flush()
  {
  }

  /**
   * Completes the stream.
   */
  public void close()
    throws IOException
  {
    try {
      flushBlock();

      TempBuffer.free(_tempBuffer);
      _tempBuffer = null;
    } finally {
      if (_inode != null)
	_inode.closeOutputStream();
    }
  }

  /**
   * Writes the long.
   */
  private static void writeLong(byte []buffer, int offset, long v)
  {
    buffer[offset + 0] = (byte) (v >> 56);
    buffer[offset + 1] = (byte) (v >> 48);
    buffer[offset + 2] = (byte) (v >> 40);
    buffer[offset + 3] = (byte) (v >> 32);
    
    buffer[offset + 4] = (byte) (v >> 24);
    buffer[offset + 5] = (byte) (v >> 16);
    buffer[offset + 6] = (byte) (v >> 8);
    buffer[offset + 7] = (byte) (v);
  }

  /**
   * Reads a long.
   */
  private static long readLong(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xffL) << 56) |
	    ((buffer[offset + 1] & 0xffL) << 48) |
	    ((buffer[offset + 2] & 0xffL) << 40) |
	    ((buffer[offset + 3] & 0xffL) << 32) |
	    
	    ((buffer[offset + 4] & 0xffL) << 24) |
	    ((buffer[offset + 4] & 0xffL) << 16) |
	    ((buffer[offset + 4] & 0xffL) << 8) |
	    ((buffer[offset + 4] & 0xffL)));
  }

  /**
   * Writes the short.
   */
  private static void writeShort(byte []buffer, int offset, short v)
  {
    buffer[offset + 0] = (byte) (v >> 8);
    buffer[offset + 1] = (byte) (v);
  }

  /**
   * Reads a short.
   */
  private static int readShort(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xff) << 8) |
	    ((buffer[offset + 1] & 0xff)));
  }
}
