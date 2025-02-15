package org.apache.lucene.analysis.ja;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.util.ResourceLoader;

/** Fake resource loader for tests: works if you want to fake reading a single file */
class StringMockResourceLoader implements ResourceLoader {
  String text;

  public StringMockResourceLoader(String text) {
    this.text = text;
  }

  // TODO: do this subpackages thing... wtf is that?
  public <T> T newInstance(String cname, Class<T> expectedType, String... subpackages) {
    try {
      Class<? extends T> clazz = Class.forName(cname).asSubclass(expectedType);
      return clazz.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public InputStream openResource(String resource) throws IOException {
    return new ByteArrayInputStream(text.getBytes("UTF-8"));
  }
}
