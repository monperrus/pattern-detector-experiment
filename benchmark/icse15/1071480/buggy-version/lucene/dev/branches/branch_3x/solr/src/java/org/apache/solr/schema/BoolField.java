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
import org.apache.solr.search.function.OrdFieldSource;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.response.XMLWriter;
import org.apache.solr.analysis.SolrAnalyzer;

import java.util.Map;
import java.io.Reader;
import java.io.IOException;
/**
 * @version $Id$
 */
public class BoolField extends FieldType {
  @Override
  protected void init(IndexSchema schema, Map<String,String> args) {
  }

  @Override
  public SortField getSortField(SchemaField field,boolean reverse) {
    field.checkSortability();
    return getStringSort(field,reverse);
  }

  @Override
  public ValueSource getValueSource(SchemaField field) {
    return new OrdFieldSource(field.name);
  }

  // avoid instantiating every time...
  protected final static char[] TRUE_TOKEN = {'T'};
  protected final static char[] FALSE_TOKEN = {'F'};

  ////////////////////////////////////////////////////////////////////////
  // TODO: look into creating my own queryParser that can more efficiently
  // handle single valued non-text fields (int,bool,etc) if needed.

  protected final static Analyzer boolAnalyzer = new SolrAnalyzer() {
    @Override
    public TokenStreamInfo getStream(String fieldName, Reader reader) {
      Tokenizer tokenizer = new Tokenizer(reader) {
        final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        boolean done = false;

        @Override
        public void reset(Reader input) throws IOException {
          done = false;
          super.reset(input);
        }

        @Override
        public boolean incrementToken() throws IOException {
          clearAttributes();
          if (done) return false;
          done = true;
          int ch = input.read();
          if (ch==-1) return false;
          termAtt.copyBuffer(
                  ((ch=='t' || ch=='T' || ch=='1') ? TRUE_TOKEN : FALSE_TOKEN)
                  ,0,1);
          return true;
        }
      };

      return new TokenStreamInfo(tokenizer, tokenizer);
    }
  };


  @Override
  public Analyzer getAnalyzer() {
    return boolAnalyzer;
  }

  @Override
  public Analyzer getQueryAnalyzer() {
    return boolAnalyzer;
  }

  @Override
  public String toInternal(String val) {
    char ch = (val!=null && val.length()>0) ? val.charAt(0) : 0;
    return (ch=='1' || ch=='t' || ch=='T') ? "T" : "F";
  }

  @Override
  public String toExternal(Fieldable f) {
    return indexedToReadable(f.stringValue());
  }

  @Override
  public Boolean toObject(Fieldable f) {
    return Boolean.valueOf( toExternal(f) );
  }

  @Override
  public String indexedToReadable(String indexedForm) {
    char ch = indexedForm.charAt(0);
    return ch=='T' ? "true" : "false";
  }

  @Override
  public void write(XMLWriter xmlWriter, String name, Fieldable f) throws IOException {
    xmlWriter.writeBool(name, f.stringValue().charAt(0) =='T');
  }

  @Override
  public void write(TextResponseWriter writer, String name, Fieldable f) throws IOException {
    writer.writeBool(name, f.stringValue().charAt(0) =='T');
  }
}
