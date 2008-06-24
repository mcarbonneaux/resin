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
 * @author Emil Ong
 */

package com.caucho.quercus.lib.bam;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.bam.BamStream;
import com.caucho.config.ConfigException;
import com.caucho.hemp.broker.GenericService;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.webbeans.manager.WebBeansContainer;
import com.caucho.xmpp.disco.DiscoInfoQuery;

import javax.annotation.PostConstruct;

/**
 * BAM agent spawns a new BamPhpAgent when requested.
 **/
public class BamPhpRootService extends GenericService {
  private static final L10N L = new L10N(BamPhpAgent.class);
  private static final Logger log
    = Logger.getLogger(BamPhpRootService.class.getName());

  private final HashMap<String,BamPhpAgent> _agents = 
    new HashMap<String,BamPhpAgent>();

  private Path _script;
  private String _encoding = "ISO-8859-1";

  public Path getScript()
  {
    return _script;
  }

  public void setScript(Path script)
  {
    _script = script;
  }

  public String getEncoding()
  {
    return _encoding;
  }

  public void setEncoding(String encoding)
  {
    _encoding = encoding;
  }

  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (_script == null)
      throw new ConfigException(L.l("script path not specified"));

    super.init();
  }

  @Override
  public BamStream findAgent(String jid)
  {
    if (log.isLoggable(Level.FINE)) 
      log.fine(L.l("{0}.findAgent({1})", toString(), jid));

    BamPhpAgent agent = _agents.get(jid);

    if (agent == null) {
      agent = new BamPhpAgent(_script, _encoding);
      agent.setName(jid);

      WebBeansContainer container = WebBeansContainer.getCurrent();
      container.injectObject(agent);

      _agents.put(jid, agent);
    }

    return agent;
  }

  public String toString()
  {
    return "BamPhpRootService[jid=" + getJid() + 
                            ",script=" + _script + "]";
  }
}
