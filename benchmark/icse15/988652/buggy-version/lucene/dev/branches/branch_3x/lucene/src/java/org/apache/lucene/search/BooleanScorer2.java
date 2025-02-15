  Merged /lucene/dev/trunk/lucene/src/java/org/apache/lucene/search/MultiTermQueryWrapperFilter.java:r988592
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
import java.util.ArrayList;
import java.util.List;

/* See the description in BooleanScorer.java, comparing
 * BooleanScorer & BooleanScorer2 */

/** An alternative to BooleanScorer that also allows a minimum number
 * of optional scorers that should match.
 * <br>Implements skipTo(), and has no limitations on the numbers of added scorers.
 * <br>Uses ConjunctionScorer, DisjunctionScorer, ReqOptScorer and ReqExclScorer.
 */
class BooleanScorer2 extends Scorer {
  
  private final List<Scorer> requiredScorers;
  private final List<Scorer> optionalScorers;
  private final List<Scorer> prohibitedScorers;

  private class Coordinator {
    float[] coordFactors = null;
    int maxCoord = 0; // to be increased for each non prohibited scorer
    int nrMatchers; // to be increased by score() of match counting scorers.
    
    void init() { // use after all scorers have been added.
      coordFactors = new float[maxCoord + 1];
      Similarity sim = getSimilarity();
      for (int i = 0; i <= maxCoord; i++) {
        coordFactors[i] = sim.coord(i, maxCoord);
      }
    }
    
  }

  private final Coordinator coordinator;

  /** The scorer to which all scoring will be delegated,
   * except for computing and using the coordination factor.
   */
  private final Scorer countingSumScorer;

  /** The number of optionalScorers that need to match (if there are any) */
  private final int minNrShouldMatch;
  
  private int doc = -1;

  /**
   * Creates a {@link Scorer} with the given similarity and lists of required,
   * prohibited and optional scorers. In no required scorers are added, at least
   * one of the optional scorers will have to match during the search.
   * 
   * @param similarity
   *          The similarity to be used.
   * @param minNrShouldMatch
   *          The minimum number of optional added scorers that should match
   *          during the search. In case no required scorers are added, at least
   *          one of the optional scorers will have to match during the search.
   * @param required
   *          the list of required scorers.
   * @param prohibited
   *          the list of prohibited scorers.
   * @param optional
   *          the list of optional scorers.
   */
  public BooleanScorer2(Similarity similarity, int minNrShouldMatch,
      List<Scorer> required, List<Scorer> prohibited, List<Scorer> optional) throws IOException {
    super(similarity);
    if (minNrShouldMatch < 0) {
      throw new IllegalArgumentException("Minimum number of optional scorers should not be negative");
    }
    coordinator = new Coordinator();
    this.minNrShouldMatch = minNrShouldMatch;

    optionalScorers = optional;
    coordinator.maxCoord += optional.size();

    requiredScorers = required;
    coordinator.maxCoord += required.size();
    
    prohibitedScorers = prohibited;
    
    coordinator.init();
    countingSumScorer = makeCountingSumScorer();
  }
  
  /** Count a scorer as a single match. */
  private class SingleMatchScorer extends Scorer {
    private Scorer scorer;
    private int lastScoredDoc = -1;
    // Save the score of lastScoredDoc, so that we don't compute it more than
    // once in score().
    private float lastDocScore = Float.NaN;

    SingleMatchScorer(Scorer scorer) {
      super(scorer.getSimilarity());
      this.scorer = scorer;
    }

    @Override
    public float score() throws IOException {
      int doc = docID();
      if (doc >= lastScoredDoc) {
        if (doc > lastScoredDoc) {
          lastDocScore = scorer.score();
          lastScoredDoc = doc;
        }
        coordinator.nrMatchers++;
      }
      return lastDocScore;
    }

    @Override
    public int docID() {
      return scorer.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return scorer.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      return scorer.advance(target);
    }
  }

  private Scorer countingDisjunctionSumScorer(final List<Scorer> scorers,
      int minNrShouldMatch) throws IOException {
    // each scorer from the list counted as a single matcher
    return new DisjunctionSumScorer(scorers, minNrShouldMatch) {
      private int lastScoredDoc = -1;
      // Save the score of lastScoredDoc, so that we don't compute it more than
      // once in score().
      private float lastDocScore = Float.NaN;
      @Override public float score() throws IOException {
        int doc = docID();
        if (doc >= lastScoredDoc) {
          if (doc > lastScoredDoc) {
            lastDocScore = super.score();
            lastScoredDoc = doc;
          }
          coordinator.nrMatchers += super.nrMatchers;
        }
        return lastDocScore;
      }
    };
  }

  private static final Similarity defaultSimilarity = Similarity.getDefault();

  private Scorer countingConjunctionSumScorer(List<Scorer> requiredScorers) throws IOException {
    // each scorer from the list counted as a single matcher
    final int requiredNrMatchers = requiredScorers.size();
    return new ConjunctionScorer(defaultSimilarity, requiredScorers) {
      private int lastScoredDoc = -1;
      // Save the score of lastScoredDoc, so that we don't compute it more than
      // once in score().
      private float lastDocScore = Float.NaN;
      @Override public float score() throws IOException {
        int doc = docID();
        if (doc >= lastScoredDoc) {
          if (doc > lastScoredDoc) {
            lastDocScore = super.score();
            lastScoredDoc = doc;
          }
          coordinator.nrMatchers += requiredNrMatchers;
        }
        // All scorers match, so defaultSimilarity super.score() always has 1 as
        // the coordination factor.
        // Therefore the sum of the scores of the requiredScorers
        // is used as score.
        return lastDocScore;
      }
    };
  }

  private Scorer dualConjunctionSumScorer(Scorer req1, Scorer req2) throws IOException { // non counting.
    return new ConjunctionScorer(defaultSimilarity, new Scorer[]{req1, req2});
    // All scorers match, so defaultSimilarity always has 1 as
    // the coordination factor.
    // Therefore the sum of the scores of two scorers
    // is used as score.
  }

  /** Returns the scorer to be used for match counting and score summing.
   * Uses requiredScorers, optionalScorers and prohibitedScorers.
   */
  private Scorer makeCountingSumScorer() throws IOException { // each scorer counted as a single matcher
    return (requiredScorers.size() == 0)
          ? makeCountingSumScorerNoReq()
          : makeCountingSumScorerSomeReq();
  }

  private Scorer makeCountingSumScorerNoReq() throws IOException { // No required scorers
    // minNrShouldMatch optional scorers are required, but at least 1
    int nrOptRequired = (minNrShouldMatch < 1) ? 1 : minNrShouldMatch;
    Scorer requiredCountingSumScorer;
    if (optionalScorers.size() > nrOptRequired)
      requiredCountingSumScorer = countingDisjunctionSumScorer(optionalScorers, nrOptRequired);
    else if (optionalScorers.size() == 1)
      requiredCountingSumScorer = new SingleMatchScorer(optionalScorers.get(0));
    else
      requiredCountingSumScorer = countingConjunctionSumScorer(optionalScorers);
    return addProhibitedScorers(requiredCountingSumScorer);
  }

  private Scorer makeCountingSumScorerSomeReq() throws IOException { // At least one required scorer.
    if (optionalScorers.size() == minNrShouldMatch) { // all optional scorers also required.
      ArrayList<Scorer> allReq = new ArrayList<Scorer>(requiredScorers);
      allReq.addAll(optionalScorers);
      return addProhibitedScorers(countingConjunctionSumScorer(allReq));
    } else { // optionalScorers.size() > minNrShouldMatch, and at least one required scorer
      Scorer requiredCountingSumScorer =
            requiredScorers.size() == 1
            ? new SingleMatchScorer(requiredScorers.get(0))
            : countingConjunctionSumScorer(requiredScorers);
      if (minNrShouldMatch > 0) { // use a required disjunction scorer over the optional scorers
        return addProhibitedScorers( 
                      dualConjunctionSumScorer( // non counting
                              requiredCountingSumScorer,
                              countingDisjunctionSumScorer(
                                      optionalScorers,
                                      minNrShouldMatch)));
      } else { // minNrShouldMatch == 0
        return new ReqOptSumScorer(
                      addProhibitedScorers(requiredCountingSumScorer),
                      optionalScorers.size() == 1
                        ? new SingleMatchScorer(optionalScorers.get(0))
                        // require 1 in combined, optional scorer.
                        : countingDisjunctionSumScorer(optionalScorers, 1));
      }
    }
  }
  
  /** Returns the scorer to be used for match counting and score summing.
   * Uses the given required scorer and the prohibitedScorers.
   * @param requiredCountingSumScorer A required scorer already built.
   */
  private Scorer addProhibitedScorers(Scorer requiredCountingSumScorer) throws IOException
  {
    return (prohibitedScorers.size() == 0)
          ? requiredCountingSumScorer // no prohibited
          : new ReqExclScorer(requiredCountingSumScorer,
                              ((prohibitedScorers.size() == 1)
                                ? prohibitedScorers.get(0)
                                : new DisjunctionSumScorer(prohibitedScorers)));
  }

  /** Scores and collects all matching documents.
   * @param collector The collector to which all matching documents are passed through.
   */
  @Override
  public void score(Collector collector) throws IOException {
    collector.setScorer(this);
    while ((doc = countingSumScorer.nextDoc()) != NO_MORE_DOCS) {
      collector.collect(doc);
    }
  }
  
  @Override
  protected boolean score(Collector collector, int max, int firstDocID) throws IOException {
    doc = firstDocID;
    collector.setScorer(this);
    while (doc < max) {
      collector.collect(doc);
      doc = countingSumScorer.nextDoc();
    }
    return doc != NO_MORE_DOCS;
  }

  @Override
  public int docID() {
    return doc;
  }
  
  @Override
  public int nextDoc() throws IOException {
    return doc = countingSumScorer.nextDoc();
  }
  
  @Override
  public float score() throws IOException {
    coordinator.nrMatchers = 0;
    float sum = countingSumScorer.score();
    return sum * coordinator.coordFactors[coordinator.nrMatchers];
  }

  @Override
  public int advance(int target) throws IOException {
    return doc = countingSumScorer.advance(target);
  }
}


