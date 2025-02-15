/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.dht;

import java.util.Comparator;

public interface IPartitioner<T extends Token>
{
    /**
     * Transform key to on-disk format s.t. keys are stored in node comparison order.
     * This lets bootstrap rip out parts of the sstable sequentially instead of doing random seeks.
     *
     * @param key the raw, client-facing key
     * @return decorated on-disk version of key
     */
    public String decorateKey(String key);

    public String undecorateKey(String decoratedKey);

    public Comparator<String> getDecoratedKeyComparator();

    public Comparator<String> getReverseDecoratedKeyComparator();

	/**
	 * @return The minimum possible Token in the range that is being partitioned.
	 */
	public T getMinimumToken();

    /**
     * @return a Token that can be used to route a given key
     */
    public T getToken(String key);

    /**
     * @return the default Token to represent this node if none was saved.
     * Uses the one given in the InitialToken configuration directive,
     * or picks one automatically if that was not given.
     */
    public T getDefaultToken();

    public Token.TokenFactory getTokenFactory();
    
    /**
     * @return True if the implementing class preserves key order in the Tokens
     * it generates.
     */
    public boolean preservesOrder();
}
