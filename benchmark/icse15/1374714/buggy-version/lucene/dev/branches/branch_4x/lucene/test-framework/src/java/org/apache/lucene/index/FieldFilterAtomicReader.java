package org.apache.lucene.index;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A {@link FilterAtomicReader} that exposes only a subset
 * of fields from the underlying wrapped reader.
 */
public final class FieldFilterAtomicReader extends FilterAtomicReader {
  
  private final Set<String> fields;
  private final boolean negate;
  private final FieldInfos fieldInfos;

  public FieldFilterAtomicReader(AtomicReader in, Set<String> fields, boolean negate) {
    super(in);
    this.fields = fields;
    this.negate = negate;
    ArrayList<FieldInfo> filteredInfos = new ArrayList<FieldInfo>();
    for (FieldInfo fi : in.getFieldInfos()) {
      if (hasField(fi.name)) {
        filteredInfos.add(fi);
      }
    }
    fieldInfos = new FieldInfos(filteredInfos.toArray(new FieldInfo[filteredInfos.size()]));
  }
  
  boolean hasField(String field) {
    return negate ^ fields.contains(field);
  }

  @Override
  public FieldInfos getFieldInfos() {
    return fieldInfos;
  }

  @Override
  public Fields getTermVectors(int docID) throws IOException {
    Fields f = super.getTermVectors(docID);
    if (f == null) {
      return null;
    }
    f = new FieldFilterFields(f);
    // we need to check for emptyness, so we can return
    // null:
    return f.iterator().hasNext() ? f : null;
  }

  @Override
  public void document(final int docID, final StoredFieldVisitor visitor) throws IOException {
    super.document(docID, new StoredFieldVisitor() {
      @Override
      public void binaryField(FieldInfo fieldInfo, byte[] value, int offset, int length) throws IOException {
        visitor.binaryField(fieldInfo, value, offset, length);
      }

      @Override
      public void stringField(FieldInfo fieldInfo, String value) throws IOException {
        visitor.stringField(fieldInfo, value);
      }

      @Override
      public void intField(FieldInfo fieldInfo, int value) throws IOException {
        visitor.intField(fieldInfo, value);
      }

      @Override
      public void longField(FieldInfo fieldInfo, long value) throws IOException {
        visitor.longField(fieldInfo, value);
      }

      @Override
      public void floatField(FieldInfo fieldInfo, float value) throws IOException {
        visitor.floatField(fieldInfo, value);
      }

      @Override
      public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
        visitor.doubleField(fieldInfo, value);
      }

      @Override
      public Status needsField(FieldInfo fieldInfo) throws IOException {
        return hasField(fieldInfo.name) ? visitor.needsField(fieldInfo) : Status.NO;
      }
    });
  }

  @Override
  public Fields fields() throws IOException {
    final Fields f = super.fields();
    return (f == null) ? null : new FieldFilterFields(f);
  }

  @Override
  public DocValues docValues(String field) throws IOException {
    return hasField(field) ? super.docValues(field) : null;
  }

  @Override
  public DocValues normValues(String field) throws IOException {
    return hasField(field) ? super.normValues(field) : null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("FieldFilterAtomicReader(reader=");
    sb.append(in).append(", fields=");
    if (negate) sb.append('!');
    return sb.append(fields).append(')').toString();
  }
  
  private class FieldFilterFields extends FilterFields {

    public FieldFilterFields(Fields in) {
      super(in);
    }

    @Override
    public int size() {
      // TODO: add faster implementation!
      int c = 0;
      final Iterator<String> it = iterator();
      while (it.hasNext()) {
        it.next();
        c++;
      }
      return c;
    }

    @Override
    public Iterator<String> iterator() {
      final Iterator<String> in = super.iterator();
      return new Iterator<String>() {
        private String cached = null;
        
        @Override
        public String next() {
          if (cached != null) {
            String next = cached;
            cached = null;
            return next;
          } else {
            String next = doNext();
            if (next == null) {
              throw new NoSuchElementException();
            } else {
              return next;
            }
          }
        }

        @Override
        public boolean hasNext() {
          return cached != null || (cached = doNext()) != null;
        }
        
        private String doNext() {
          while (in.hasNext()) {
            String field = in.next();
            if (hasField(field)) {
              return field;
            }
          }
          return null;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public Terms terms(String field) throws IOException {
      return hasField(field) ? super.terms(field) : null;
    }
    
  }
  
}
