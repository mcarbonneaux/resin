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

package com.caucho.server.admin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.caucho.bam.Query;
import com.caucho.bam.proxy.ReplyCallback;
import com.caucho.cloud.deploy.CopyTagQuery;
import com.caucho.cloud.deploy.RemoveTagQuery;
import com.caucho.cloud.deploy.SetTagQuery;
import com.caucho.config.ConfigException;
import com.caucho.env.deploy.DeployControllerService;
import com.caucho.env.deploy.DeployException;
import com.caucho.env.deploy.DeployTagItem;
import com.caucho.env.git.GitTree;
import com.caucho.env.repository.RepositorySpi;
import com.caucho.env.repository.RepositoryTagEntry;
import com.caucho.env.service.ResinSystem;
import com.caucho.env.thread.ThreadPool;
import com.caucho.lifecycle.LifecycleState;
import com.caucho.server.admin.DeployActor.BlobStreamSource;
import com.caucho.util.IoUtil;
import com.caucho.util.L10N;
import com.caucho.vfs.StreamSource;

public class DeployActorProxyImpl
{
  private static final L10N L = new L10N(DeployActorProxyImpl.class);
  
  private static final Logger log
    = Logger.getLogger(DeployActorProxyImpl.class.getName());
  
  private static final String UID = "deploy";
  
  private String _serverId;
  private RepositorySpi _repository;
  
  private DeployActor _deployActor;
  private DeployActorProxy _podDeployProxy;

  public DeployActorProxyImpl(RepositorySpi repository,
                              DeployActor deployActor)
  {
    _serverId = ResinSystem.getCurrentId();
    
    _repository = repository;
    _deployActor = deployActor;
    
    if (repository == null) {
      throw new NullPointerException();
    }
  }

  
  public String []getCommitList(String []commitList)
  {
    ArrayList<String> uncommittedList = new ArrayList<String>();
    
    if (commitList != null) {
      for (String commit : commitList) {
        if (! _repository.exists(commit))
          uncommittedList.add(commit);
      }
    }
    
    String []result = new String[uncommittedList.size()];
    uncommittedList.toArray(result);

    return result;
  }

  /**
   * Returns a file to the client.
   */
  public StreamSource getFile(String tag, String fileName)
    throws IOException
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer(this + " getFile(" + tag + "," + fileName + ")");
    }
    
    while (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }
    
    RepositoryTagEntry entry = _repository.getTagMap().get(tag);
    
    if (entry == null) {
      throw new ConfigException(L.l("'{0}' is an unknown repository tag",
                                    tag));
    }
    
    String sha1 = entry.getRoot();
    
    String fileSha = findFile(sha1, fileName, fileName);

    BlobStreamSource iss = new BlobStreamSource(_repository, fileSha);
    
    return new StreamSource(iss);
  }
  
  private String findFile(String sha1, String fullFilename, String fileName)
    throws IOException
  {
    if (fileName.equals("")) {
      if (_repository.isBlob(sha1))
        return sha1;
      else {
        throw new ConfigException(L.l("'{0}' is not a file", fullFilename));
      }
    }
    
    int p = fileName.indexOf('/');
    String tail = "";
    
    if (p > 0) {
      tail = fileName.substring(p + 1);
      fileName = fileName.substring(0, p);
    }
    
    if (! _repository.isTree(sha1))
      throw new ConfigException(L.l("'{0}' is an invalid path", fullFilename));
    
    GitTree tree = _repository.readTree(sha1);
    
    String childSha1 = tree.getHash(fileName);
    
    if (childSha1 == null)
      throw new ConfigException(L.l("'{0}' is an unknown file",
                                    fullFilename));
   
    return findFile(childSha1, fullFilename, tail);
  }

  /**
   * Receives a file from the client.
   * 
   * @param sha1 the hash identifier for the file
   * @param source the binary stream content
   */
  public boolean sendFile(String sha1, StreamSource source)
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " sendFile sha1=" + sha1);

    InputStream is = null;
    try {
      is = source.getInputStream();

      _repository.writeRawGitFile(sha1, is);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);

      return false;
    } finally {
      IoUtil.close(is);
    }

    return true;
  }

  public String []getFileList(String tag, String fileName)
    throws IOException
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer(this + " getFileList(" + tag + "," + fileName + ")");
    }
    
    RepositoryTagEntry entry = _repository.getTagMap().get(tag);
    
    if (entry == null) {
      throw new ConfigException(L.l("'{0}' is an unknown repository tag",
                                    tag));
    }
    
    String sha1 = entry.getRoot();
    
    ArrayList<String> fileList = new ArrayList<String>();
    
    listFiles(fileList, sha1, "");
    
    Collections.sort(fileList);
    
    String []files = new String[fileList.size()];
    
    fileList.toArray(files);

    return files;
  }
  
  private void listFiles(ArrayList<String> files, 
                         String sha1,
                         String prefix)
    throws IOException
  {
    if (sha1 == null)
      return;
    
    if (_repository.isBlob(sha1)) {
      files.add(prefix);
      return;
    }
    
    if (! _repository.isTree(sha1))
      throw new ConfigException(L.l("'{0}' is an invalid path", prefix));
    
    GitTree tree = _repository.readTree(sha1);
    
    for (String key : tree.getMap().keySet()) {
      String name;
      
      if ("".equals(prefix))
        name = key;
      else
        name = prefix + "/" + key;
      
      listFiles(files, tree.getHash(key), name);
    }
  }
  
  //
  // tag methods
  //

  public boolean putTag(String tagName, 
                        String contentHash,
                        Map<String,String> attributes)
  {
    if (contentHash == null)
      throw new NullPointerException();

    String server = "default";
    
    TreeMap<String,String> commitMetaData = new TreeMap<String,String>();
    
    if (attributes != null)
      commitMetaData.putAll(attributes);
    
    commitMetaData.put("server", server);
    
    return _repository.putTag(tagName, 
                              contentHash,
                              commitMetaData);
  }

  public boolean tagCopy(String tag, 
                         String sourceTag, 
                         Map<String,String> attributes)
  {
    RepositoryTagEntry entry = _repository.getTagMap().get(sourceTag);

    if (entry == null) {
      log.fine(this + " copyError dst='" + tag + "' src='" + sourceTag + "'");

      throw new DeployException(L.l("deploy-copy: '{0}' is an unknown source tag.",
                                    sourceTag));
      /*
      getBroker().queryError(id, from, to, query,
                             new BamError(BamError.TYPE_CANCEL,
                                          BamError.ITEM_NOT_FOUND,
                             "unknown tag"));
      return;
      */
    }

    log.fine(this + " copy dst='" + tag + "' src='" + sourceTag + "'");
    
    String server = "default";
    
    TreeMap<String,String> metaDataMap = new TreeMap<String,String>();
    
    if (attributes != null)
      metaDataMap.putAll(attributes);
    
    if (server != null)
      metaDataMap.put("server", server);

    return _repository.putTag(tag, entry.getRoot(), metaDataMap);
  }

  public TagResult []queryTags(String regexp)
  {
    ArrayList<TagResult> tags = new ArrayList<TagResult>();

    Pattern pattern = Pattern.compile(regexp);

    for (Map.Entry<String, RepositoryTagEntry> entry :
         _repository.getTagMap().entrySet()) {
      String tag = entry.getKey();

      if (pattern.matcher(tag).matches()) {
        tags.add(new TagResult(tag, entry.getValue().getRoot()));
      }
    }

    return tags.toArray(new TagResult[tags.size()]);
  }

  public TagStateQuery getTagState(String tag)
  {
    // XXX: just ping the tag?
    // updateDeploy();
    
    DeployControllerService deploy = DeployControllerService.getCurrent();
    DeployTagItem item = null;
    
    if (deploy != null) {
      deploy.update(tag);
      item = deploy.getTagItem(tag);
    }
    
    if (item != null) {
      return new TagStateQuery(tag, item.getStateName(),
                               item.getDeployException());
    }
    else {
      return null;
    }
  }

  public boolean removeTag(String tag, Map<String,String> attributes)
  {
    if (log.isLoggable(Level.FINE))
      log.fine(this + " removeTag " + tag);
    
    String server = "default";
    
    HashMap<String,String> commitMetaData = new HashMap<String,String>();
    
    if (attributes != null) {
      commitMetaData.putAll(attributes);
    }
    
    commitMetaData.put("server", server);

    return _repository.removeTag(tag, commitMetaData);
  }

  //
  // start/restart
  //

  public ControllerStateActionQueryReply start(String tag)
  {
    LifecycleState state = startImpl(tag);

    ControllerStateActionQueryReply result
      = new ControllerStateActionQueryReply(tag, state);

    log.fine(this
             + " start '"
             + tag
             + "' -> "
             + state.getStateName());

    return result;
  }

  private LifecycleState startImpl(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();
    
    DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));

    controller.toStart();

    return controller.getState();
  }

  /**
   * @deprecated
   */
  public ControllerStateActionQueryReply stop(String tag)
  {
    LifecycleState state = stopImpl(tag);

    log.fine(this + " stop '" + tag + "' -> " + state.getStateName());

    return new ControllerStateActionQueryReply(tag, state);
  }

  private LifecycleState stopImpl(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();

    DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));
    controller.toStop();

    return controller.getState();
  }

  public ControllerStateActionQueryReply controllerRestart(String tag)
  {
    return controllerRestart(tag);
  }

  public ControllerStateActionQueryReply restart(String tag)
  {
    LifecycleState state = restartImpl(tag);

    ControllerStateActionQueryReply result
      = new ControllerStateActionQueryReply(tag, state);

    log.fine(this
             + " restart '"
             + tag
             + "' -> "
             + state.getStateName());

    return result;
  }

  public void restartCluster(String tag, ReplyCallback<ControllerStateActionQueryReply> cb)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  private LifecycleState restartImpl(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();

    final DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));

    ThreadPool.getCurrent().schedule(new Runnable() {
      public void run() {
        controller.toRestart();
      }
    });

    return controller.getState();
  }
  
  public String getUid()
  {
    return UID;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serverId + "]";
  }
}
