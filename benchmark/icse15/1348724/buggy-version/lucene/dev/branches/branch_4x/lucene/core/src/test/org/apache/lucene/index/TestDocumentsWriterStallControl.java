package org.apache.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.DocumentsWriterStallControl.MemoryController;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeaks;

/**
 * Tests for {@link DocumentsWriterStallControl}
 */
@ThreadLeaks(failTestIfLeaking = true)
public class TestDocumentsWriterStallControl extends LuceneTestCase {
  
  public void testSimpleStall() throws InterruptedException {
    DocumentsWriterStallControl ctrl = new DocumentsWriterStallControl();
    SimpleMemCtrl memCtrl = new SimpleMemCtrl();
    memCtrl.limit = 1000;
    memCtrl.netBytes = 1000;
    memCtrl.flushBytes = 20;
    ctrl.updateStalled(memCtrl);
    Thread[] waitThreads = waitThreads(atLeast(1), ctrl);
    start(waitThreads);
    assertFalse(ctrl.hasBlocked());
    assertFalse(ctrl.anyStalledThreads());
    join(waitThreads, 10);
    
    // now stall threads and wake them up again
    memCtrl.netBytes = 1001;
    memCtrl.flushBytes = 100;
    ctrl.updateStalled(memCtrl);
    waitThreads = waitThreads(atLeast(1), ctrl);
    start(waitThreads);
    awaitState(100, Thread.State.WAITING, waitThreads);
    assertTrue(ctrl.hasBlocked());
    assertTrue(ctrl.anyStalledThreads());
    memCtrl.netBytes = 50;
    memCtrl.flushBytes = 0;
    ctrl.updateStalled(memCtrl);
    assertFalse(ctrl.anyStalledThreads());
    join(waitThreads, 500);
  }
  
  public void testRandom() throws InterruptedException {
    final DocumentsWriterStallControl ctrl = new DocumentsWriterStallControl();
    SimpleMemCtrl memCtrl = new SimpleMemCtrl();
    memCtrl.limit = 1000;
    memCtrl.netBytes = 1;
    ctrl.updateStalled(memCtrl);
    Thread[] stallThreads = new Thread[atLeast(3)];
    for (int i = 0; i < stallThreads.length; i++) {
      final int threadId = i;
      stallThreads[i] = new Thread() {
        public void run() {
          int baseBytes = threadId % 2 == 0 ? 500 : 700;
          SimpleMemCtrl memCtrl = new SimpleMemCtrl();
          memCtrl.limit = 1000;
          memCtrl.netBytes = 1;
          memCtrl.flushBytes = 0;

          int iters = atLeast(1000);
          for (int j = 0; j < iters; j++) {
            memCtrl.netBytes = baseBytes + random().nextInt(1000);
            memCtrl.flushBytes = random().nextInt((int)memCtrl.netBytes);
            ctrl.updateStalled(memCtrl);
            if (random().nextInt(5) == 0) { // thread 0 only updates
              ctrl.waitIfStalled();
            }
          }
        }
      };
    }
    start(stallThreads);
    long time = System.currentTimeMillis();
    /*
     * use a 100 sec timeout to make sure we not hang forever. join will fail in
     * that case
     */
    while ((System.currentTimeMillis() - time) < 100 * 1000
        && !terminated(stallThreads)) {
      ctrl.updateStalled(memCtrl);
      if (random().nextBoolean()) {
        Thread.yield();
      } else {
        Thread.sleep(1);
      }
      
    }
    join(stallThreads, 100);
    
  }
  
  public void testAccquireReleaseRace() throws InterruptedException {
    final DocumentsWriterStallControl ctrl = new DocumentsWriterStallControl();
    SimpleMemCtrl memCtrl = new SimpleMemCtrl();
    memCtrl.limit = 1000;
    memCtrl.netBytes = 1;
    memCtrl.flushBytes = 0;
    ctrl.updateStalled(memCtrl);
    final AtomicBoolean stop = new AtomicBoolean(false);
    final AtomicBoolean checkPoint = new AtomicBoolean(true);
    
    int numStallers = atLeast(1);
    int numReleasers = atLeast(1);
    int numWaiters = atLeast(1);
    final Synchonizer sync = new Synchonizer(numStallers + numReleasers, numStallers + numReleasers+numWaiters);
    Thread[] threads = new Thread[numReleasers + numStallers + numWaiters];
    List<Throwable> exceptions =  Collections.synchronizedList(new ArrayList<Throwable>());
    for (int i = 0; i < numReleasers; i++) {
      threads[i] = new Updater(stop, checkPoint, ctrl, sync, true, exceptions);
    }
    for (int i = numReleasers; i < numReleasers + numStallers; i++) {
      threads[i] = new Updater(stop, checkPoint, ctrl, sync, false, exceptions);
      
    }
    for (int i = numReleasers + numStallers; i < numReleasers + numStallers
        + numWaiters; i++) {
      threads[i] = new Waiter(stop, checkPoint, ctrl, sync, exceptions);
      
    }
    
    start(threads);
    int iters = atLeast(20000);
    for (int i = 0; i < iters; i++) {
      if (checkPoint.get()) {
       
        assertTrue("timed out waiting for update threads - deadlock?", sync.updateJoin.await(10, TimeUnit.SECONDS));
        if (!exceptions.isEmpty()) {
          for (Throwable throwable : exceptions) {
            throwable.printStackTrace();
          }
          fail("got exceptions in threads");
        }
        
        if (ctrl.hasBlocked() && ctrl.isHealthy()) {
          assertState(numReleasers, numStallers, numWaiters, threads, ctrl);
          
           
          }
        
        checkPoint.set(false);
        sync.waiter.countDown();
        sync.leftCheckpoint.await();
      }
      assertFalse(checkPoint.get());
      assertEquals(0, sync.waiter.getCount());
      if (random().nextInt(2) == 0) {
        sync.reset(numStallers + numReleasers, numStallers + numReleasers
            + numWaiters);
        checkPoint.set(true);
      }
  
    }
    if (!checkPoint.get()) {
      sync.reset(numStallers + numReleasers, numStallers + numReleasers
          + numWaiters);
      checkPoint.set(true);
    }
    
    assertTrue(sync.updateJoin.await(10, TimeUnit.SECONDS));
    assertState(numReleasers, numStallers, numWaiters, threads, ctrl);
    checkPoint.set(false);
    stop.set(true);
    sync.waiter.countDown();
    sync.leftCheckpoint.await();
    
    
    for (int i = 0; i < threads.length; i++) {
      memCtrl.limit = 1000;
      memCtrl.netBytes = 1;
      memCtrl.flushBytes = 0;
      ctrl.updateStalled(memCtrl);
      threads[i].join(2000);
      if (threads[i].isAlive() && threads[i] instanceof Waiter) {
        if (threads[i].getState() == Thread.State.WAITING) {
          fail("waiter is not released - anyThreadsStalled: "
              + ctrl.anyStalledThreads());
        }
      }
    }
  }
  
  private void assertState(int numReleasers, int numStallers, int numWaiters, Thread[] threads, DocumentsWriterStallControl ctrl) throws InterruptedException {
    int millisToSleep = 100;
    while (true) {
      if (ctrl.hasBlocked() && ctrl.isHealthy()) {
        for (int n = numReleasers + numStallers; n < numReleasers
            + numStallers + numWaiters; n++) {
          if (ctrl.isThreadQueued(threads[n])) {
            if (millisToSleep < 60000) {
              Thread.sleep(millisToSleep);
              millisToSleep *=2;
              break;
            } else {
              fail("control claims no stalled threads but waiter seems to be blocked ");
            }
          }
        }
        break;
      } else {
        break;
      }
    }
    
  }

  public static class Waiter extends Thread {
    private Synchonizer sync;
    private DocumentsWriterStallControl ctrl;
    private AtomicBoolean checkPoint;
    private AtomicBoolean stop;
    private List<Throwable> exceptions;
    
    public Waiter(AtomicBoolean stop, AtomicBoolean checkPoint,
        DocumentsWriterStallControl ctrl, Synchonizer sync,
        List<Throwable> exceptions) {
      super("waiter");
      this.stop = stop;
      this.checkPoint = checkPoint;
      this.ctrl = ctrl;
      this.sync = sync;
      this.exceptions = exceptions;
    }
    
    public void run() {
      try {
        while (!stop.get()) {
          ctrl.waitIfStalled();
          if (checkPoint.get()) {
            try {
              assertTrue(sync.await());
            } catch (InterruptedException e) {
              System.out.println("[Waiter] got interrupted - wait count: " + sync.waiter.getCount());
              throw new ThreadInterruptedException(e);
            }
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
        exceptions.add(e);
      }
    }
  }
  
  public static class Updater extends Thread {
    
    private Synchonizer sync;
    private DocumentsWriterStallControl ctrl;
    private AtomicBoolean checkPoint;
    private AtomicBoolean stop;
    private boolean release;
    private List<Throwable> exceptions;
    
    public Updater(AtomicBoolean stop, AtomicBoolean checkPoint,
        DocumentsWriterStallControl ctrl, Synchonizer sync,
        boolean release, List<Throwable> exceptions) {
      super("updater");
      this.stop = stop;
      this.checkPoint = checkPoint;
      this.ctrl = ctrl;
      this.sync = sync;
      this.release = release;
      this.exceptions = exceptions;
    }
    
    public void run() {
      try {
        SimpleMemCtrl memCtrl = new SimpleMemCtrl();
        memCtrl.limit = 1000;
        memCtrl.netBytes = release ? 1 : 2000;
        memCtrl.flushBytes = random().nextInt((int)memCtrl.netBytes);
        while (!stop.get()) {
          int internalIters = release && random().nextBoolean() ? atLeast(5) : 1;
          for (int i = 0; i < internalIters; i++) {
            ctrl.updateStalled(memCtrl);
          }
          if (checkPoint.get()) {
            sync.updateJoin.countDown();
            try {
              assertTrue(sync.await());
            } catch (InterruptedException e) {
              System.out.println("[Updater] got interrupted - wait count: " + sync.waiter.getCount());
              throw new ThreadInterruptedException(e);
            }
            sync.leftCheckpoint.countDown();
          }
          if (random().nextBoolean()) {
            Thread.yield();
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
        exceptions.add(e);
      }
      sync.updateJoin.countDown();
    }
    
  }
  
  public static boolean terminated(Thread[] threads) {
    for (Thread thread : threads) {
      if (Thread.State.TERMINATED != thread.getState()) return false;
    }
    return true;
  }
  
  public static void start(Thread[] tostart) throws InterruptedException {
    for (Thread thread : tostart) {
      thread.start();
    }
    Thread.sleep(1); // let them start
  }
  
  public static void join(Thread[] toJoin, long timeout)
      throws InterruptedException {
    for (Thread thread : toJoin) {
      thread.join(timeout);
    }
  }
  
  public static Thread[] waitThreads(int num,
      final DocumentsWriterStallControl ctrl) {
    Thread[] array = new Thread[num];
    for (int i = 0; i < array.length; i++) {
      array[i] = new Thread() {
        public void run() {
          ctrl.waitIfStalled();
        }
      };
    }
    return array;
  }
  
  public static void awaitState(long timeout, Thread.State state,
      Thread... threads) throws InterruptedException {
    long t = System.currentTimeMillis();
    while (System.currentTimeMillis() - t <= timeout) {
      boolean done = true;
      for (Thread thread : threads) {
        if (thread.getState() != state) {
          done = false;
        }
      }
      if (done) {
        return;
      }
      if (random().nextBoolean()) {
        Thread.yield();
      } else {
        Thread.sleep(1);
      }
    }
    fail("timed out waiting for state: " + state + " timeout: " + timeout
        + " ms");
  }
  
  private static class SimpleMemCtrl implements MemoryController {
    long netBytes;
    long limit;
    long flushBytes;
    
    @Override
    public long netBytes() {
      return netBytes;
    }
    
    @Override
    public long stallLimitBytes() {
      return limit;
    }

    @Override
    public long flushBytes() {
      return flushBytes;
    }
    
  }
  
  private static final class Synchonizer {
    volatile CountDownLatch waiter;
    volatile CountDownLatch updateJoin;
    volatile CountDownLatch leftCheckpoint;
    
    public Synchonizer(int numUpdater, int numThreads) {
      reset(numUpdater, numThreads);
    }
    
    public void reset(int numUpdaters, int numThreads) {
      this.waiter = new CountDownLatch(1);
      this.updateJoin = new CountDownLatch(numUpdaters);
      this.leftCheckpoint = new CountDownLatch(numUpdaters);
    }
    
    public boolean await() throws InterruptedException {
      return waiter.await(10, TimeUnit.SECONDS);
    }
    
  }
}
