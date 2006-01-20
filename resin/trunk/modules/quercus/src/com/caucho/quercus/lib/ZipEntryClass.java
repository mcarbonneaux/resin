/*
 * Copyright (c) 1998-2005 Caucho Technology -- all rights reserved
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
 * @author Charles Reich
 */

package com.caucho.quercus.lib;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.util.L10N;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.module.Optional;

public class ZipEntryClass {
  private static final Logger log = Logger.getLogger(ZipEntryClass.class.getName());
  private static final L10N L = new L10N(ZipEntryClass.class);

  private ZipEntry _zipEntry;
  private ZipClass _zipClass;

  public ZipEntryClass(ZipEntry zipEntry)
  {
    _zipEntry = zipEntry;
  }

  public String zip_entry_name()
  {
    return _zipEntry.getName();
  }

  public long zip_entry_filesize()
  {
    return _zipEntry.getSize();
  }

  /**
   *
   * @param zipClass
   * @return always returns true because we are using ZipInputStream
   */
  public boolean zip_entry_open(ZipClass zipClass)
  {
    _zipClass = zipClass;
    return true;
  }

  /**
   * stubbed out for now.  Not sure if zip_entry_close is
   * applicable given we are using ZipInputStream
   *
   * @return true.  has no meaning
   */
  public boolean zip_entry_close()
  {
    return true;
  }

  /**
   *
   *
   * @param length
   * @return FALSE if end of file or IOException
   */
  public Value zip_entry_read (@Optional("1024") int length)
  {
    byte[] buf = new byte[length];
    int numBytes;
    ZipInputStream zis = _zipClass.getZipInputStream();

    try {
      numBytes = zis.read(buf,0,length);
    } catch (IOException ex) {
      log.log(Level.FINE,  ex.toString(),  ex);
      return BooleanValue.FALSE;
    }

    if (numBytes == 0)
      return BooleanValue.FALSE;
    else {
     // System.out.println("buf: " + buf);
     // System.out.println("numBytes: " + numBytes);
     // System.out.println("buf.length: " + buf.length);
     // System.out.println("length: " + length);
      return new StringValue(buf.toString());
    }
  }

  public long zip_entry_compressedsize()
  {
    if (_zipEntry == null)
      return -1;

    return _zipEntry.getCompressedSize();
  }

  /**
   * seems like only two values are:
   * ZipEntry.DEFLATED
   * ZipEntry.STORED
   *
   * @return the compression method
   */
  public String zip_entry_compressionmethod()
  {
    if (_zipEntry == null)
      return "";

    Integer method = _zipEntry.getMethod();

    switch(method) {
      case ZipEntry.DEFLATED:
        return "deflated";
      case ZipEntry.STORED:
        return "stored";
      default:
        return method.toString();
    }
  }
}
