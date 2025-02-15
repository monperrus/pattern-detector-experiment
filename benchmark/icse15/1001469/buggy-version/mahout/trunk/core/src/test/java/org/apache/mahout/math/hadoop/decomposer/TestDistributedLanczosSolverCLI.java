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

package org.apache.mahout.math.hadoop.decomposer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.mahout.clustering.AbstractCluster;
import org.apache.mahout.common.MahoutTestCase;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.math.hadoop.DistributedRowMatrix;
import org.apache.mahout.math.hadoop.TestDistributedRowMatrix;
import org.apache.mahout.math.hadoop.decomposer.DistributedLanczosSolver.DistributedLanczosSolverJob;
import org.junit.Test;

public final class TestDistributedLanczosSolverCLI extends MahoutTestCase {

  @Test
  public void testDistributedLanczosSolverCLI() throws Exception {
    Path testData = getTestTempDirPath("testdata");
    DistributedRowMatrix corpus = new TestDistributedRowMatrix().randomDistributedMatrix(500, 450, 500, 10, 10.0, true, testData
        .toString());
    corpus.configure(new JobConf());
    Path output = getTestTempDirPath("output");
    Path tmp = getTestTempDirPath("tmp");
    String[] args = { "-i", new Path(testData, "distMatrix").toString(), "-o", output.toString(), "--tempDir", tmp.toString(), "--numRows", "500",
        "--numCols", "500", "--rank", "10", "--symmetric", "true" };
    new DistributedLanczosSolver().new DistributedLanczosSolverJob().run(args);

    Path rawEigenvectors = new Path(output, DistributedLanczosSolver.RAW_EIGENVECTORS);
    Matrix eigenVectors = new DenseMatrix(10, corpus.numCols());
    Configuration conf = new Configuration();

    FileSystem fs = FileSystem.get(rawEigenvectors.toUri(), conf);
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, rawEigenvectors, conf);
    try {
      Writable key = reader.getKeyClass().asSubclass(Writable.class).newInstance();
      Writable value = reader.getValueClass().asSubclass(Writable.class).newInstance();
      int i = 0;
      while (reader.next(key, value)) {
        Vector v = ((VectorWritable) value).get();
        eigenVectors.assignRow(i, v);
        System.out.println("k=" + key.toString() + " V=" + AbstractCluster.formatVector(v, null));
        value = reader.getValueClass().asSubclass(Writable.class).newInstance();
        i++;
      }
      assertEquals("number of eigenvectors", 9, i);
    } finally {
      reader.close();
    }
  }

  @Test
  public void testDistributedLanczosSolverEVJCLI() throws Exception {
    Path testData = getTestTempDirPath("testdata");
    DistributedRowMatrix corpus = new TestDistributedRowMatrix().randomDistributedMatrix(500, 450, 500, 10, 10.0, true, testData
        .toString());
    corpus.configure(new JobConf());
    Path output = getTestTempDirPath("output");
    Path tmp = getTestTempDirPath("tmp");
    String[] args = { "-i", new Path(testData, "distMatrix").toString(), "-o", output.toString(), "--tempDir", tmp.toString(), "--numRows", "500",
        "--numCols", "500", "--rank", "10", "--symmetric", "true", "--cleansvd", "true" };
    new DistributedLanczosSolver().new DistributedLanczosSolverJob().run(args);
  
    Path cleanEigenvectors = new Path(output, EigenVerificationJob.CLEAN_EIGENVECTORS);
    Matrix eigenVectors = new DenseMatrix(10, corpus.numCols());
    Configuration conf = new Configuration();
  
    FileSystem fs = FileSystem.get(cleanEigenvectors.toUri(), conf);
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, cleanEigenvectors, conf);
    try {
      Writable key = reader.getKeyClass().asSubclass(Writable.class).newInstance();
      Writable value = reader.getValueClass().asSubclass(Writable.class).newInstance();
      int i = 0;
      while (reader.next(key, value)) {
        Vector v = ((VectorWritable) value).get();
        eigenVectors.assignRow(i, v);
        System.out.println("k=" + key.toString() + " V=" + AbstractCluster.formatVector(v, null));
        value = reader.getValueClass().asSubclass(Writable.class).newInstance();
        i++;
      }
      assertEquals("number of clean eigenvectors", 4, i);
    } finally {
      reader.close();
    }
  }

}
