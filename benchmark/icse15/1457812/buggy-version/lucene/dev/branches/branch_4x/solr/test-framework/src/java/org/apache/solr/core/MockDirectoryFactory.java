  Merged /lucene/dev/trunk/solr/core:r1457784
  Merged /lucene/dev/trunk/solr/solrj:r1457784
  Merged /lucene/dev/trunk/solr/example:r1457784
  Merged /lucene/dev/trunk/solr/build.xml:r1457784
  Merged /lucene/dev/trunk/solr/NOTICE.txt:r1457784
  Merged /lucene/dev/trunk/solr/LICENSE.txt:r1457784
  Merged /lucene/dev/trunk/solr/contrib:r1457784
  Merged /lucene/dev/trunk/solr/site:r1457784
  Merged /lucene/dev/trunk/solr/SYSTEM_REQUIREMENTS.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpclient-LICENSE-ASL.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpcore-LICENSE-ASL.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpcore-NOTICE.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpmime-LICENSE-ASL.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpclient-NOTICE.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses/httpmime-NOTICE.txt:r1457784
  Merged /lucene/dev/trunk/solr/licenses:r1457784
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

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Opens a directory with {@link LuceneTestCase#newDirectory()}
 */
public class MockDirectoryFactory extends EphemeralDirectoryFactory {

  @Override
  protected Directory create(String path, DirContext dirContext) throws IOException {
    Directory dir = LuceneTestCase.newDirectory();
    if (dir instanceof MockDirectoryWrapper) {
      MockDirectoryWrapper mockDirWrapper = (MockDirectoryWrapper) dir;
      
      // we can't currently do this check because of how
      // Solr has to reboot a new Directory sometimes when replicating
      // or rolling back - the old directory is closed and the following
      // test assumes it can open an IndexWriter when that happens - we
      // have a new Directory for the same dir and still an open IW at 
      // this point
      mockDirWrapper.setAssertNoUnrefencedFilesOnClose(false);
      
      // ram dirs in cores that are restarted end up empty
      // and check index fails
      mockDirWrapper.setCheckIndexOnClose(false);
      
      // if we enable this, TestReplicationHandler fails when it
      // tries to write to index.properties after the file has
      // already been created.
      mockDirWrapper.setPreventDoubleWrite(false);
    }
    
    return dir;
  }
  
  @Override
  public boolean isAbsolute(String path) {
    // TODO: kind of a hack - we don't know what the delegate is, so
    // we treat it as file based since this works on most ephem impls
    return new File(path).isAbsolute();
  }

}
