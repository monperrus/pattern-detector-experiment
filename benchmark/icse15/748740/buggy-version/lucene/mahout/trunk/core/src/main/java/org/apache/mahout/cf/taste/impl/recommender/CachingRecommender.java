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

package org.apache.mahout.cf.taste.impl.recommender;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.Cache;
import org.apache.mahout.cf.taste.impl.common.Pair;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.impl.common.Retriever;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Item;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.recommender.Rescorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>A {@link Recommender} which caches the results from another {@link Recommender} in memory.
 * Results are held by {@link SoftReference}s so that the JVM may reclaim memory from the recommendationCache
 * in low-memory situations.</p>
 */
public final class CachingRecommender implements Recommender {

  private static final Logger log = LoggerFactory.getLogger(CachingRecommender.class);

  private final Recommender recommender;
  private final AtomicInteger maxHowMany;
  private final Cache<Object, Recommendations> recommendationCache;
  private final Cache<Pair<?, ?>, Double> estimatedPrefCache;
  private final RefreshHelper refreshHelper;
  private Rescorer<Item> currentRescorer;

  public CachingRecommender(Recommender recommender) throws TasteException {
    if (recommender == null) {
      throw new IllegalArgumentException("recommender is null");
    }
    this.recommender = recommender;
    this.maxHowMany = new AtomicInteger(1);
    // Use "num users" as an upper limit on cache size. Rough guess.
    int numUsers = recommender.getDataModel().getNumUsers();
    this.recommendationCache =
        new Cache<Object, Recommendations>(new RecommendationRetriever(this.recommender), numUsers);
    this.estimatedPrefCache =
            new Cache<Pair<?, ?>, Double>(new EstimatedPrefRetriever(this.recommender), numUsers);
    this.refreshHelper = new RefreshHelper(new Callable<Object>() {
      @Override
      public Object call() {
        clear();
        return null;
      }
    });
    this.refreshHelper.addDependency(recommender);
  }

  private synchronized void setCurrentRescorer(Rescorer<Item> rescorer) {
    if (rescorer == null) {
      if (currentRescorer != null) {
        currentRescorer = null;
        clear();
      }
    } else {
      if (!rescorer.equals(currentRescorer)) {
        currentRescorer = rescorer;
        clear();
      }
    }
  }

  @Override
  public List<RecommendedItem> recommend(Object userID, int howMany) throws TasteException {
    return recommend(userID, howMany, null);
  }

  @Override
  public List<RecommendedItem> recommend(Object userID, int howMany, Rescorer<Item> rescorer)
          throws TasteException {
    if (userID == null) {
      throw new IllegalArgumentException("userID is null");
    }
    if (howMany < 1) {
      throw new IllegalArgumentException("howMany must be at least 1");
    }

    synchronized (maxHowMany) {
      if (howMany > maxHowMany.get()) {
        maxHowMany.set(howMany);
      }
    }

    setCurrentRescorer(rescorer);

    Recommendations recommendations = recommendationCache.get(userID);
    if (recommendations.getItems().size() < howMany && !recommendations.isNoMoreRecommendableItems()) {
      clear(userID);
      recommendations = recommendationCache.get(userID);
      if (recommendations.getItems().size() < howMany) {
        recommendations.setNoMoreRecommendableItems(true);
      }
    }

    List<RecommendedItem> recommendedItems = recommendations.getItems();
    return recommendedItems.size() > howMany ?
           recommendedItems.subList(0, howMany) :
           recommendedItems;
  }

  @Override
  public double estimatePreference(Object userID, Object itemID) throws TasteException {
    return estimatedPrefCache.get(new Pair<Object, Object>(userID, itemID));
  }

  @Override
  public void setPreference(Object userID, Object itemID, double value) throws TasteException {
    recommender.setPreference(userID, itemID, value);
    clear(userID);
  }

  @Override
  public void removePreference(Object userID, Object itemID) throws TasteException {
    recommender.removePreference(userID, itemID);
    clear(userID);
  }

  @Override
  public DataModel getDataModel() {
    return recommender.getDataModel();
  }

  @Override
  public void refresh(Collection<Refreshable> alreadyRefreshed) {
    refreshHelper.refresh(alreadyRefreshed);
  }

  /**
   * <p>Clears cached recommendations for the given user.</p>
   *
   * @param userID clear cached data associated with this user ID
   */
  public void clear(Object userID) {
    log.debug("Clearing recommendations for user ID '{}'", userID);
    recommendationCache.remove(userID);
  }

  /**
   * <p>Clears all cached recommendations.</p>
   */
  public void clear() {
    log.debug("Clearing all recommendations...");
    recommendationCache.clear();
  }

  @Override
  public String toString() {
    return "CachingRecommender[recommender:" + recommender + ']';
  }

  private final class RecommendationRetriever implements Retriever<Object, Recommendations> {

    private final Recommender recommender;

    private RecommendationRetriever(Recommender recommender) {
      this.recommender = recommender;
    }

    @Override
    public Recommendations get(Object key) throws TasteException {
      log.debug("Retrieving new recommendations for user ID '{}'", key);
      List<RecommendedItem> recommendations;
      int howMany = maxHowMany.get();
      if (currentRescorer == null) {
        recommendations = recommender.recommend(key, howMany);
      } else {
        recommendations = recommender.recommend(key, howMany, currentRescorer);
      }
      return new Recommendations(Collections.unmodifiableList(recommendations));
    }
  }

  private static final class EstimatedPrefRetriever implements Retriever<Pair<?, ?>, Double> {

    private final Recommender recommender;

    private EstimatedPrefRetriever(Recommender recommender) {
      this.recommender = recommender;
    }

    @Override
    public Double get(Pair<?, ?> key) throws TasteException {
      Object userID = key.getFirst();
      Object itemID = key.getSecond();
      log.debug("Retrieving estimated preference for user ID '{}' and item ID '{}'", userID, itemID);
      return recommender.estimatePreference(userID, itemID);
    }
  }

  private static final class Recommendations {

    private final List<RecommendedItem> items;
    private boolean noMoreRecommendableItems;

    private Recommendations(List<RecommendedItem> items) {
      this.items = items;
    }

    List<RecommendedItem> getItems() {
      return items;
    }

    boolean isNoMoreRecommendableItems() {
      return noMoreRecommendableItems;
    }

    void setNoMoreRecommendableItems(boolean noMoreRecommendableItems) {
      this.noMoreRecommendableItems = noMoreRecommendableItems;
    }
  }

}
