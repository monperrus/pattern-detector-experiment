  + native
  + Date Author Id Revision HeadURL
package org.apache.lucene.analysis;

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

import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.CloseableThreadLocal;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * An Analyzer builds TokenStreams, which analyze text.  It thus represents a
 * policy for extracting index terms from text.
 * <p>
 * In order to define what analysis is done, subclasses must define their
 * {@link TokenStreamComponents TokenStreamComponents} in {@link #createComponents(String, Reader)}.
 * The components are then reused in each call to {@link #tokenStream(String, Reader)}.
 * <p>
 * Simple example:
 * <pre class="prettyprint">
 * Analyzer analyzer = new Analyzer() {
 *  {@literal @Override}
 *   protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
 *     Tokenizer source = new FooTokenizer(reader);
 *     TokenStream filter = new FooFilter(source);
 *     filter = new BarFilter(filter);
 *     return new TokenStreamComponents(source, filter);
 *   }
 * };
 * </pre>
 * For more examples, see the {@link org.apache.lucene.analysis Analysis package documentation}.
 * <p>
 * For some concrete implementations bundled with Lucene, look in the analysis modules:
 * <ul>
 *   <li><a href="{@docRoot}/../analyzers-common/overview-summary.html">Common</a>:
 *       Analyzers for indexing content in different languages and domains.
 *   <li><a href="{@docRoot}/../analyzers-icu/overview-summary.html">ICU</a>:
 *       Exposes functionality from ICU to Apache Lucene. 
 *   <li><a href="{@docRoot}/../analyzers-kuromoji/overview-summary.html">Kuromoji</a>:
 *       Morphological analyzer for Japanese text.
 *   <li><a href="{@docRoot}/../analyzers-morfologik/overview-summary.html">Morfologik</a>:
 *       Dictionary-driven lemmatization for the Polish language.
 *   <li><a href="{@docRoot}/../analyzers-phonetic/overview-summary.html">Phonetic</a>:
 *       Analysis for indexing phonetic signatures (for sounds-alike search).
 *   <li><a href="{@docRoot}/../analyzers-smartcn/overview-summary.html">Smart Chinese</a>:
 *       Analyzer for Simplified Chinese, which indexes words.
 *   <li><a href="{@docRoot}/../analyzers-stempel/overview-summary.html">Stempel</a>:
 *       Algorithmic Stemmer for the Polish Language.
 *   <li><a href="{@docRoot}/../analyzers-uima/overview-summary.html">UIMA</a>: 
 *       Analysis integration with Apache UIMA. 
 * </ul>
 */
public abstract class Analyzer implements Closeable {

  private final ReuseStrategy reuseStrategy;

  /**
   * Create a new Analyzer, reusing the same set of components per-thread
   * across calls to {@link #tokenStream(String, Reader)}. 
   */
  public Analyzer() {
    this(new GlobalReuseStrategy());
  }

  /**
   * Expert: create a new Analyzer with a custom {@link ReuseStrategy}.
   * <p>
   * NOTE: if you just want to reuse on a per-field basis, its easier to
   * use a subclass of {@link AnalyzerWrapper} such as 
   * <a href="{@docRoot}/../analyzers-common/org/apache/lucene/analysis/miscellaneous/PerFieldAnalyzerWrapper.html">
   * PerFieldAnalyerWrapper</a> instead.
   */
  public Analyzer(ReuseStrategy reuseStrategy) {
    this.reuseStrategy = reuseStrategy;
  }

  /**
   * Creates a new {@link TokenStreamComponents} instance for this analyzer.
   * 
   * @param fieldName
   *          the name of the fields content passed to the
   *          {@link TokenStreamComponents} sink as a reader
   * @param reader
   *          the reader passed to the {@link Tokenizer} constructor
   * @return the {@link TokenStreamComponents} for this analyzer.
   */
  protected abstract TokenStreamComponents createComponents(String fieldName,
      Reader reader);

  /**
   * Returns a TokenStream suitable for <code>fieldName</code>, tokenizing
   * the contents of <code>reader</code>.
   * <p>
   * This method uses {@link #createComponents(String, Reader)} to obtain an
   * instance of {@link TokenStreamComponents}. It returns the sink of the
   * components and stores the components internally. Subsequent calls to this
   * method will reuse the previously stored components after resetting them
   * through {@link TokenStreamComponents#setReader(Reader)}.
   * <p>
   * <b>NOTE:</b> After calling this method, the consumer must follow the 
   * workflow described in {@link TokenStream} to properly consume its contents.
   * See the {@link org.apache.lucene.analysis Analysis package documentation} for
   * some examples demonstrating this.
   * 
   * @param fieldName the name of the field the created TokenStream is used for
   * @param reader the reader the streams source reads from
   * @return TokenStream for iterating the analyzed content of <code>reader</code>
   * @throws AlreadyClosedException if the Analyzer is closed.
   * @throws IOException if an i/o error occurs.
   */
  public final TokenStream tokenStream(final String fieldName,
                                       final Reader reader) throws IOException {
    TokenStreamComponents components = reuseStrategy.getReusableComponents(fieldName);
    final Reader r = initReader(fieldName, reader);
    if (components == null) {
      components = createComponents(fieldName, r);
      reuseStrategy.setReusableComponents(fieldName, components);
    } else {
      components.setReader(r);
    }
    return components.getTokenStream();
  }
  
  /**
   * Override this if you want to add a CharFilter chain.
   * <p>
   * The default implementation returns <code>reader</code>
   * unchanged.
   * 
   * @param fieldName IndexableField name being indexed
   * @param reader original Reader
   * @return reader, optionally decorated with CharFilter(s)
   */
  protected Reader initReader(String fieldName, Reader reader) {
    return reader;
  }

  /**
   * Invoked before indexing a IndexableField instance if
   * terms have already been added to that field.  This allows custom
   * analyzers to place an automatic position increment gap between
   * IndexbleField instances using the same field name.  The default value
   * position increment gap is 0.  With a 0 position increment gap and
   * the typical default token position increment of 1, all terms in a field,
   * including across IndexableField instances, are in successive positions, allowing
   * exact PhraseQuery matches, for instance, across IndexableField instance boundaries.
   *
   * @param fieldName IndexableField name being indexed.
   * @return position increment gap, added to the next token emitted from {@link #tokenStream(String,Reader)}.
   *         This value must be {@code >= 0}.
   */
  public int getPositionIncrementGap(String fieldName) {
    return 0;
  }

  /**
   * Just like {@link #getPositionIncrementGap}, except for
   * Token offsets instead.  By default this returns 1.
   * This method is only called if the field
   * produced at least one token for indexing.
   *
   * @param fieldName the field just indexed
   * @return offset gap, added to the next token emitted from {@link #tokenStream(String,Reader)}.
   *         This value must be {@code >= 0}.
   */
  public int getOffsetGap(String fieldName) {
    return 1;
  }

  /** Frees persistent resources used by this Analyzer */
  @Override
  public void close() {
    reuseStrategy.close();
  }

  /**
   * This class encapsulates the outer components of a token stream. It provides
   * access to the source ({@link Tokenizer}) and the outer end (sink), an
   * instance of {@link TokenFilter} which also serves as the
   * {@link TokenStream} returned by
   * {@link Analyzer#tokenStream(String, Reader)}.
   */
  public static class TokenStreamComponents {
    /**
     * Original source of the tokens.
     */
    protected final Tokenizer source;
    /**
     * Sink tokenstream, such as the outer tokenfilter decorating
     * the chain. This can be the source if there are no filters.
     */
    protected final TokenStream sink;

    /**
     * Creates a new {@link TokenStreamComponents} instance.
     * 
     * @param source
     *          the analyzer's tokenizer
     * @param result
     *          the analyzer's resulting token stream
     */
    public TokenStreamComponents(final Tokenizer source,
        final TokenStream result) {
      this.source = source;
      this.sink = result;
    }
    
    /**
     * Creates a new {@link TokenStreamComponents} instance.
     * 
     * @param source
     *          the analyzer's tokenizer
     */
    public TokenStreamComponents(final Tokenizer source) {
      this.source = source;
      this.sink = source;
    }

    /**
     * Resets the encapsulated components with the given reader. If the components
     * cannot be reset, an Exception should be thrown.
     * 
     * @param reader
     *          a reader to reset the source component
     * @throws IOException
     *           if the component's reset method throws an {@link IOException}
     */
    protected void setReader(final Reader reader) throws IOException {
      source.setReader(reader);
    }

    /**
     * Returns the sink {@link TokenStream}
     * 
     * @return the sink {@link TokenStream}
     */
    public TokenStream getTokenStream() {
      return sink;
    }

    /**
     * Returns the component's {@link Tokenizer}
     *
     * @return Component's {@link Tokenizer}
     */
    public Tokenizer getTokenizer() {
      return source;
    }
  }

  /**
   * Strategy defining how TokenStreamComponents are reused per call to
   * {@link Analyzer#tokenStream(String, java.io.Reader)}.
   */
  public static abstract class ReuseStrategy implements Closeable {

    private CloseableThreadLocal<Object> storedValue = new CloseableThreadLocal<Object>();

    /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
    public ReuseStrategy() {}

    /**
     * Gets the reusable TokenStreamComponents for the field with the given name
     *
     * @param fieldName Name of the field whose reusable TokenStreamComponents
     *        are to be retrieved
     * @return Reusable TokenStreamComponents for the field, or {@code null}
     *         if there was no previous components for the field
     */
    public abstract TokenStreamComponents getReusableComponents(String fieldName);

    /**
     * Stores the given TokenStreamComponents as the reusable components for the
     * field with the give name
     *
     * @param fieldName Name of the field whose TokenStreamComponents are being set
     * @param components TokenStreamComponents which are to be reused for the field
     */
    public abstract void setReusableComponents(String fieldName, TokenStreamComponents components);

    /**
     * Returns the currently stored value
     *
     * @return Currently stored value or {@code null} if no value is stored
     * @throws AlreadyClosedException if the ReuseStrategy is closed.
     */
    protected final Object getStoredValue() {
      try {
        return storedValue.get();
      } catch (NullPointerException npe) {
        if (storedValue == null) {
          throw new AlreadyClosedException("this Analyzer is closed");
        } else {
          throw npe;
        }
      }
    }

    /**
     * Sets the stored value
     *
     * @param storedValue Value to store
     * @throws AlreadyClosedException if the ReuseStrategy is closed.
     */
    protected final void setStoredValue(Object storedValue) {
      try {
        this.storedValue.set(storedValue);
      } catch (NullPointerException npe) {
        if (storedValue == null) {
          throw new AlreadyClosedException("this Analyzer is closed");
        } else {
          throw npe;
        }
      }
    }

    /**
     * Closes the ReuseStrategy, freeing any resources
     */
    @Override
    public void close() {
      if (storedValue != null) {
        storedValue.close();
        storedValue = null;
      }
    }
  }

  /**
   * Implementation of {@link ReuseStrategy} that reuses the same components for
   * every field.
   */
  public final static class GlobalReuseStrategy extends ReuseStrategy {
    
    /** Creates a new instance, with empty per-thread values */
    public GlobalReuseStrategy() {}

    @Override
    public TokenStreamComponents getReusableComponents(String fieldName) {
      return (TokenStreamComponents) getStoredValue();
    }

    @Override
    public void setReusableComponents(String fieldName, TokenStreamComponents components) {
      setStoredValue(components);
    }
  }

  /**
   * Implementation of {@link ReuseStrategy} that reuses components per-field by
   * maintaining a Map of TokenStreamComponent per field name.
   */
  public static class PerFieldReuseStrategy extends ReuseStrategy {

    /** Creates a new instance, with empty per-thread-per-field values */
    public PerFieldReuseStrategy() {}

    @SuppressWarnings("unchecked")
    @Override
    public TokenStreamComponents getReusableComponents(String fieldName) {
      Map<String, TokenStreamComponents> componentsPerField = (Map<String, TokenStreamComponents>) getStoredValue();
      return componentsPerField != null ? componentsPerField.get(fieldName) : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setReusableComponents(String fieldName, TokenStreamComponents components) {
      Map<String, TokenStreamComponents> componentsPerField = (Map<String, TokenStreamComponents>) getStoredValue();
      if (componentsPerField == null) {
        componentsPerField = new HashMap<String, TokenStreamComponents>();
        setStoredValue(componentsPerField);
      }
      componentsPerField.put(fieldName, components);
    }
  }

}
