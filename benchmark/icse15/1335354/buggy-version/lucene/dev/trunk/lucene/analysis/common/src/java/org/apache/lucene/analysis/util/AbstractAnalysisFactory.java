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
import java.util.List;
import java.util.Map;

abstract class AbstractAnalysisFactory {

  /** The init args */
  protected Map<String,String> args;

  /** the luceneVersion arg */
  protected Version luceneMatchVersion = null;

  public void init(Map<String,String> args) {
    this.args = args;
  }

  public Map<String,String> getArgs() {
    return args;
  }

   /** this method can be called in the {@link org.apache.lucene.analysis.util.TokenizerFactory#create(java.io.Reader)}
   * or {@link org.apache.lucene.analysis.util.TokenFilterFactory#create(org.apache.lucene.analysis.TokenStream)} methods,
   * to inform user, that for this factory a {@link #luceneMatchVersion} is required */
  protected final void assureMatchVersion() {
    if (luceneMatchVersion == null) {
      throw new InitializationException("Configuration Error: Factory '" + this.getClass().getName() +
        "' needs a 'luceneMatchVersion' parameter");
    }
  }

  public void setLuceneMatchVersion(Version luceneMatchVersion) {
    this.luceneMatchVersion = luceneMatchVersion;
  }

  public Version getLuceneMatchVersion() {
    return this.luceneMatchVersion;
  }

  protected int getInt(String name) {
    return getInt(name, -1, false);
  }

  protected int getInt(String name, int defaultVal) {
    return getInt(name, defaultVal, true);
  }

  protected int getInt(String name, int defaultVal, boolean useDefault) {
    String s = args.get(name);
    if (s == null) {
      if (useDefault) {
        return defaultVal;
      }
      throw new InitializationException("Configuration Error: missing parameter '" + name + "'");
    }
    return Integer.parseInt(s);
  }

  protected boolean getBoolean(String name, boolean defaultVal) {
    return getBoolean(name, defaultVal, true);
  }

  protected boolean getBoolean(String name, boolean defaultVal, boolean useDefault) {
    String s = args.get(name);
    if (s==null) {
      if (useDefault) return defaultVal;
      throw new InitializationException("Configuration Error: missing parameter '" + name + "'");
    }
    return Boolean.parseBoolean(s);
  }

  protected CharArraySet getWordSet(ResourceLoader loader,
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
        List<String> wlist = loader.getLines(file.trim());
        words.addAll(StopFilter.makeStopSet(luceneMatchVersion, wlist,
            ignoreCase));
      }
    }
    return words;
  }

  /** same as {@link #getWordSet(ResourceLoader, String, boolean)},
   * except the input is in snowball format. */
  protected CharArraySet getSnowballWordSet(ResourceLoader loader,
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
  protected List<String> splitFileNames(String fileNames) {
    if (fileNames == null)
      return Collections.<String>emptyList();

    List<String> result = new ArrayList<String>();
    for (String file : fileNames.split("(?<!\\\\),")) {
      result.add(file.replaceAll("\\\\(?=,)", ""));
    }

    return result;
  }
}
