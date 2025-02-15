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

package org.apache.mahout.clustering.evaluation;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.clustering.AbstractCluster;
import org.apache.mahout.clustering.Cluster;
import org.apache.mahout.clustering.WeightedVectorWritable;
import org.apache.mahout.clustering.kmeans.OutputLogFilter;
import org.apache.mahout.common.AbstractJob;
import org.apache.mahout.common.commandline.DefaultOptionCreator;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.math.VectorWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RepresentativePointsDriver extends AbstractJob {

  public static final String STATE_IN_KEY = "org.apache.mahout.clustering.stateIn";

  public static final String DISTANCE_MEASURE_KEY = "org.apache.mahout.clustering.measure";

  private static final Logger log = LoggerFactory.getLogger(RepresentativePointsDriver.class);

  private RepresentativePointsDriver() {
  }

  public static void main(String[] args) throws Exception {
    ToolRunner.run(new Configuration(), new RepresentativePointsDriver(), args);
  }

  @Override
  public int run(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException,
      InterruptedException {
    addInputOption();
    addOutputOption();
    addOption(DefaultOptionCreator.distanceMeasureOption().create());
    addOption(DefaultOptionCreator.maxIterationsOption().create());
    addOption(DefaultOptionCreator.methodOption().create());
    if (parseArguments(args) == null) {
      return -1;
    }

    Path input = getInputPath();
    Path output = getOutputPath();
    String distanceMeasureClass = getOption(DefaultOptionCreator.DISTANCE_MEASURE_OPTION);
    int maxIterations = Integer.parseInt(getOption(DefaultOptionCreator.MAX_ITERATIONS_OPTION));
    boolean runSequential = getOption(DefaultOptionCreator.METHOD_OPTION).equalsIgnoreCase(DefaultOptionCreator.SEQUENTIAL_METHOD);
    ClassLoader ccl = Thread.currentThread().getContextClassLoader();
    DistanceMeasure measure = ccl.loadClass(distanceMeasureClass).asSubclass(DistanceMeasure.class).newInstance();

    run(getConf(), input, null, output, measure, maxIterations, runSequential);
    return 0;
  }

  public static void run(Configuration conf,
                         Path clustersIn,
                         Path clusteredPointsIn,
                         Path output,
                         DistanceMeasure measure,
                         int numIterations,
                         boolean runSequential) throws InstantiationException, IllegalAccessException, IOException,
      InterruptedException, ClassNotFoundException {
    Path stateIn = new Path(output, "representativePoints-0");
    writeInitialState(stateIn, clustersIn);

    for (int iteration = 0; iteration < numIterations; iteration++) {
      log.info("Iteration {}", iteration);
      // point the output to a new directory per iteration
      Path stateOut = new Path(output, "representativePoints-" + (iteration + 1));
      runIteration(conf, clusteredPointsIn, stateIn, stateOut, measure, runSequential);
      // now point the input to the old output directory
      stateIn = stateOut;
    }

    conf.set(STATE_IN_KEY, stateIn.toString());
    conf.set(DISTANCE_MEASURE_KEY, measure.getClass().getName());
  }

  private static void writeInitialState(Path output, Path clustersIn) throws InstantiationException, IllegalAccessException,
      IOException, SecurityException {
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(output.toUri(), conf);
    for (FileStatus part : fs.listStatus(clustersIn)) {
      if (!part.getPath().getName().startsWith(".")) {
        Path inPart = part.getPath();
        SequenceFile.Reader reader = new SequenceFile.Reader(fs, inPart, conf);
        Writable key = reader.getKeyClass().asSubclass(Writable.class).newInstance();
        Writable value = reader.getValueClass().asSubclass(Writable.class).newInstance();
        Path path = new Path(output, inPart.getName());
        SequenceFile.Writer writer = new SequenceFile.Writer(fs, conf, path, IntWritable.class, VectorWritable.class);
        while (reader.next(key, value)) {
          Cluster cluster = (Cluster) value;
          log.info("C-" + cluster.getId() + ": " + AbstractCluster.formatVector(cluster.getCenter(), null));
          writer.append(new IntWritable(cluster.getId()), new VectorWritable(cluster.getCenter()));
        }
        writer.close();
      }
    }
  }

  private static void runIteration(Configuration conf,
                                   Path clusteredPointsIn,
                                   Path stateIn,
                                   Path stateOut,
                                   DistanceMeasure measure,
                                   boolean runSequential) throws IOException, InterruptedException, ClassNotFoundException,
      InstantiationException, IllegalAccessException {
    if (runSequential) {
      runIterationSeq(conf, clusteredPointsIn, stateIn, stateOut, measure);
    } else {
      runIterationMR(conf, clusteredPointsIn, stateIn, stateOut, measure);
    }
  }

  /**
   * Run the job using supplied arguments as a sequential process
   * @param conf 
   *          the Configuration to use
   * @param input
   *          the directory pathname for input points
   * @param stateIn
   *          the directory pathname for input state
   * @param stateOut
   *          the directory pathname for output state
   * @param measure
   *          the DistanceMeasure to use
   */
  private static void runIterationSeq(Configuration conf,
                                      Path clusteredPointsIn,
                                      Path stateIn,
                                      Path stateOut,
                                      DistanceMeasure measure) throws IOException, InstantiationException, IllegalAccessException {

    Map<Integer, List<VectorWritable>> repPoints = RepresentativePointsMapper.getRepresentativePoints(conf, stateIn);
    Map<Integer, WeightedVectorWritable> mostDistantPoints = new HashMap<Integer, WeightedVectorWritable>();
    FileSystem fs = FileSystem.get(clusteredPointsIn.toUri(), conf);
    FileStatus[] status = fs.listStatus(clusteredPointsIn, new OutputLogFilter());
    int part = 0;
    for (FileStatus s : status) {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, s.getPath(), conf);
      try {
        IntWritable key = (IntWritable) reader.getKeyClass().asSubclass(Writable.class).newInstance();
        WeightedVectorWritable vw = (WeightedVectorWritable) reader.getValueClass().asSubclass(Writable.class).newInstance();
        while (reader.next(key, vw)) {
          RepresentativePointsMapper.mapPoint(key, vw, measure, repPoints, mostDistantPoints);
        }
      } finally {
        reader.close();
      }
    }
    SequenceFile.Writer writer = new SequenceFile.Writer(fs,
                                                         conf,
                                                         new Path(stateOut, "part-m-" + part++),
                                                         IntWritable.class,
                                                         VectorWritable.class);
    try {
      for (Entry<Integer, List<VectorWritable>> entry : repPoints.entrySet()) {
        for (VectorWritable vw : entry.getValue()) {
          writer.append(new IntWritable(entry.getKey()), vw);
        }
      }
    } finally {
      writer.close();
    }
    writer = new SequenceFile.Writer(fs, conf, new Path(stateOut, "part-m-" + part++), IntWritable.class, VectorWritable.class);
    try {
      for (Map.Entry<Integer, WeightedVectorWritable> entry : mostDistantPoints.entrySet()) {
        writer.append(new IntWritable(entry.getKey()), new VectorWritable(entry.getValue().getVector()));
      }
    } finally {
      writer.close();
    }
  }

  /**
   * Run the job using supplied arguments as a Map/Reduce process
   * @param conf 
   *          the Configuration to use
   * @param input
   *          the directory pathname for input points
   * @param stateIn
   *          the directory pathname for input state
   * @param stateOut
   *          the directory pathname for output state
   * @param measure
   *          the DistanceMeasure to use
   */
  private static void runIterationMR(Configuration conf, Path input, Path stateIn, Path stateOut, DistanceMeasure measure)
      throws IOException, InterruptedException, ClassNotFoundException {
    conf.set(STATE_IN_KEY, stateIn.toString());
    conf.set(DISTANCE_MEASURE_KEY, measure.getClass().getName());
    Job job = new Job(conf);
    job.setJarByClass(RepresentativePointsDriver.class);
    job.setOutputKeyClass(IntWritable.class);
    job.setOutputValueClass(VectorWritable.class);
    job.setMapOutputKeyClass(IntWritable.class);
    job.setMapOutputValueClass(WeightedVectorWritable.class);

    FileInputFormat.setInputPaths(job, input);
    FileOutputFormat.setOutputPath(job, stateOut);

    job.setMapperClass(RepresentativePointsMapper.class);
    job.setReducerClass(RepresentativePointsReducer.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);
    job.setOutputFormatClass(SequenceFileOutputFormat.class);

    job.waitForCompletion(true);
  }
}
