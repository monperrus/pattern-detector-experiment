package org.apache.solr.core;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FlushInfo;
import org.apache.lucene.store.IOContext;
import org.apache.solr.common.SolrException;
import org.apache.solr.core.CachingDirectoryFactory.CloseListener;
import org.apache.solr.util.plugin.NamedListInitializedPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides access to a Directory implementation. You must release every
 * Directory that you get.
 */
public abstract class DirectoryFactory implements NamedListInitializedPlugin,
    Closeable {

  // Estimate 10M docs, 100GB size, to avoid caching by NRTCachingDirectory
  // Stayed away from upper bounds of the int/long in case any other code tried to aggregate these numbers.
  // A large estimate should currently have no other side effects.
  public static final IOContext IOCONTEXT_NO_CACHE = new IOContext(new FlushInfo(10*1000*1000, 100L*1000*1000*1000));

  // hint about what the directory contains - default is index directory
  public enum DirContext {DEFAULT, META_DATA}

  private static final Logger log = LoggerFactory.getLogger(DirectoryFactory.class.getName());
  
  /**
   * Indicates a Directory will no longer be used, and when it's ref count
   * hits 0, it can be closed. On shutdown all directories will be closed
   * whether this has been called or not. This is simply to allow early cleanup.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract void doneWithDirectory(Directory directory) throws IOException;
  
  /**
   * Adds a close listener for a Directory.
   */
  public abstract void addCloseListener(Directory dir, CloseListener closeListener);
  
  /**
   * Close the this and all of the Directories it contains.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  @Override
  public abstract void close() throws IOException;
  
  /**
   * Creates a new Directory for a given path.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  protected abstract Directory create(String path,  DirContext dirContext) throws IOException;
  
  /**
   * Returns true if a Directory exists for a given path.
   * @throws IOException If there is a low-level I/O error.
   * 
   */
  public abstract boolean exists(String path) throws IOException;
  
  /**
   * Removes the Directory's persistent storage.
   * For example: A file system impl may remove the
   * on disk directory.
   * @throws IOException If there is a low-level I/O error.
   * 
   */
  public abstract void remove(Directory dir) throws IOException;
  
  /**
   * This remove is special in that it may be called even after
   * the factory has been closed. Remove only makes sense for
   * persistent directory factories.
   * 
   * @param path to remove
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract void remove(String path) throws IOException;
  
  /**
   * Override for more efficient moves.
   * 
   * Intended for use with replication - use
   * carefully - some Directory wrappers will
   * cache files for example.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public void move(Directory fromDir, Directory toDir, String fileName, IOContext ioContext) throws IOException {
    fromDir.copy(toDir, fileName, fileName, ioContext);
    fromDir.deleteFile(fileName);
  }
  
  /**
   * Returns the Directory for a given path, using the specified rawLockType.
   * Will return the same Directory instance for the same path.
   * 
   * Note: sometimes you might pass null for the rawLockType when
   * you know the Directory exists and the rawLockType is already
   * in use.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract Directory get(String path, DirContext dirContext, String rawLockType)
      throws IOException;
  
  /**
   * Returns the Directory for a given path, using the specified rawLockType.
   * Will return the same Directory instance for the same path unless forceNew,
   * in which case a new Directory is returned. There is no need to call
   * {@link #doneWithDirectory(Directory)} in this case - the old Directory
   * will be closed when it's ref count hits 0.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract Directory get(String path,  DirContext dirContext, String rawLockType,
      boolean forceNew) throws IOException;
  
  /**
   * Increment the number of references to the given Directory. You must call
   * release for every call to this method.
   * 
   */
  public abstract void incRef(Directory directory);
  
  
  /**
   * @return true if data is kept after close.
   */
  public abstract boolean isPersistent();
  
  /**
   * Releases the Directory so that it may be closed when it is no longer
   * referenced.
   * 
   * @throws IOException If there is a low-level I/O error.
   */
  public abstract void release(Directory directory) throws IOException;
  
  
  /**
   * Normalize a given path.
   * 
   * @param path to normalize
   * @return normalized path
   * @throws IOException on io error
   */
  public String normalize(String path) throws IOException {
    return path;
  }
  
  /**
   * @param path the path to check
   * @return true if absolute, as in not relative
   */
  public boolean isAbsolute(String path) {
    // back compat
    return new File(path).isAbsolute();
  }
  
  public static long sizeOfDirectory(Directory directory) throws IOException {
    final String[] files = directory.listAll();
    long size = 0;
    
    for (final String file : files) {
      size += sizeOf(directory, file);
      if (size < 0) {
        break;
      }
    }
    
    return size;
  }
  
  public static long sizeOf(Directory directory, String file) throws IOException {
    if (!directory.fileExists(file)) {
      return 0;
    }
    
    return directory.fileLength(file);
  }
  
  /**
   * Delete the files in the Directory
   */
  public static boolean empty(Directory dir) {
    boolean isSuccess = true;
    String contents[];
    try {
      contents = dir.listAll();
      if (contents != null) {
        for (String file : contents) {
          dir.deleteFile(file);
        }
      }
    } catch (IOException e) {
      SolrException.log(log, "Error deleting files from Directory", e);
      isSuccess = false;
    }
    return isSuccess;
  }
  
}
