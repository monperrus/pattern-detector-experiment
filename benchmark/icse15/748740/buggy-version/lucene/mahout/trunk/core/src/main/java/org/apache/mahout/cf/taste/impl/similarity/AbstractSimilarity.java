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

package org.apache.mahout.cf.taste.impl.similarity;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.Weighting;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.PreferenceInferrer;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Item;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.User;
import org.apache.mahout.cf.taste.transforms.SimilarityTransform;
import org.apache.mahout.cf.taste.transforms.PreferenceTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * Abstract superclass encapsulating functionality that is common to most
 * implementations in this package.
 */
abstract class AbstractSimilarity implements UserSimilarity, ItemSimilarity {

  private static final Logger log = LoggerFactory.getLogger(AbstractSimilarity.class);

  private final DataModel dataModel;
  private PreferenceInferrer inferrer;
  private PreferenceTransform prefTransform;
  private SimilarityTransform<Object> similarityTransform;
  private boolean weighted;
  private int cachedNumItems;
  private int cachedNumUsers;
  private final RefreshHelper refreshHelper;

  /**
   * <p>Creates a normal (unweighted) {@link AbstractSimilarity}.</p>
   */
  AbstractSimilarity(DataModel dataModel) throws TasteException {
    this(dataModel, Weighting.UNWEIGHTED);
  }

  /**
   * <p>Creates a possibly weighted {@link AbstractSimilarity}.</p>
   */
  AbstractSimilarity(DataModel dataModel, Weighting weighting) throws TasteException {
    if (dataModel == null) {
      throw new IllegalArgumentException("dataModel is null");
    }
    this.dataModel = dataModel;
    this.weighted = weighting == Weighting.WEIGHTED;
    this.cachedNumItems = dataModel.getNumItems();
    this.cachedNumUsers = dataModel.getNumUsers();
    this.refreshHelper = new RefreshHelper(new Callable<Object>() {
      @Override
      public Object call() throws TasteException {
        cachedNumItems = AbstractSimilarity.this.dataModel.getNumItems();
        cachedNumUsers = AbstractSimilarity.this.dataModel.getNumUsers();
        return null;
      }
    });
    this.refreshHelper.addDependency(this.dataModel);
    this.refreshHelper.addDependency(this.inferrer);
    this.refreshHelper.addDependency(this.prefTransform);
    this.refreshHelper.addDependency(this.similarityTransform);
  }

  final DataModel getDataModel() {
    return dataModel;
  }

  final PreferenceInferrer getPreferenceInferrer() {
    return inferrer;
  }

  @Override
  public final void setPreferenceInferrer(PreferenceInferrer inferrer) {
    if (inferrer == null) {
      throw new IllegalArgumentException("inferrer is null");
    }
    this.inferrer = inferrer;
  }

  public final PreferenceTransform getPrefTransform() {
    return prefTransform;
  }

  public final void setPrefTransform(PreferenceTransform prefTransform) {
    this.prefTransform = prefTransform;
  }

  public final SimilarityTransform<Object> getSimilarityTransform() {
    return similarityTransform;
  }

  public final void setSimilarityTransform(SimilarityTransform<Object> similarityTransform) {
    this.similarityTransform = similarityTransform;
  }

  final boolean isWeighted() {
    return weighted;
  }

  /**
   * <p>Several subclasses in this package implement this method to actually compute the similarity
   * from figures computed over users or items. Note that the computations in this class "center" the
   * data, such that X and Y's mean are 0.</p>
   *
   * <p>Note that the sum of all X and Y values must then be 0. This value isn't passed down into
   * the standard similarity computations as a result.</p>
   *
   * @param n total number of users or items
   * @param sumXY sum of product of user/item preference values, over all items/users prefererred by
   * both users/items
   * @param sumX2 sum of the square of user/item preference values, over the first item/user
   * @param sumY2 sum of the square of the user/item preference values, over the second item/user
   * @param sumXYdiff2 sum of squares of differences in X and Y values
   * @return similarity value between -1.0 and 1.0, inclusive, or {@link Double#NaN} if no similarity
   *         can be computed (e.g. when no {@link Item}s have been rated by both {@link User}s
   */
  abstract double computeResult(int n, double sumXY, double sumX2, double sumY2, double sumXYdiff2);

  @Override
  public double userSimilarity(User user1, User user2) throws TasteException {

    if (user1 == null || user2 == null) {
      throw new IllegalArgumentException("user1 or user2 is null");
    }

    Preference[] xPrefs = user1.getPreferencesAsArray();
    Preference[] yPrefs = user2.getPreferencesAsArray();

    if (xPrefs.length == 0 || yPrefs.length == 0) {
      return Double.NaN;
    }

    Preference xPref = xPrefs[0];
    Preference yPref = yPrefs[0];
    Item xIndex = xPref.getItem();
    Item yIndex = yPref.getItem();
    int xPrefIndex = 1;
    int yPrefIndex = 1;

    double sumX = 0.0;
    double sumX2 = 0.0;
    double sumY = 0.0;
    double sumY2 = 0.0;
    double sumXY = 0.0;
    double sumXYdiff2 = 0.0;
    int count = 0;

    boolean hasInferrer = inferrer != null;
    boolean hasPrefTransform = prefTransform != null;

    while (true) {
      int compare = xIndex.compareTo(yIndex);
      if (hasInferrer || compare == 0) {
        double x;
        double y;
        if (compare == 0) {
          // Both users expressed a preference for the item
          if (hasPrefTransform) {
            x = prefTransform.getTransformedValue(xPref);
            y = prefTransform.getTransformedValue(yPref);
          } else {
            x = xPref.getValue();
            y = yPref.getValue();
          }
        } else {
          // Only one user expressed a preference, but infer the other one's preference and tally
          // as if the other user expressed that preference
          if (compare < 0) {
            // X has a value; infer Y's
            if (hasPrefTransform) {
              x = prefTransform.getTransformedValue(xPref);
            } else {
              x = xPref.getValue();
            }
            y = inferrer.inferPreference(user2, xIndex);
          } else {
            // compare > 0
            // Y has a value; infer X's
            x = inferrer.inferPreference(user1, yIndex);
            if (hasPrefTransform) {
              y = prefTransform.getTransformedValue(yPref);
            } else {
              y = yPref.getValue();
            }
          }
        }
        sumXY += x * y;
        sumX += x;
        sumX2 += x * x;
        sumY += y;
        sumY2 += y * y;
        double diff = x - y;
        sumXYdiff2 += diff * diff;
        count++;
      }
      if (compare <= 0) {
        if (xPrefIndex == xPrefs.length) {
          break;
        }
        xPref = xPrefs[xPrefIndex++];
        xIndex = xPref.getItem();
      }
      if (compare >= 0) {
        if (yPrefIndex == yPrefs.length) {
          break;
        }
        yPref = yPrefs[yPrefIndex++];
        yIndex = yPref.getItem();
      }
    }

    // "Center" the data. If my math is correct, this'll do it.
    double n = (double) count;
    double meanX = sumX / n;
    double meanY = sumY / n;
    // double centeredSumXY = sumXY - meanY * sumX - meanX * sumY + n * meanX * meanY;
    double centeredSumXY = sumXY - meanY * sumX;
    // double centeredSumX2 = sumX2 - 2.0 * meanX * sumX + n * meanX * meanX;
    double centeredSumX2 = sumX2 - meanX * sumX;
    // double centeredSumY2 = sumY2 - 2.0 * meanY * sumY + n * meanY * meanY;
    double centeredSumY2 = sumY2 - meanY * sumY;

    double result = computeResult(count, centeredSumXY, centeredSumX2, centeredSumY2, sumXYdiff2);

    if (similarityTransform != null) {
      result = similarityTransform.transformSimilarity(user1, user2, result);
    }

    if (!Double.isNaN(result)) {
      result = normalizeWeightResult(result, count, cachedNumItems);
    }
    return result;
  }

  @Override
  public final double itemSimilarity(Item item1, Item item2) throws TasteException {

    if (item1 == null || item2 == null) {
      throw new IllegalArgumentException("item1 or item2 is null");
    }

    Preference[] xPrefs = dataModel.getPreferencesForItemAsArray(item1.getID());
    Preference[] yPrefs = dataModel.getPreferencesForItemAsArray(item2.getID());

    if (xPrefs.length == 0 || yPrefs.length == 0) {
      return Double.NaN;
    }

    Preference xPref = xPrefs[0];
    Preference yPref = yPrefs[0];
    User xIndex = xPref.getUser();
    User yIndex = yPref.getUser();
    int xPrefIndex = 1;
    int yPrefIndex = 1;

    double sumX = 0.0;
    double sumX2 = 0.0;
    double sumY = 0.0;
    double sumY2 = 0.0;
    double sumXY = 0.0;
    double sumXYdiff2 = 0.0;
    int count = 0;

    // No, pref inferrers and transforms don't appy here. I think.

    while (true) {
      int compare = xIndex.compareTo(yIndex);
      if (compare == 0) {
        // Both users expressed a preference for the item
        double x = xPref.getValue();
        double y = yPref.getValue();
        sumXY += x * y;
        sumX += x;
        sumX2 += x * x;
        sumY += y;
        sumY2 += y * y;
        double diff = x - y;
        sumXYdiff2 += diff * diff;
        count++;
      }
      if (compare <= 0) {
        if (xPrefIndex == xPrefs.length) {
          break;
        }
        xPref = xPrefs[xPrefIndex++];
        xIndex = xPref.getUser();
      }
      if (compare >= 0) {
        if (yPrefIndex == yPrefs.length) {
          break;
        }
        yPref = yPrefs[yPrefIndex++];
        yIndex = yPref.getUser();
      }
    }

    // See comments above on these computations
    double n = (double) count;
    double meanX = sumX / n;
    double meanY = sumY / n;
    // double centeredSumXY = sumXY - meanY * sumX - meanX * sumY + n * meanX * meanY;
    double centeredSumXY = sumXY - meanY * sumX;
    // double centeredSumX2 = sumX2 - 2.0 * meanX * sumX + n * meanX * meanX;
    double centeredSumX2 = sumX2 - meanX * sumX;
    // double centeredSumY2 = sumY2 - 2.0 * meanY * sumY + n * meanY * meanY;
    double centeredSumY2 = sumY2 - meanY * sumY;

    double result = computeResult(count, centeredSumXY, centeredSumX2, centeredSumY2, sumXYdiff2);

    if (similarityTransform != null) {
      result = similarityTransform.transformSimilarity(item1, item2, result);
    }

    if (!Double.isNaN(result)) {
      result = normalizeWeightResult(result, count, cachedNumUsers);
    }
    return result;
  }

  final double normalizeWeightResult(double result, int count, int num) {
    if (weighted) {
      double scaleFactor = 1.0 - (double) count / (double) (num + 1);
      if (result < 0.0) {
        result = -1.0 + scaleFactor * (1.0 + result);
      } else {
        result = 1.0 - scaleFactor * (1.0 - result);
      }
    }
    // Make sure the result is not accidentally a little outside [-1.0, 1.0] due to rounding:
    if (result < -1.0) {
      result = -1.0;
    } else if (result > 1.0) {
      result = 1.0;
    }
    return result;
  }

  @Override
  public final void refresh(Collection<Refreshable> alreadyRefreshed) {
    refreshHelper.refresh(alreadyRefreshed);
  }

  @Override
  public final String toString() {
    return this.getClass().getSimpleName() + "[dataModel:" + dataModel + ",inferrer:" + inferrer + ']';
  }

}
