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
 * @author Scott Ferguson
 */

package com.caucho.vfs;

import java.io.*;
import java.net.*;
import java.util.*;

import com.caucho.util.*;

/**
 * Always returns <code>FileNotFound</code> for any open attempt.
 * <code>NotFoundPath</code> is a useful utility Path for MergePath when
 * the path doesn't exist in any of the merged paths.
 *
 * @since Resin 1.2
 */
class NotFoundPath extends Path {
  private String _url;

  /**
   * Creates new NotFoundPath
   */
  NotFoundPath(String url)
  {
    super(null);

    _url = url;
    _schemeMap = SchemeMap.getNullSchemeMap();
  }

  /**
   * Dummy return.
   */
  public Path schemeWalk(String userPath,
			 Map<String,Object> attributes,
                         String path, int offset)
  {
    return this;
  }

  /**
   * The URL is error
   */
  public String getURL()
  {
    return "error:" + _url;
  }

  public String getScheme()
  {
    return "error";
  }

  /**
   * Returns the URL which can't be found.
   */
  public String getPath()
  {
    return _url;
  }

  /**
   * Dummy return.
   */
  public Path lookupImpl(String userPath, Map<String,Object> newAttributes)
  {
    return this;
  }

  /**
   * Throws a FileNotFoundException for any read.
   */
  protected StreamImpl openReadImpl()
    throws IOException
  {
    throw new FileNotFoundException(_url);
  }
}
