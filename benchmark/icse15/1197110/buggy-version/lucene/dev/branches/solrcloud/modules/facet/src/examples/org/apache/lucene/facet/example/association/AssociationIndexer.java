package org.apache.lucene.facet.example.association;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;

import org.apache.lucene.facet.enhancements.EnhancementsDocumentBuilder;
import org.apache.lucene.facet.enhancements.association.AssociationProperty;
import org.apache.lucene.facet.example.ExampleUtils;
import org.apache.lucene.facet.example.simple.SimpleUtils;
import org.apache.lucene.facet.index.CategoryContainer;
import org.apache.lucene.facet.index.CategoryDocumentBuilder;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.lucene.LuceneTaxonomyWriter;

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
 * Sample indexer creates an index, and adds to it sample documents with
 * categories, which can be simple or contain associations.
 * 
 * @lucene.experimental
 */
public class AssociationIndexer {

  /**
   * Create an index, and adds to it sample documents and categories.
   * 
   * @param indexDir
   *            Directory in which the index should be created.
   * @param taxoDir
   *            Directory in which the taxonomy index should be created.
   * @throws Exception
   *             on error (no detailed exception handling here for sample
   *             simplicity
   */
  public static void index(Directory indexDir, Directory taxoDir) throws Exception {

    // create and open an index writer
    IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig(ExampleUtils.EXAMPLE_VER, SimpleUtils.analyzer));

    // create and open a taxonomy writer
    TaxonomyWriter taxo = new LuceneTaxonomyWriter(taxoDir, OpenMode.CREATE);

    // loop over sample documents
    int nDocsAdded = 0;
    int nFacetsAdded = 0;
    for (int docNum = 0; docNum < SimpleUtils.docTexts.length; docNum++) {
      ExampleUtils.log(" ++++ DOC ID: " + docNum);
      // obtain the sample categories for current document
      CategoryContainer categoryContainer = new CategoryContainer();
      for (CategoryPath path : SimpleUtils.categories[docNum]) {
        categoryContainer.addCategory(path);
        ExampleUtils.log("\t ++++ PATH: " + path);
      }
      // and also those with associations
      CategoryPath[] associationsPaths = AssociationUtils.categories[docNum];
      AssociationProperty[] associationProps = AssociationUtils.associations[docNum];
      for (int i = 0; i < associationsPaths.length; i++) {
        categoryContainer.addCategory(associationsPaths[i], associationProps[i]);
        ExampleUtils.log("\t $$$$ Association: ("
            + associationsPaths[i] + "," + associationProps[i]
            + ")");
      }

      // we do not alter indexing parameters!
      // a category document builder will add the categories to a document
      // once build() is called
      CategoryDocumentBuilder categoryDocBuilder = new EnhancementsDocumentBuilder(
          taxo, AssociationUtils.assocIndexingParams);
      categoryDocBuilder.setCategories(categoryContainer);

      // create a plain Lucene document and add some regular Lucene fields
      // to it
      Document doc = new Document();
      doc.add(new Field(SimpleUtils.TITLE, SimpleUtils.docTitles[docNum], TextField.TYPE_STORED));
      doc.add(new TextField(SimpleUtils.TEXT, SimpleUtils.docTexts[docNum]));

      // invoke the category document builder for adding categories to the
      // document and,
      // as required, to the taxonomy index
      categoryDocBuilder.build(doc);

      // finally add the document to the index
      iw.addDocument(doc);

      nDocsAdded++;
      nFacetsAdded += categoryContainer.size();
    }

    // commit changes.
    // we commit changes to the taxonomy index prior to committing them to
    // the search index.
    // this is important, so that all facets referred to by documents in the
    // search index
    // will indeed exist in the taxonomy index.
    taxo.commit();
    iw.commit();

    // close the taxonomy index and the index - all modifications are
    // now safely in the provided directories: indexDir and taxoDir.
    taxo.close();
    iw.close();

    ExampleUtils.log("Indexed " + nDocsAdded + " documents with overall "
        + nFacetsAdded + " facets.");
  }
  
}
