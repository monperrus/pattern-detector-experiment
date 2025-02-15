package org.apache.lucene.index;

/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.Random;
import java.util.Stack;

import junit.framework.TestCase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;

/**
 * Tests for the "Index" class, including accesses from two threads at the
 * same time.
 * 
 * @author Daniel Naber
 */
public class TestIndexModifier extends TestCase {

  private final int ITERATIONS = 500;		// iterations of thread test

  private int docCount = 0;
  
  private final Term allDocTerm = new Term("all", "x");

  public void testIndex() throws IOException {
    Directory ramDir = new RAMDirectory();
    IndexModifier i = new IndexModifier(ramDir, new StandardAnalyzer(), true);
    i.addDocument(getDoc());
    assertEquals(1, i.docCount());
    i.flush();
    i.addDocument(getDoc(), new SimpleAnalyzer());
    assertEquals(2, i.docCount());
    i.optimize();
    assertEquals(2, i.docCount());
    i.flush();
    i.delete(0);
    assertEquals(1, i.docCount());
    i.flush();
    assertEquals(1, i.docCount());
    i.addDocument(getDoc());
    i.addDocument(getDoc());
    i.flush();
    assertEquals(3, i.docCount());
    i.delete(allDocTerm);
    assertEquals(0, i.docCount());
    i.optimize();
    assertEquals(0, i.docCount());
    
    //  Lucene defaults:
    assertNull(i.getInfoStream());
    assertTrue(i.getUseCompoundFile());
    assertEquals(10, i.getMaxBufferedDocs());
    assertEquals(10000, i.getMaxFieldLength());
    assertEquals(10, i.getMergeFactor());
    // test setting properties:
    i.setMaxBufferedDocs(100);
    i.setMergeFactor(25);
    i.setMaxFieldLength(250000);
    i.addDocument(getDoc());
    i.setUseCompoundFile(false);
    i.flush();
    assertEquals(100, i.getMaxBufferedDocs());
    assertEquals(25, i.getMergeFactor());
    assertEquals(250000, i.getMaxFieldLength());
    assertFalse(i.getUseCompoundFile());

    // test setting properties when internally the reader is opened:
    i.delete(allDocTerm);
    i.setMaxBufferedDocs(100);
    i.setMergeFactor(25);
    i.setMaxFieldLength(250000);
    i.addDocument(getDoc());
    i.setUseCompoundFile(false);
    i.optimize();
    assertEquals(100, i.getMaxBufferedDocs());
    assertEquals(25, i.getMergeFactor());
    assertEquals(250000, i.getMaxFieldLength());
    assertFalse(i.getUseCompoundFile());

    i.close();
    try {
      i.docCount();
      fail();
    } catch (IllegalStateException e) {
      // expected exception
    }
  }

  public void testExtendedIndex() throws IOException {
    Directory ramDir = new RAMDirectory();
    PowerIndex powerIndex = new PowerIndex(ramDir, new StandardAnalyzer(), true);
    powerIndex.addDocument(getDoc());
    powerIndex.addDocument(getDoc());
    powerIndex.addDocument(getDoc());
    powerIndex.addDocument(getDoc());
    powerIndex.addDocument(getDoc());
    powerIndex.flush();
    assertEquals(5, powerIndex.docFreq(allDocTerm));
    powerIndex.close();
  }
  
  private Document getDoc() {
    Document doc = new Document();
    doc.add(new Field("body", new Integer(docCount).toString(), Field.Store.YES, Field.Index.UN_TOKENIZED));
    doc.add(new Field("all", "x", Field.Store.YES, Field.Index.UN_TOKENIZED));
    docCount++;
    return doc;
  }
  
  public void testIndexWithThreads() throws IOException {
    testIndexInternal(0);
    testIndexInternal(10);
    testIndexInternal(50);
  }
  
  private void testIndexInternal(int maxWait) throws IOException {
    boolean create = true;
    //Directory rd = new RAMDirectory();
    // work on disk to make sure potential lock problems are tested:
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir == null)
      throw new IOException("java.io.tmpdir undefined, cannot run test");
    File indexDir = new File(tempDir, "lucenetestindex");
    Directory rd = FSDirectory.getDirectory(indexDir, create);
    IndexModifier index = new IndexModifier(rd, new StandardAnalyzer(), create);
    IndexThread thread1 = new IndexThread(index, maxWait);
    thread1.start();
    IndexThread thread2 = new IndexThread(index, maxWait);
    thread2.start();
    while(thread1.isAlive() || thread2.isAlive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    index.optimize();
    int added = thread1.added + thread2.added;
    int deleted = thread1.deleted + thread2.deleted;
    assertEquals(added-deleted, index.docCount());
    index.close();
    
    try {
      index.close();
      fail();
    } catch(IllegalStateException e) {
      // expected exception
    }
    rmDir(indexDir);
  }
  
  private void rmDir(File dir) {
    File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      files[i].delete();
    }
    dir.delete();
  }

  private int id = 0;
  private Stack idStack = new Stack();
  // TODO: test case is not reproducible despite pseudo-random numbers
  // used for anything:
  private Random random = new Random(101);		// constant seed for reproducability
  
  private class PowerIndex extends IndexModifier {
    public PowerIndex(Directory dir, Analyzer analyzer, boolean create) throws IOException {
      super(dir, analyzer, create);
    }
    public int docFreq(Term term) throws IOException {
      synchronized(directory) {
        assureOpen();
        createIndexReader();
        return indexReader.docFreq(term);
      }
    }
  }

  private class IndexThread extends Thread {
    
    private int maxWait = 10;
    private IndexModifier index;
    private int added = 0;
    private int deleted = 0;
    
    IndexThread(IndexModifier index, int maxWait) {
      this.index = index;
      this.maxWait = maxWait;
      id = 0;
      idStack.clear();
    }
    
    public void run() {
      try {
        for(int i = 0; i < ITERATIONS; i++) {
          int rand = random.nextInt(101);
          if (rand < 5) {
            index.optimize();
          } else if (rand < 60) {
            Document doc = getDocument();
            //System.out.println("add doc id=" + doc.get("id"));
            index.addDocument(doc);
            idStack.push(doc.get("id"));
            added++;
          } else {
            if (idStack.size() == 0) {
              // not enough docs in index, let's wait for next chance
            } else {
              // we just delete the last document added and remove it
              // from the id stack so that it won't be removed twice:
              String delId = (String)idStack.pop();
              //System.out.println("delete doc id = " + delId);
              index.delete(new Term("id", new Integer(delId).toString()));
              deleted++;
            }
          }
          if (maxWait > 0) {
            try {
              rand = random.nextInt(maxWait);
              //System.out.println("waiting " + rand + "ms");
              Thread.sleep(rand);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private Document getDocument() {
      Document doc = new Document();
      doc.add(new Field("id", new Integer(id++).toString(), Field.Store.YES,
          Field.Index.UN_TOKENIZED));
      // add random stuff:
      doc.add(new Field("content", new Integer(random.nextInt(1000)).toString(), Field.Store.YES, 
          Field.Index.TOKENIZED));
      doc.add(new Field("content", new Integer(random.nextInt(1000)).toString(), Field.Store.YES, 
          Field.Index.TOKENIZED));
      doc.add(new Field("all", "x", Field.Store.YES, Field.Index.TOKENIZED));
      return doc;
    }
  }
  
}
