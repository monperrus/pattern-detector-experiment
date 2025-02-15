  Merged /lucene/dev/branches/branch_3x/modules/benchmark:r1230429
package org.apache.lucene.facet.enhancements;

import org.apache.lucene.analysis.TokenStream;

import org.apache.lucene.facet.enhancements.params.EnhancementsIndexingParams;
import org.apache.lucene.facet.index.attributes.CategoryAttribute;
import org.apache.lucene.facet.index.attributes.CategoryProperty;
import org.apache.lucene.facet.index.streaming.CategoryListTokenizer;
import org.apache.lucene.facet.index.streaming.CategoryParentsStream;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;

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

/**
 * This interface allows easy addition of enhanced category features. Usually, a
 * {@link CategoryEnhancement} will correspond to a {@link CategoryProperty}.
 * <p>
 * A category enhancement can contribute to the index in two possible ways:
 * <ol>
 * <li>To each category with data relevant to the enhancement, add this data to
 * the category's token payload, through
 * {@link #getCategoryTokenBytes(CategoryAttribute)}. This data will be read
 * during search using {@link #extractCategoryTokenData(byte[], int, int)}.</li>
 * <li>To each document which contains categories with data relevant to the
 * enhancement, add a {@link CategoryListTokenizer} through
 * {@link #getCategoryListTokenizer(TokenStream, EnhancementsIndexingParams, TaxonomyWriter)}
 * . The {@link CategoryListTokenizer} should add a single token which includes
 * all the enhancement relevant data from the categories. The category list
 * token's text is defined by {@link #getCategoryListTermText()}.</li>
 * </ol>
 * 
 * @lucene.experimental
 */
public interface CategoryEnhancement {

  /**
   * Get the bytes to be added to the category token payload for this
   * enhancement.
   * <p>
   * <b>NOTE</b>: The returned array is copied, it is recommended to allocate
   * a new one each time.
   * <p>
   * The bytes generated by this method are the input of
   * {@link #extractCategoryTokenData(byte[], int, int)}.
   * 
   * @param categoryAttribute
   *            The attribute of the category.
   * @return The bytes to be added to the category token payload for this
   *         enhancement.
   */
  byte[] getCategoryTokenBytes(CategoryAttribute categoryAttribute);

  /**
   * Get the data of this enhancement from a category token payload.
   * <p>
   * The input bytes for this method are generated in
   * {@link #getCategoryTokenBytes(CategoryAttribute)}.
   * 
   * @param buffer
   *            The payload buffer.
   * @param offset
   *            The offset of this enhancement's data in the buffer.
   * @param length
   *            The length of this enhancement's data (bytes).
   * @return An Object containing the data.
   */
  Object extractCategoryTokenData(byte[] buffer, int offset, int length);

  /**
   * Declarative method to indicate whether this enhancement generates
   * separate category list.
   * 
   * @return {@code true} if generates category list, else {@code false}.
   */
  boolean generatesCategoryList();

  /**
   * Returns the text of this enhancement's category list term.
   * 
   * @return The text of this enhancement's category list term.
   */
  String getCategoryListTermText();

  /**
   * Get the {@link CategoryListTokenizer} which generates the category list
   * for this enhancement. If {@link #generatesCategoryList()} returns
   * {@code false} this method will not be called.
   * 
   * @param tokenizer
   *            The input stream containing categories.
   * @param indexingParams
   *            The indexing params to use.
   * @param taxonomyWriter
   *            The taxonomy to add categories and get their ordinals.
   * @return A {@link CategoryListTokenizer} generating the category list for
   *         this enhancement, with {@code tokenizer} as it's input.
   */
  CategoryListTokenizer getCategoryListTokenizer(TokenStream tokenizer,
      EnhancementsIndexingParams indexingParams,
      TaxonomyWriter taxonomyWriter);

  /**
   * Get a {@link CategoryProperty} class to be retained when creating
   * {@link CategoryParentsStream}.
   * 
   * @return the {@link CategoryProperty} class to be retained when creating
   *         {@link CategoryParentsStream}, or {@code null} if there is no
   *         such property.
   */
  Class<? extends CategoryProperty> getRetainableProperty();

}
