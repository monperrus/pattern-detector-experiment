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

package org.apache.mahout.cf.taste.hadoop;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.WritableComparable;
import org.apache.mahout.common.RandomUtils;

/** A {@link WritableComparable} encapsulating two items. */
public final class EntityEntityWritable
    implements WritableComparable<EntityEntityWritable>, Cloneable {
  
  private long aID;
  private long bID;
  
  public EntityEntityWritable() {
  // do nothing
  }
  
  public EntityEntityWritable(long aID, long bID) {
    this.aID = aID;
    this.bID = bID;
  }
  
  public long getAID() {
    return aID;
  }
  
  public long getBID() {
    return bID;
  }

  public void set(long aID, long bID) {
    this.aID = aID;
    this.bID = bID;
  }
  
  @Override
  public void write(DataOutput out) throws IOException {
    out.writeLong(aID);
    out.writeLong(bID);
  }
  
  @Override
  public void readFields(DataInput in) throws IOException {
    aID = in.readLong();
    bID = in.readLong();
  }
  
  @Override
  public int compareTo(EntityEntityWritable that) {
    int aCompare = compare(aID, that.getAID());
    return aCompare == 0 ? compare(bID, that.getBID()) : aCompare;
  }
  
  private static int compare(long a, long b) {
    return a < b ? -1 : a > b ? 1 : 0;
  }
  
  @Override
  public int hashCode() {
    return RandomUtils.hashLong(aID) + 31 * RandomUtils.hashLong(bID);
  }
  
  @Override
  public boolean equals(Object o) {
    if (o instanceof EntityEntityWritable) {
      EntityEntityWritable that = (EntityEntityWritable) o;
      return (aID == that.getAID()) && (bID == that.getBID());
    }
    return false;
  }
  
  @Override
  public String toString() {
    return aID + "\t" + bID;
  }

  @Override
  public EntityEntityWritable clone() {
    return new EntityEntityWritable(aID, bID);
  }
  
}
