package org.apache.lucene.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * Subscribes to
 * {@link Thread#setDefaultUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)}
 * and causes test/ suite failures if uncaught exceptions are detected.
 */
public class UncaughtExceptionsRule implements TestRule {
  // This was originally volatile, but I don't think it needs to be. It's the same
  // thread accessing it, always.
  private UncaughtExceptionHandler savedUncaughtExceptionHandler;

  public static class UncaughtExceptionEntry {
    public final Thread thread;
    public final Throwable exception;

    public UncaughtExceptionEntry(Thread thread, Throwable exception) {
      this.thread = thread;
      this.exception = exception;
    }
  }

  @SuppressWarnings("serial")
  private static class UncaughtExceptionsInBackgroundThread extends RuntimeException {
    public UncaughtExceptionsInBackgroundThread(UncaughtExceptionEntry e) {
      super("Uncaught exception by thread: " + e.thread, e.exception);
    }
  }

  // Lock on uncaughtExceptions to access.
  private final List<UncaughtExceptionEntry> uncaughtExceptions = new ArrayList<UncaughtExceptionEntry>();

  public Statement apply(final Statement s, final Description d) {
    return new Statement() {
      public void evaluate() throws Throwable {
        final ArrayList<Throwable> errors = new ArrayList<Throwable>();
        try {
          setupHandler();
          s.evaluate();
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          restoreHandler();
        }

        synchronized (uncaughtExceptions) {
          for (UncaughtExceptionEntry e : uncaughtExceptions) {
            errors.add(new UncaughtExceptionsInBackgroundThread(e));
          }
          uncaughtExceptions.clear();
        }

        MultipleFailureException.assertEmpty(errors);
      }
    };
  }
 
  /**
   * Just a check if anything's been caught.
   */
  public boolean hasUncaughtExceptions() {
    synchronized (uncaughtExceptions) {
      return !uncaughtExceptions.isEmpty();
    }
  }
  
  private void restoreHandler() {
    Thread.setDefaultUncaughtExceptionHandler(savedUncaughtExceptionHandler);    
  }

  private void setupHandler() {
    savedUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        // org.junit.internal.AssumptionViolatedException in older releases
        // org.junit.Assume.AssumptionViolatedException in recent ones
        if (e.getClass().getName().endsWith("AssumptionViolatedException")) {
          String where = "<unknown>";
          for (StackTraceElement elem : e.getStackTrace()) {
            if (!elem.getClassName().startsWith("org.junit")) {
              where = elem.toString();
              break;
            }
          }
          System.err.print("NOTE: Uncaught exception handler caught a failed assumption at " 
              + where + " (ignored):");
        } else {
          synchronized (uncaughtExceptions) {
            uncaughtExceptions.add(new UncaughtExceptionEntry(t, e));
          }

          StringWriter sw = new StringWriter();
          sw.write("\n===>\nUncaught exception by thread: " + t + "\n");
          PrintWriter pw = new PrintWriter(sw);
          e.printStackTrace(pw);
          pw.flush();
          sw.write("<===\n");
          System.err.println(sw.toString());
        }
      }
    });
  }  
}
