package org.apache.lucene.search;

/**
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

import java.io.IOException;

import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.util.Counter;
import org.apache.lucene.util.ThreadInterruptedException;

/**
 * The {@link TimeLimitingCollector} is used to timeout search requests that
 * take longer than the maximum allowed search time limit. After this time is
 * exceeded, the search thread is stopped by throwing a
 * {@link TimeExceededException}.
 */
public class TimeLimitingCollector extends Collector {


  /** Thrown when elapsed search time exceeds allowed search time. */
  @SuppressWarnings("serial")
  public static class TimeExceededException extends RuntimeException {
    private long timeAllowed;
    private long timeElapsed;
    private int lastDocCollected;
    private TimeExceededException(long timeAllowed, long timeElapsed, int lastDocCollected) {
      super("Elapsed time: " + timeElapsed + "Exceeded allowed search time: " + timeAllowed + " ms.");
      this.timeAllowed = timeAllowed;
      this.timeElapsed = timeElapsed;
      this.lastDocCollected = lastDocCollected;
    }
    /** Returns allowed time (milliseconds). */
    public long getTimeAllowed() {
      return timeAllowed;
    }
    /** Returns elapsed time (milliseconds). */
    public long getTimeElapsed() {
      return timeElapsed;
    }
    /** Returns last doc (absolute doc id) that was collected when the search time exceeded. */
    public int getLastDocCollected() {
      return lastDocCollected;
    }
  }

  private long t0 = Long.MIN_VALUE;
  private long timeout = Long.MIN_VALUE;
  private final Collector collector;
  private final Counter clock;
  private final long ticksAllowed;
  private boolean greedy = false;
  private int docBase;

  /**
   * Create a TimeLimitedCollector wrapper over another {@link Collector} with a specified timeout.
   * @param collector the wrapped {@link Collector}
   * @param timeAllowed max time allowed for collecting hits after which {@link TimeExceededException} is thrown
   */
  public TimeLimitingCollector(final Collector collector, Counter clock, final long ticksAllowed ) {
    this.collector = collector;
    this.clock = clock;
    this.ticksAllowed = ticksAllowed;
  }
  
  /**
   * Sets the baseline for this collector. By default the collectors baseline is 
   * initialized once the first reader is passed to
   * {@link #setNextReader(AtomicReaderContext)}. To include operations executed
   * in prior to the actual document collection set the baseline through this method
   * in your prelude.
   * <p>
   * Example usage:
   * <pre>
   *   Counter clock = ...;
   *   long baseline = clock.get();
   *   // ... prepare search
   *   TimeLimitingCollector collector = new TimeLimitingCollector(c, clock, numTicks);
   *   collector.setBaseline(baseline);
   *   indexSearcher.search(query, collector);
   * </pre>
   * </p>
   * @see #setBaseline() 
   * @param clockTime
   */
  public void setBaseline(long clockTime) {
    t0 = clockTime;
    timeout = t0 + ticksAllowed;
  }
  
  /**
   * Syntactic sugar for {@link #setBaseline(long)} using {@link Counter#get()}
   * on the clock passed to the construcutor.
   */
  public void setBaseline() {
    setBaseline(clock.get());
  }
  
  /**
   * Checks if this time limited collector is greedy in collecting the last hit.
   * A non greedy collector, upon a timeout, would throw a {@link TimeExceededException} 
   * without allowing the wrapped collector to collect current doc. A greedy one would 
   * first allow the wrapped hit collector to collect current doc and only then 
   * throw a {@link TimeExceededException}.
   * @see #setGreedy(boolean)
   */
  public boolean isGreedy() {
    return greedy;
  }

  /**
   * Sets whether this time limited collector is greedy.
   * @param greedy true to make this time limited greedy
   * @see #isGreedy()
   */
  public void setGreedy(boolean greedy) {
    this.greedy = greedy;
  }
  
  /**
   * Calls {@link Collector#collect(int)} on the decorated {@link Collector}
   * unless the allowed time has passed, in which case it throws an exception.
   * 
   * @throws TimeExceededException
   *           if the time allowed has exceeded.
   */
  @Override
  public void collect(final int doc) throws IOException {
    final long time = clock.get();
    if (timeout < time) {
      if (greedy) {
        //System.out.println(this+"  greedy: before failing, collecting doc: "+(docBase + doc)+"  "+(time-t0));
        collector.collect(doc);
      }
      //System.out.println(this+"  failing on:  "+(docBase + doc)+"  "+(time-t0));
      throw new TimeExceededException( timeout-t0, time-t0, docBase + doc );
    }
    //System.out.println(this+"  collecting: "+(docBase + doc)+"  "+(time-t0));
    collector.collect(doc);
  }
  
  @Override
  public void setNextReader(AtomicReaderContext context) throws IOException {
    collector.setNextReader(context);
    this.docBase = context.docBase;
    if (Long.MIN_VALUE == t0) {
      setBaseline();
    }
  }
  
  @Override
  public void setScorer(Scorer scorer) throws IOException {
    collector.setScorer(scorer);
  }

  @Override
  public boolean acceptsDocsOutOfOrder() {
    return collector.acceptsDocsOutOfOrder();
  }


  /**
   * Returns the global TimerThreads {@link Counter}
   * <p>
   * Invoking this creates may create a new instance of {@link TimerThread} iff
   * the global {@link TimerThread} has never been accessed before. The thread
   * returned from this method is started on creation and will be alive unless
   * you stop the {@link TimerThread} via {@link TimerThread#stopTimer()}.
   * </p>
   * @return the global TimerThreads {@link Counter}
   * @lucene.experimental
   */
  public static Counter getGlobalCounter() {
    return TimerThreadHolder.THREAD.counter;
  }
  
  /**
   * Returns the global {@link TimerThread}.
   * <p>
   * Invoking this creates may create a new instance of {@link TimerThread} iff
   * the global {@link TimerThread} has never been accessed before. The thread
   * returned from this method is started on creation and will be alive unless
   * you stop the {@link TimerThread} via {@link TimerThread#stopTimer()}.
   * </p>
   * 
   * @return the global {@link TimerThread}
   * @lucene.experimental
   */
  public static TimerThread getGlobalTimerThread() {
    return TimerThreadHolder.THREAD;
  }
  
  private static final class TimerThreadHolder {
    static final TimerThread THREAD;
    static {
      THREAD = new TimerThread(Counter.newCounter(true));
      THREAD.start();
    }
  }

  /**
   * @lucene.experimental
   */
  public static final class TimerThread extends Thread  {
    
    public static final String THREAD_NAME = "TimeLimitedCollector timer thread";
    public static final int DEFAULT_RESOLUTION = 20;
    // NOTE: we can avoid explicit synchronization here for several reasons:
    // * updates to volatile long variables are atomic
    // * only single thread modifies this value
    // * use of volatile keyword ensures that it does not reside in
    //   a register, but in main memory (so that changes are visible to
    //   other threads).
    // * visibility of changes does not need to be instantaneous, we can
    //   afford losing a tick or two.
    //
    // See section 17 of the Java Language Specification for details.
    private volatile long time = 0;
    private volatile boolean stop = false;
    private volatile long resolution;
    final Counter counter;
    
    public TimerThread(long resolution, Counter counter) {
      super(THREAD_NAME);
      this.resolution = resolution;
      this.counter = counter;
      this.setDaemon(true);
    }
    
    public TimerThread(Counter counter) {
      this(DEFAULT_RESOLUTION, counter);
    }

    @Override
    public void run() {
      while (!stop) {
        // TODO: Use System.nanoTime() when Lucene moves to Java SE 5.
        counter.addAndGet(resolution);
        try {
          Thread.sleep( resolution );
        } catch (InterruptedException ie) {
          throw new ThreadInterruptedException(ie);
        }
      }
    }

    /**
     * Get the timer value in milliseconds.
     */
    public long getMilliseconds() {
      return time;
    }
    
    /**
     * Stops the timer thread 
     */
    public void stopTimer() {
      stop = true;
    }
    
    /** 
     * Return the timer resolution.
     * @see #setResolution(long)
     */
    public long getResolution() {
      return resolution;
    }
    
    /**
     * Set the timer resolution.
     * The default timer resolution is 20 milliseconds. 
     * This means that a search required to take no longer than 
     * 800 milliseconds may be stopped after 780 to 820 milliseconds.
     * <br>Note that: 
     * <ul>
     * <li>Finer (smaller) resolution is more accurate but less efficient.</li>
     * <li>Setting resolution to less than 5 milliseconds will be silently modified to 5 milliseconds.</li>
     * <li>Setting resolution smaller than current resolution might take effect only after current 
     * resolution. (Assume current resolution of 20 milliseconds is modified to 5 milliseconds, 
     * then it can take up to 20 milliseconds for the change to have effect.</li>
     * </ul>      
     */
    public void setResolution(long resolution) {
      this.resolution = Math.max(resolution, 5); // 5 milliseconds is about the minimum reasonable time for a Object.wait(long) call.
    }
  }
  
}
