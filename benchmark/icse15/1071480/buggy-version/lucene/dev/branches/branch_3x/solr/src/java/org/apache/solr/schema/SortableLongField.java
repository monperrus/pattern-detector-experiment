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

package org.apache.solr.schema;

import org.apache.lucene.search.SortField;
import org.apache.solr.search.function.ValueSource;
import org.apache.solr.search.function.FieldCacheSource;
import org.apache.solr.search.function.DocValues;
import org.apache.solr.search.function.StringIndexDocValues;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.util.NumberUtils;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.response.XMLWriter;

import java.util.Map;
import java.io.IOException;
/**
 * @version $Id$
 */
public class SortableLongField extends FieldType {
  @Override
  protected void init(IndexSchema schema, Map<String,String> args) {
  }

  @Override
  public SortField getSortField(SchemaField field,boolean reverse) {
    return getStringSort(field,reverse);
  }

  @Override
  public ValueSource getValueSource(SchemaField field) {
    return new SortableLongFieldSource(field.name);
  }

  @Override
  public String toInternal(String val) {
    return NumberUtils.long2sortableStr(val);
  }

  @Override
  public String indexedToReadable(String indexedForm) {
    return NumberUtils.SortableStr2long(indexedForm);
  }

  @Override
  public String toExternal(Fieldable f) {
    return indexedToReadable(f.stringValue());
  }

  @Override
  public Long toObject(Fieldable f) {
    return NumberUtils.SortableStr2long(f.stringValue(),0,5);
  }
  
  @Override
  public void write(XMLWriter xmlWriter, String name, Fieldable f) throws IOException {
    String sval = f.stringValue();
    xmlWriter.writeLong(name, NumberUtils.SortableStr2long(sval,0,sval.length()));
  }

  @Override
  public void write(TextResponseWriter writer, String name, Fieldable f) throws IOException {
    String sval = f.stringValue();
    writer.writeLong(name, NumberUtils.SortableStr2long(sval,0,sval.length()));
  }
}





class SortableLongFieldSource extends FieldCacheSource {
  protected long defVal;

  public SortableLongFieldSource(String field) {
    this(field, 0);
  }

  public SortableLongFieldSource(String field, long defVal) {
    super(field);
    this.defVal = defVal;
  }

  @Override
  public String description() {
    return "slong(" + field + ')';
  }

  @Override
  public DocValues getValues(Map context, IndexReader reader) throws IOException {
    final long def = defVal;

    return new StringIndexDocValues(this, reader, field) {
      @Override
      protected String toTerm(String readableValue) {
        return NumberUtils.long2sortableStr(readableValue);
      }

      @Override
      public float floatVal(int doc) {
        return (float)longVal(doc);
      }

      @Override
      public int intVal(int doc) {
        return (int)longVal(doc);
      }

      @Override
      public long longVal(int doc) {
        int ord=order[doc];
        return ord==0 ? def  : NumberUtils.SortableStr2long(lookup[ord],0,5);
      }

      @Override
      public double doubleVal(int doc) {
        return (double)longVal(doc);
      }

      @Override
      public String strVal(int doc) {
        return Long.toString(longVal(doc));
      }

      @Override
      public String toString(int doc) {
        return description() + '=' + longVal(doc);
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SortableLongFieldSource
            && super.equals(o)
            && defVal == ((SortableLongFieldSource)o).defVal;
  }

  private static int hcode = SortableLongFieldSource.class.hashCode();
  @Override
  public int hashCode() {
    return hcode + super.hashCode() + (int)defVal;
  };
}
