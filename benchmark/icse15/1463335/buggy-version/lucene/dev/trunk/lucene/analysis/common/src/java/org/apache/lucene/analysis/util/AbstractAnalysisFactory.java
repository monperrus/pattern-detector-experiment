package org.apache.lucene.analysis.util;

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

import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Abstract parent class for analysis factories {@link TokenizerFactory},
 * {@link TokenFilterFactory} and {@link CharFilterFactory}.
 * <p>
 * The typical lifecycle for a factory consumer is:
 * <ol>
 *   <li>Create factory via its constructor (or via XXXFactory.forName)
 *   <li>(Optional) If the factory uses resources such as files, {@link ResourceLoaderAware#inform(ResourceLoader)} is called to initialize those resources.
 *   <li>Consumer calls create() to obtain instances.
 * </ol>
 */
public abstract class AbstractAnalysisFactory {

  /** The original args, before any processing */
  private final Map<String,String> originalArgs;

  /** the luceneVersion arg */
  protected final Version luceneMatchVersion;

  /**
   * Initialize this factory via a set of key-value pairs.
   */
  protected AbstractAnalysisFactory(Map<String,String> args) {
    originalArgs = Collections.unmodifiableMap(new HashMap<String,String>(args));
    String version = args.remove("luceneMatchVersion");
    luceneMatchVersion = version == null ? null : Version.parseLeniently(version);
  }
  
  public final Map<String,String> getOriginalArgs() {
    return originalArgs;
  }

   /** this method can be called in the {@link org.apache.lucene.analysis.util.TokenizerFactory#create(java.io.Reader)}
   * or {@link org.apache.lucene.analysis.util.TokenFilterFactory#create(org.apache.lucene.analysis.TokenStream)} methods,
   * to inform user, that for this factory a {@link #luceneMatchVersion} is required */
  protected final void assureMatchVersion() {
    if (luceneMatchVersion == null) {
      throw new IllegalArgumentException("Configuration Error: Factory '" + this.getClass().getName() +
        "' needs a 'luceneMatchVersion' parameter");
    }
  }

  public final Version getLuceneMatchVersion() {
    return this.luceneMatchVersion;
  }

  protected final int getInt(Map<String,String> args, String name) {
    return getInt(args, name, -1, false);
  }

  protected final int getInt(Map<String,String> args, String name, int defaultVal) {
    return getInt(args, name, defaultVal, true);
  }

  protected final int getInt(Map<String,String> args, String name, int defaultVal, boolean useDefault) {
    String s = args.remove(name);
    if (s == null) {
      if (useDefault) {
        return defaultVal;
      }
      throw new IllegalArgumentException("Configuration Error: missing parameter '" + name + "'");
    }
    return Integer.parseInt(s);
  }

  protected final boolean getBoolean(Map<String,String> args, String name, boolean defaultVal) {
    return getBoolean(args, name, defaultVal, true);
  }

  protected final boolean getBoolean(Map<String,String> args, String name, boolean defaultVal, boolean useDefault) {
    String s = args.remove(name);
    if (s==null) {
      if (useDefault) return defaultVal;
      throw new IllegalArgumentException("Configuration Error: missing parameter '" + name + "'");
    }
    return Boolean.parseBoolean(s);
  }

  /**
   * Compiles a pattern for the value of the specified argument key <code>name</code> 
   */
  protected final Pattern getPattern(Map<String,String> args, String name) {
    try {
      String pat = args.remove(name);
      if (null == pat) {
        throw new IllegalArgumentException("Configuration Error: missing parameter '" + name + "'");
      }
      return Pattern.compile(pat);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException
        ("Configuration Error: '" + name + "' can not be parsed in " +
         this.getClass().getSimpleName(), e);
    }
  }

  /**
   * Returns as {@link CharArraySet} from wordFiles, which
   * can be a comma-separated list of filenames
   */
  protected final CharArraySet getWordSet(ResourceLoader loader,
      String wordFiles, boolean ignoreCase) throws IOException {
    assureMatchVersion();
    List<String> files = splitFileNames(wordFiles);
    CharArraySet words = null;
    if (files.size() > 0) {
      // default stopwords list has 35 or so words, but maybe don't make it that
      // big to start
      words = new CharArraySet(luceneMatchVersion,
          files.size() * 10, ignoreCase);
      for (String file : files) {
        List<String> wlist = getLines(loader, file.trim());
        words.addAll(StopFilter.makeStopSet(luceneMatchVersion, wlist,
            ignoreCase));
      }
    }
    return words;
  }
  
  /**
   * Returns the resource's lines (with content treated as UTF-8)
   */
  protected final List<String> getLines(ResourceLoader loader, String resource) throws IOException {
    return WordlistLoader.getLines(loader.openResource(resource), IOUtils.CHARSET_UTF_8);
  }

  /** same as {@link #getWordSet(ResourceLoader, String, boolean)},
   * except the input is in snowball format. */
  protected final CharArraySet getSnowballWordSet(ResourceLoader loader,
      String wordFiles, boolean ignoreCase) throws IOException {
    assureMatchVersion();
    List<String> files = splitFileNames(wordFiles);
    CharArraySet words = null;
    if (files.size() > 0) {
      // default stopwords list has 35 or so words, but maybe don't make it that
      // big to start
      words = new CharArraySet(luceneMatchVersion,
          files.size() * 10, ignoreCase);
      for (String file : files) {
        InputStream stream = null;
        Reader reader = null;
        try {
          stream = loader.openResource(file.trim());
          CharsetDecoder decoder = IOUtils.CHARSET_UTF_8.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
          reader = new InputStreamReader(stream, decoder);
          WordlistLoader.getSnowballWordSet(reader, words);
        } finally {
          IOUtils.closeWhileHandlingException(reader, stream);
        }
      }
    }
    return words;
  }

  /**
   * Splits file names separated by comma character.
   * File names can contain comma characters escaped by backslash '\'
   *
   * @param fileNames the string containing file names
   * @return a list of file names with the escaping backslashed removed
   */
  protected final List<String> splitFileNames(String fileNames) {
    if (fileNames == null)
      return Collections.<String>emptyList();

    List<String> result = new ArrayList<String>();
    for (String file : fileNames.split("(?<!\\\\),")) {
      result.add(file.replaceAll("\\\\(?=,)", ""));
    }

    return result;
  }
}
