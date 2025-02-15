package org.apache.solr.spelling;

import org.apache.lucene.analysis.Token;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;


/**
 * Implementations of SolrSpellChecker must return suggestions as SpellResult instance.
 * This is converted into the required NamedList format in SpellCheckComponent.
 * 
 * @since solr 1.3
 */
public class SpellingResult {
  private Collection<Token> tokens;

  /**
   * Key == token
   * Value = Map  -> key is the suggestion, value is the frequency of the token in the collection
   */
  private Map<Token, LinkedHashMap<String, Integer>> suggestions = new LinkedHashMap<Token, LinkedHashMap<String, Integer>>();
  private Map<Token, Integer> tokenFrequency;
  public static final int NO_FREQUENCY_INFO = -1;


  public SpellingResult() {
  }

  public SpellingResult(Collection<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Adds a whole bunch of suggestions, and does not worry about frequency.
   *
   * @param token The token to associate the suggestions with
   * @param suggestions The suggestions
   */
  public void add(Token token, List<String> suggestions) {
    LinkedHashMap<String, Integer> map = this.suggestions.get(token);
    if (map == null ) {
      map = new LinkedHashMap<String, Integer>();
      this.suggestions.put(token, map);
    }
    for (String suggestion : suggestions) {
      map.put(suggestion, NO_FREQUENCY_INFO);
    }
  }

  public void add(Token token, int docFreq) {
    if (tokenFrequency == null) {
      tokenFrequency = new LinkedHashMap<Token, Integer>();
    }
    tokenFrequency.put(token, docFreq);
  }

  /**
   * Suggestions must be added with the best suggestion first.  ORDER is important.
   * @param token The {@link org.apache.lucene.analysis.Token}
   * @param suggestion The suggestion for the Token
   * @param docFreq The document frequency
   */
  public void add(Token token, String suggestion, int docFreq) {
    LinkedHashMap<String, Integer> map = this.suggestions.get(token);
    //Don't bother adding if we already have this token
    if (map == null) {
      map = new LinkedHashMap<String, Integer>();
      this.suggestions.put(token, map);
    }
    map.put(suggestion, docFreq);
  }

  /**
   * Gets the suggestions for the given token.
   *
   * @param token The {@link org.apache.lucene.analysis.Token} to look up
   * @return A LinkedHashMap of the suggestions.  Key is the suggestion, value is the token frequency in the index, else {@link #NO_FREQUENCY_INFO}.
   *
   * The suggestions are added in sorted order (i.e. best suggestion first) then the iterator will return the suggestions in order
   */
  public LinkedHashMap<String, Integer> get(Token token) {
    return suggestions.get(token);
  }

  /**
   * The token frequency of the input token in the collection
   *
   * @param token The token
   * @return The frequency or null
   */
  public Integer getTokenFrequency(Token token) {
    return tokenFrequency.get(token);
  }

  public boolean hasTokenFrequencyInfo() {
    return tokenFrequency != null && !tokenFrequency.isEmpty();
  }

  /**
   * All the suggestions.  The ordering of the inner LinkedHashMap is by best suggestion first.
   * @return The Map of suggestions for each Token.  Key is the token, value is a LinkedHashMap whose key is the Suggestion and the value is the frequency or {@link #NO_FREQUENCY_INFO} if frequency info is not available.
   *
   */
  public Map<Token, LinkedHashMap<String, Integer>> getSuggestions() {
    return suggestions;
  }

  public Map<Token, Integer> getTokenFrequency() {
    return tokenFrequency;
  }

  /**
   * @return The original tokens
   */
  public Collection<Token> getTokens() {
    return tokens;
  }

  public void setTokens(Collection<Token> tokens) {
    this.tokens = tokens;
  }
}
