package org.apache.lucene.analysis.hunspell2;

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

import org.apache.lucene.analysis.util.CharArrayMap;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.OfflineSorter;
import org.apache.lucene.util.OfflineSorter.ByteSequencesReader;
import org.apache.lucene.util.OfflineSorter.ByteSequencesWriter;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * In-memory structure for the dictionary (.dic) and affix (.aff)
 * data of a hunspell dictionary.
 */
public class Dictionary {

  static final char[] NOFLAGS = new char[0];
  
  private static final String ALIAS_KEY = "AF";
  private static final String PREFIX_KEY = "PFX";
  private static final String SUFFIX_KEY = "SFX";
  private static final String FLAG_KEY = "FLAG";

  private static final String NUM_FLAG_TYPE = "num";
  private static final String UTF8_FLAG_TYPE = "UTF-8";
  private static final String LONG_FLAG_TYPE = "long";
  
  private static final String PREFIX_CONDITION_REGEX_PATTERN = "%s.*";
  private static final String SUFFIX_CONDITION_REGEX_PATTERN = ".*%s";

  public CharArrayMap<List<Affix>> prefixes;
  public CharArrayMap<List<Affix>> suffixes;
  
  // all Patterns used by prefixes and suffixes. these are typically re-used across
  // many affix stripping rules. so these are deduplicated, to save RAM.
  // TODO: maybe don't use Pattern for the condition check...
  // TODO: when we cut over Affix to FST, just store integer index to this.
  public ArrayList<Pattern> patterns = new ArrayList<>();
  
  // the entries in the .dic file, mapping to their set of flags.
  // the fst output is the ordinal for flagLookup
  public FST<Long> words;
  // the list of unique flagsets (wordforms). theoretically huge, but practically
  // small (e.g. for polish this is 756), otherwise humans wouldn't be able to deal with it either.
  public BytesRefHash flagLookup = new BytesRefHash();
  
  // the list of unique strip affixes.
  public BytesRefHash stripLookup = new BytesRefHash();

  private FlagParsingStrategy flagParsingStrategy = new SimpleFlagParsingStrategy(); // Default flag parsing strategy

  private String[] aliases;
  private int aliasCount = 0;
  
  private final File tempDir = OfflineSorter.defaultTempDir(); // TODO: make this configurable?

  /**
   * Creates a new Dictionary containing the information read from the provided InputStreams to hunspell affix
   * and dictionary files.
   * You have to close the provided InputStreams yourself.
   *
   * @param affix InputStream for reading the hunspell affix file (won't be closed).
   * @param dictionary InputStream for reading the hunspell dictionary file (won't be closed).
   * @throws IOException Can be thrown while reading from the InputStreams
   * @throws ParseException Can be thrown if the content of the files does not meet expected formats
   */
  public Dictionary(InputStream affix, InputStream dictionary) throws IOException, ParseException {
    BufferedInputStream buffered = new BufferedInputStream(affix, 8192);
    buffered.mark(8192);
    String encoding = getDictionaryEncoding(affix);
    buffered.reset();
    CharsetDecoder decoder = getJavaEncoding(encoding);
    readAffixFile(buffered, decoder);
    flagLookup.add(new BytesRef()); // no flags -> ord 0
    stripLookup.add(new BytesRef()); // no strip -> ord 0
    PositiveIntOutputs o = PositiveIntOutputs.getSingleton();
    Builder<Long> b = new Builder<Long>(FST.INPUT_TYPE.BYTE4, o);
    readDictionaryFile(dictionary, decoder, b);
    words = b.finish();
  }

  /**
   * Looks up words that match the String created from the given char array, offset and length
   *
   * @param word Char array to generate the String from
   * @param offset Offset in the char array that the String starts at
   * @param length Length from the offset that the String is
   * @return List of HunspellWords that match the generated String, or {@code null} if none are found
   */
  char[] lookupWord(char word[], int offset, int length, BytesRef scratch) {
    Integer ord = null;
    try {
      ord = lookupOrd(word, offset, length);
    } catch (IOException ex) { /* bogus */ }
    if (ord == null) {
      return null;
    }
    return decodeFlags(flagLookup.get(ord, scratch));
  }
  
  public Integer lookupOrd(char word[], int offset, int length) throws IOException {
    final FST.BytesReader bytesReader = words.getBytesReader();
    final FST.Arc<Long> arc = words.getFirstArc(new FST.Arc<Long>());
    // Accumulate output as we go
    final Long NO_OUTPUT = words.outputs.getNoOutput();
    Long output = NO_OUTPUT;
    
    int l = offset + length;
    for (int i = offset, cp = 0; i < l; i += Character.charCount(cp)) {
      cp = Character.codePointAt(word, i, l);
      if (words.findTargetArc(cp, arc, arc, bytesReader) == null) {
        return null;
      } else if (arc.output != NO_OUTPUT) {
        output = words.outputs.add(output, arc.output);
      }
    }
    if (words.findTargetArc(FST.END_LABEL, arc, arc, bytesReader) == null) {
      return null;
    } else if (arc.output != NO_OUTPUT) {
      return words.outputs.add(output, arc.output).intValue();
    } else {
      return output.intValue();
    }
  }

  /**
   * Looks up HunspellAffix prefixes that have an append that matches the String created from the given char array, offset and length
   *
   * @param word Char array to generate the String from
   * @param offset Offset in the char array that the String starts at
   * @param length Length from the offset that the String is
   * @return List of HunspellAffix prefixes with an append that matches the String, or {@code null} if none are found
   */
  public List<Affix> lookupPrefix(char word[], int offset, int length) {
    return prefixes.get(word, offset, length);
  }

  /**
   * Looks up HunspellAffix suffixes that have an append that matches the String created from the given char array, offset and length
   *
   * @param word Char array to generate the String from
   * @param offset Offset in the char array that the String starts at
   * @param length Length from the offset that the String is
   * @return List of HunspellAffix suffixes with an append that matches the String, or {@code null} if none are found
   */
  List<Affix> lookupSuffix(char word[], int offset, int length) {
    return suffixes.get(word, offset, length);
  }

  /**
   * Reads the affix file through the provided InputStream, building up the prefix and suffix maps
   *
   * @param affixStream InputStream to read the content of the affix file from
   * @param decoder CharsetDecoder to decode the content of the file
   * @throws IOException Can be thrown while reading from the InputStream
   */
  private void readAffixFile(InputStream affixStream, CharsetDecoder decoder) throws IOException, ParseException {
    prefixes = new CharArrayMap<List<Affix>>(Version.LUCENE_CURRENT, 8, false);
    suffixes = new CharArrayMap<List<Affix>>(Version.LUCENE_CURRENT, 8, false);
    Map<String,Integer> seenPatterns = new HashMap<>();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(affixStream, decoder));
    String line = null;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith(ALIAS_KEY)) {
        parseAlias(line);
      } else if (line.startsWith(PREFIX_KEY)) {
        parseAffix(prefixes, line, reader, PREFIX_CONDITION_REGEX_PATTERN, seenPatterns);
      } else if (line.startsWith(SUFFIX_KEY)) {
        parseAffix(suffixes, line, reader, SUFFIX_CONDITION_REGEX_PATTERN, seenPatterns);
      } else if (line.startsWith(FLAG_KEY)) {
        // Assume that the FLAG line comes before any prefix or suffixes
        // Store the strategy so it can be used when parsing the dic file
        flagParsingStrategy = getFlagParsingStrategy(line);
      }
    }
  }

  /**
   * Parses a specific affix rule putting the result into the provided affix map
   * 
   * @param affixes Map where the result of the parsing will be put
   * @param header Header line of the affix rule
   * @param reader BufferedReader to read the content of the rule from
   * @param conditionPattern {@link String#format(String, Object...)} pattern to be used to generate the condition regex
   *                         pattern
   * @param seenPatterns map from condition -> index of patterns, for deduplication.
   * @throws IOException Can be thrown while reading the rule
   */
  private void parseAffix(CharArrayMap<List<Affix>> affixes,
                          String header,
                          LineNumberReader reader,
                          String conditionPattern,
                          Map<String,Integer> seenPatterns) throws IOException, ParseException {
    
    BytesRef scratch = new BytesRef();
    String args[] = header.split("\\s+");

    boolean crossProduct = args[2].equals("Y");
    
    int numLines = Integer.parseInt(args[3]);
    for (int i = 0; i < numLines; i++) {
      String line = reader.readLine();
      String ruleArgs[] = line.split("\\s+");

      if (ruleArgs.length < 5) {
          throw new ParseException("The affix file contains a rule with less than five elements", reader.getLineNumber());
      }

      
      char flag = flagParsingStrategy.parseFlag(ruleArgs[1]);
      String strip = ruleArgs[2].equals("0") ? "" : ruleArgs[2];
      String affixArg = ruleArgs[3];
      char appendFlags[] = null;
      
      int flagSep = affixArg.lastIndexOf('/');
      if (flagSep != -1) {
        String flagPart = affixArg.substring(flagSep + 1);
        affixArg = affixArg.substring(0, flagSep);

        if (aliasCount > 0) {
          flagPart = getAliasValue(Integer.parseInt(flagPart));
        } 
        
        appendFlags = flagParsingStrategy.parseFlags(flagPart);
        Arrays.sort(appendFlags);
      }

      String condition = ruleArgs[4];
      // at least the gascon affix file has this issue
      if (condition.startsWith("[") && !condition.endsWith("]")) {
        condition = condition + "]";
      }
      // "dash hasn't got special meaning" (we must escape it)
      if (condition.indexOf('-') >= 0) {
        condition = condition.replace("-", "\\-");
      }

      String regex = String.format(Locale.ROOT, conditionPattern, condition);
      
      // deduplicate patterns
      Integer patternIndex = seenPatterns.get(regex);
      if (patternIndex == null) {
        patternIndex = patterns.size();
        seenPatterns.put(regex, patternIndex);
        Pattern pattern = Pattern.compile(regex);
        patterns.add(pattern);
      }
      
      Affix affix = new Affix();
      scratch.copyChars(strip);
      int ord = stripLookup.add(scratch);
      if (ord < 0) {
        // already exists in our hash
        ord = (-ord)-1;
      }
      affix.setStrip(ord);
      affix.setFlag(flag);
      affix.setCondition(patternIndex);
      affix.setCrossProduct(crossProduct);
      if (appendFlags == null) {
        appendFlags = NOFLAGS;
      }
      
      final int hashCode = encodeFlagsWithHash(scratch, appendFlags);
      ord = flagLookup.add(scratch, hashCode);
      if (ord < 0) {
        // already exists in our hash
        ord = (-ord)-1;
      }
      affix.setAppendFlags(ord);
      
      List<Affix> list = affixes.get(affixArg);
      if (list == null) {
        list = new ArrayList<Affix>();
        affixes.put(affixArg, list);
      }
      
      list.add(affix);
    }
  }

  /**
   * Parses the encoding specified in the affix file readable through the provided InputStream
   *
   * @param affix InputStream for reading the affix file
   * @return Encoding specified in the affix file
   * @throws IOException Can be thrown while reading from the InputStream
   * @throws ParseException Thrown if the first non-empty non-comment line read from the file does not adhere to the format {@code SET <encoding>}
   */
  private String getDictionaryEncoding(InputStream affix) throws IOException, ParseException {
    final StringBuilder encoding = new StringBuilder();
    for (;;) {
      encoding.setLength(0);
      int ch;
      while ((ch = affix.read()) >= 0) {
        if (ch == '\n') {
          break;
        }
        if (ch != '\r') {
          encoding.append((char)ch);
        }
      }
      if (
          encoding.length() == 0 || encoding.charAt(0) == '#' ||
          // this test only at the end as ineffective but would allow lines only containing spaces:
          encoding.toString().trim().length() == 0
      ) {
        if (ch < 0) {
          throw new ParseException("Unexpected end of affix file.", 0);
        }
        continue;
      }
      if (encoding.length() > 4 && "SET ".equals(encoding.substring(0, 4))) {
        // cleanup the encoding string, too (whitespace)
        return encoding.substring(4).trim();
      }
    }
  }

  static final Map<String,String> CHARSET_ALIASES;
  static {
    Map<String,String> m = new HashMap<>();
    m.put("microsoft-cp1251", "windows-1251");
    m.put("TIS620-2533", "TIS-620");
    CHARSET_ALIASES = Collections.unmodifiableMap(m);
  }
  
  /**
   * Retrieves the CharsetDecoder for the given encoding.  Note, This isn't perfect as I think ISCII-DEVANAGARI and
   * MICROSOFT-CP1251 etc are allowed...
   *
   * @param encoding Encoding to retrieve the CharsetDecoder for
   * @return CharSetDecoder for the given encoding
   */
  private CharsetDecoder getJavaEncoding(String encoding) {
    if ("ISO8859-14".equals(encoding)) {
      return new ISO8859_14Decoder();
    }
    String canon = CHARSET_ALIASES.get(encoding);
    if (canon != null) {
      encoding = canon;
    }
    Charset charset = Charset.forName(encoding);
    return charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE);
  }

  /**
   * Determines the appropriate {@link FlagParsingStrategy} based on the FLAG definition line taken from the affix file
   *
   * @param flagLine Line containing the flag information
   * @return FlagParsingStrategy that handles parsing flags in the way specified in the FLAG definition
   */
  private FlagParsingStrategy getFlagParsingStrategy(String flagLine) {
    String flagType = flagLine.substring(5);

    if (NUM_FLAG_TYPE.equals(flagType)) {
      return new NumFlagParsingStrategy();
    } else if (UTF8_FLAG_TYPE.equals(flagType)) {
      return new SimpleFlagParsingStrategy();
    } else if (LONG_FLAG_TYPE.equals(flagType)) {
      return new DoubleASCIIFlagParsingStrategy();
    }

    throw new IllegalArgumentException("Unknown flag type: " + flagType);
  }

  /**
   * Reads the dictionary file through the provided InputStream, building up the words map
   *
   * @param dictionary InputStream to read the dictionary file through
   * @param decoder CharsetDecoder used to decode the contents of the file
   * @throws IOException Can be thrown while reading from the file
   */
  private void readDictionaryFile(InputStream dictionary, CharsetDecoder decoder, Builder<Long> words) throws IOException {
    BytesRef flagsScratch = new BytesRef();
    IntsRef scratchInts = new IntsRef();
    
    BufferedReader lines = new BufferedReader(new InputStreamReader(dictionary, decoder));
    String line = lines.readLine(); // first line is number of entries (approximately, sometimes)
    
    File unsorted = File.createTempFile("unsorted", "dat", tempDir);
    try (ByteSequencesWriter writer = new ByteSequencesWriter(unsorted)) {
      while ((line = lines.readLine()) != null) {
        writer.write(line.getBytes(IOUtils.CHARSET_UTF_8));
      }
    }
    File sorted = File.createTempFile("sorted", "dat", tempDir);
    
    OfflineSorter sorter = new OfflineSorter(new Comparator<BytesRef>() {
      BytesRef scratch1 = new BytesRef();
      BytesRef scratch2 = new BytesRef();
      
      @Override
      public int compare(BytesRef o1, BytesRef o2) {
        scratch1.bytes = o1.bytes;
        scratch1.offset = o1.offset;
        scratch1.length = o1.length;
        
        for (int i = scratch1.length - 1; i >= 0; i--) {
          if (scratch1.bytes[scratch1.offset + i] == '/') {
            scratch1.length = i;
            break;
          }
        }
        
        scratch2.bytes = o2.bytes;
        scratch2.offset = o2.offset;
        scratch2.length = o2.length;
        
        for (int i = scratch2.length - 1; i >= 0; i--) {
          if (scratch2.bytes[scratch2.offset + i] == '/') {
            scratch2.length = i;
            break;
          }
        }
        
        return scratch1.compareTo(scratch2);
      }
    });
    sorter.sort(unsorted, sorted);
    unsorted.delete();
    
    ByteSequencesReader reader = new ByteSequencesReader(sorted);
    BytesRef scratchLine = new BytesRef();
    
    // TODO: the flags themselves can be double-chars (long) or also numeric
    // either way the trick is to encode them as char... but they must be parsed differently
    
    BytesRef currentEntry = new BytesRef();
    char currentFlags[] = new char[0];
    
    while (reader.read(scratchLine)) {
      line = scratchLine.utf8ToString();
      String entry;
      char wordForm[];
      
      int flagSep = line.lastIndexOf('/');
      if (flagSep == -1) {
        wordForm = NOFLAGS;
        entry = line;
      } else {
        // note, there can be comments (morph description) after a flag.
        // we should really look for any whitespace
        int end = line.indexOf('\t', flagSep);
        if (end == -1)
          end = line.length();
        
        String flagPart = line.substring(flagSep + 1, end);
        if (aliasCount > 0) {
          flagPart = getAliasValue(Integer.parseInt(flagPart));
        } 
        
        wordForm = flagParsingStrategy.parseFlags(flagPart);
        Arrays.sort(wordForm);
        entry = line.substring(0, flagSep);
      }

      BytesRef scratch = new BytesRef(entry);
      int cmp = scratch.compareTo(currentEntry);
      if (cmp < 0) {
        throw new IllegalArgumentException("out of order: " + scratch.utf8ToString() + " < " + currentEntry.utf8ToString());
      } else if (cmp == 0) {
        currentFlags = merge(currentFlags, wordForm);
      } else {
        final int hashCode = encodeFlagsWithHash(flagsScratch, currentFlags);
        int ord = flagLookup.add(flagsScratch, hashCode);
        if (ord < 0) {
          // already exists in our hash
          ord = (-ord)-1;
        }
        UnicodeUtil.UTF8toUTF32(currentEntry, scratchInts);
        words.add(scratchInts, (long)ord);
        currentEntry = scratch;
        currentFlags = wordForm;
      }
    }
    
    final int hashCode = encodeFlagsWithHash(flagsScratch, currentFlags);
    int ord = flagLookup.add(flagsScratch, hashCode);
    if (ord < 0) {
      // already exists in our hash
      ord = (-ord)-1;
    }
    UnicodeUtil.UTF8toUTF32(currentEntry, scratchInts);
    words.add(scratchInts, (long)ord);
    
    reader.close();
    sorted.delete();
  }
  
  static char[] decodeFlags(BytesRef b) {
    int len = b.length >>> 1;
    char flags[] = new char[len];
    int upto = 0;
    int end = b.offset + b.length;
    for (int i = b.offset; i < end; i += 2) {
      flags[upto++] = (char)((b.bytes[i] << 8) | (b.bytes[i+1] & 0xff));
    }
    return flags;
  }
  
  static int encodeFlagsWithHash(BytesRef b, char flags[]) {
    int hash = 0;
    int len = flags.length << 1;
    b.grow(len);
    b.length = len;
    int upto = b.offset;
    for (int i = 0; i < flags.length; i++) {
      int flag = flags[i];
      hash = 31*hash + (b.bytes[upto++] = (byte) ((flag >> 8) & 0xff));
      hash = 31*hash + (b.bytes[upto++] = (byte) (flag & 0xff));
    }
    return hash;
  }

  private void parseAlias(String line) {
    String ruleArgs[] = line.split("\\s+");
    if (aliases == null) {
      //first line should be the aliases count
      final int count = Integer.parseInt(ruleArgs[1]);
      aliases = new String[count];
    } else {
      aliases[aliasCount++] = ruleArgs[1];
    }
  }
  
  private String getAliasValue(int id) {
    try {
      return aliases[id - 1];
    } catch (IndexOutOfBoundsException ex) {
      throw new IllegalArgumentException("Bad flag alias number:" + id, ex);
    }
  }

  /**
   * Abstraction of the process of parsing flags taken from the affix and dic files
   */
  private static abstract class FlagParsingStrategy {

    /**
     * Parses the given String into a single flag
     *
     * @param rawFlag String to parse into a flag
     * @return Parsed flag
     */
    char parseFlag(String rawFlag) {
      return parseFlags(rawFlag)[0];
    }

    /**
     * Parses the given String into multiple flags
     *
     * @param rawFlags String to parse into flags
     * @return Parsed flags
     */
    abstract char[] parseFlags(String rawFlags);
  }

  /**
   * Simple implementation of {@link FlagParsingStrategy} that treats the chars in each String as a individual flags.
   * Can be used with both the ASCII and UTF-8 flag types.
   */
  private static class SimpleFlagParsingStrategy extends FlagParsingStrategy {
    @Override
    public char[] parseFlags(String rawFlags) {
      return rawFlags.toCharArray();
    }
  }

  /**
   * Implementation of {@link FlagParsingStrategy} that assumes each flag is encoded in its numerical form.  In the case
   * of multiple flags, each number is separated by a comma.
   */
  private static class NumFlagParsingStrategy extends FlagParsingStrategy {
    @Override
    public char[] parseFlags(String rawFlags) {
      String[] rawFlagParts = rawFlags.trim().split(",");
      char[] flags = new char[rawFlagParts.length];
      int upto = 0;
      
      for (int i = 0; i < rawFlagParts.length; i++) {
        // note, removing the trailing X/leading I for nepali... what is the rule here?! 
        String replacement = rawFlagParts[i].replaceAll("[^0-9]", "");
        // note, ignoring empty flags (this happens in danish, for example)
        if (replacement.isEmpty()) {
          continue;
        }
        flags[upto++] = (char) Integer.parseInt(replacement);
      }

      if (upto < flags.length) {
        flags = Arrays.copyOf(flags, upto);
      }
      return flags;
    }
  }

  /**
   * Implementation of {@link FlagParsingStrategy} that assumes each flag is encoded as two ASCII characters whose codes
   * must be combined into a single character.
   *
   * TODO (rmuir) test
   */
  private static class DoubleASCIIFlagParsingStrategy extends FlagParsingStrategy {

    @Override
    public char[] parseFlags(String rawFlags) {
      if (rawFlags.length() == 0) {
        return new char[0];
      }

      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < rawFlags.length(); i+=2) {
        char cookedFlag = (char) ((int) rawFlags.charAt(i) + (int) rawFlags.charAt(i + 1));
        builder.append(cookedFlag);
      }
      
      char flags[] = new char[builder.length()];
      builder.getChars(0, builder.length(), flags, 0);
      return flags;
    }
  }
  
  static boolean hasFlag(char flags[], char flag) {
    return Arrays.binarySearch(flags, flag) >= 0;
  }
  
  static char[] merge(char[] flags1, char[] flags2) {
    char merged[] = new char[flags1.length + flags2.length];
    int i1 = 0, i2 = 0;
    int last = -1;
    int upto = 0;
    
    while (i1 < flags1.length && i2 < flags2.length) {
      final char next;
      if (flags1[i1] <= flags2[i2]) {
        next = flags1[i1++];
      } else {
        next = flags2[i2++];
      }
      if (next != last) {
        merged[upto++] = next;
        last = next;
      }
    }
    
    while (i1 < flags1.length) {
      char next = flags1[i1++];
      if (next != last) {
        merged[upto++] = next;
        last = next;
      }
    }
    
    while (i2 < flags2.length) {
      char next = flags2[i2++];
      if (next != last) {
        merged[upto++] = next;
        last = next;
      }
    }
    
    if (merged.length != upto) {
      merged = Arrays.copyOf(merged, upto);
    }
    
    return merged;
  }
}
