package org.apache.lucene.document;

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
import java.util.Set;
import java.util.HashSet;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFieldVisitor;

/** A {@link StoredFieldVisitor} that creates a {@link
 *  Document} containing all stored fields, or only specific
 *  requested fields provided to {@link #DocumentStoredFieldVisitor(Set)}
 *  This is used by {@link IndexReader#document(int)} to load a
 *  document.
 *
 * @lucene.experimental */

public class DocumentStoredFieldVisitor extends StoredFieldVisitor {
  private final Document doc = new Document();
  private final Set<String> fieldsToAdd;

  /** Load only fields named in the provided <code>Set&lt;String&gt;</code>. */
  public DocumentStoredFieldVisitor(Set<String> fieldsToAdd) {
    this.fieldsToAdd = fieldsToAdd;
  }

  /** Load only fields named in the provided <code>Set&lt;String&gt;</code>. */
  public DocumentStoredFieldVisitor(String... fields) {
    fieldsToAdd = new HashSet<String>(fields.length);
    for(String field : fields) {
      fieldsToAdd.add(field);
    }
  }

  /** Load all stored fields. */
  public DocumentStoredFieldVisitor() {
    this.fieldsToAdd = null;
  }

  @Override
  public void binaryField(FieldInfo fieldInfo, byte[] value, int offset, int length) throws IOException {
    doc.add(new BinaryField(fieldInfo.name, value));
  }

  @Override
  public void stringField(FieldInfo fieldInfo, String value) throws IOException {
    final FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(fieldInfo.storeTermVector);
    ft.setStoreTermVectors(fieldInfo.storeTermVector);
    ft.setIndexed(fieldInfo.isIndexed);
    ft.setOmitNorms(fieldInfo.omitNorms);
    ft.setIndexOptions(fieldInfo.indexOptions);
    doc.add(new Field(fieldInfo.name, value, ft));
  }

  @Override
  public void intField(FieldInfo fieldInfo, int value) {
    FieldType ft = NumericField.getFieldType(NumericField.DataType.INT, true);
    doc.add(new NumericField(fieldInfo.name, ft).setIntValue(value));
  }

  @Override
  public void longField(FieldInfo fieldInfo, long value) {
    FieldType ft = NumericField.getFieldType(NumericField.DataType.LONG, true);
    doc.add(new NumericField(fieldInfo.name, ft).setLongValue(value));
  }

  @Override
  public void floatField(FieldInfo fieldInfo, float value) {
    FieldType ft = NumericField.getFieldType(NumericField.DataType.FLOAT, true);
    doc.add(new NumericField(fieldInfo.name, ft).setFloatValue(value));
  }

  @Override
  public void doubleField(FieldInfo fieldInfo, double value) {
    FieldType ft = NumericField.getFieldType(NumericField.DataType.DOUBLE, true);
    doc.add(new NumericField(fieldInfo.name, ft).setDoubleValue(value));
  }

  @Override
  public Status needsField(FieldInfo fieldInfo) throws IOException {
    return fieldsToAdd == null || fieldsToAdd.contains(fieldInfo.name) ? Status.YES : Status.NO;
  }

  public Document getDocument() {
    return doc;
  }
}
