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

package org.apache.mahout.clustering.syntheticcontrol.kmeans;

import java.io.IOException;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.clustering.Cluster;
import org.apache.mahout.clustering.canopy.CanopyDriver;
import org.apache.mahout.clustering.conversion.InputDriver;
import org.apache.mahout.clustering.kmeans.KMeansDriver;
import org.apache.mahout.clustering.kmeans.RandomSeedGenerator;
import org.apache.mahout.common.AbstractJob;
import org.apache.mahout.common.ClassUtils;
import org.apache.mahout.common.HadoopUtil;
import org.apache.mahout.common.commandline.DefaultOptionCreator;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.common.distance.EuclideanDistanceMeasure;
import org.apache.mahout.common.distance.SquaredEuclideanDistanceMeasure;
import org.apache.mahout.utils.clustering.ClusterDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Job extends AbstractJob {

  private static final Logger log = LoggerFactory.getLogger(Job.class);

  private static final String DIRECTORY_CONTAINING_CONVERTED_INPUT = "data";

  private Job() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      log.info("Running with only user-supplied arguments");
      ToolRunner.run(new Configuration(), new Job(), args);
    } else {
      log.info("Running with default arguments");
      Path output = new Path("output");
      Configuration conf = new Configuration();
      HadoopUtil.delete(conf, output);
      run(conf, new Path("testdata"), output, new EuclideanDistanceMeasure(), 6, 0.5, 10);
    }
  }

  @Override
  public int run(String[] args) throws IOException, ClassNotFoundException,
      InstantiationException, IllegalAccessException, InterruptedException {
    addInputOption();
    addOutputOption();
    addOption(DefaultOptionCreator.distanceMeasureOption().create());
    addOption(DefaultOptionCreator.numClustersOption().create());
    addOption(DefaultOptionCreator.t1Option().create());
    addOption(DefaultOptionCreator.t2Option().create());
    addOption(DefaultOptionCreator.convergenceOption().create());
    addOption(DefaultOptionCreator.maxIterationsOption().create());
    addOption(DefaultOptionCreator.overwriteOption().create());

    Map<String, String> argMap = parseArguments(args);
    if (argMap == null) {
      return -1;
    }

    Path input = getInputPath();
    Path output = getOutputPath();
    String measureClass = getOption(DefaultOptionCreator.DISTANCE_MEASURE_OPTION);
    if (measureClass == null) {
      measureClass = SquaredEuclideanDistanceMeasure.class.getName();
    }
    double convergenceDelta = Double
        .parseDouble(getOption(DefaultOptionCreator.CONVERGENCE_DELTA_OPTION));
    int maxIterations = Integer
        .parseInt(getOption(DefaultOptionCreator.MAX_ITERATIONS_OPTION));
    if (hasOption(DefaultOptionCreator.OVERWRITE_OPTION)) {
      HadoopUtil.delete(getConf(), output);
    }
    DistanceMeasure measure = ClassUtils.instantiateAs(measureClass, DistanceMeasure.class);
    if (hasOption(DefaultOptionCreator.NUM_CLUSTERS_OPTION)) {
      int k = Integer
          .parseInt(getOption(DefaultOptionCreator.NUM_CLUSTERS_OPTION));
      run(getConf(), input, output, measure, k, convergenceDelta, maxIterations);
    } else {
      double t1 = Double.parseDouble(getOption(DefaultOptionCreator.T1_OPTION));
      double t2 = Double.parseDouble(getOption(DefaultOptionCreator.T2_OPTION));
      run(getConf(), input, output, measure, t1, t2, convergenceDelta,
          maxIterations);
    }
    return 0;
  }

  /**
   * Run the kmeans clustering job on an input dataset using the given the
   * number of clusters k and iteration parameters. All output data will be
   * written to the output directory, which will be initially deleted if it
   * exists. The clustered points will reside in the path
   * <output>/clustered-points. By default, the job expects a file containing
   * equal length space delimited data that resides in a directory named
   * "testdata", and writes output to a directory named "output".
   * 
   * @param conf
   *          the Configuration to use
   * @param input
   *          the String denoting the input directory path
   * @param output
   *          the String denoting the output directory path
   * @param measure
   *          the DistanceMeasure to use
   * @param k
   *          the number of clusters in Kmeans
   * @param convergenceDelta
   *          the double convergence criteria for iterations
   * @param maxIterations
   *          the int maximum number of iterations
   */
  public static void run(Configuration conf, Path input, Path output,
                         DistanceMeasure measure, int k, double convergenceDelta, int maxIterations)
      throws IOException, InterruptedException, ClassNotFoundException {
    Path directoryContainingConvertedInput = new Path(output,
        DIRECTORY_CONTAINING_CONVERTED_INPUT);
    log.info("Preparing Input");
    InputDriver.runJob(input, directoryContainingConvertedInput,
        "org.apache.mahout.math.RandomAccessSparseVector");
    log.info("Running random seed to get initial clusters");
    Path clusters = new Path(output, Cluster.INITIAL_CLUSTERS_DIR);
    clusters = RandomSeedGenerator.buildRandom(conf,
        directoryContainingConvertedInput, clusters, k, measure);
    log.info("Running KMeans");
    KMeansDriver.run(conf, directoryContainingConvertedInput, clusters, output,
        measure, convergenceDelta, maxIterations, true, false);
    // run ClusterDumper
    ClusterDumper clusterDumper = new ClusterDumper(finalClusterPath(conf,
        output, maxIterations), new Path(output, "clusteredPoints"));
    clusterDumper.printClusters(null);
  }

  /**
   * Run the kmeans clustering job on an input dataset using the given distance
   * measure, t1, t2 and iteration parameters. All output data will be written
   * to the output directory, which will be initially deleted if it exists. The
   * clustered points will reside in the path <output>/clustered-points. By
   * default, the job expects the a file containing synthetic_control.data as
   * obtained from
   * http://archive.ics.uci.edu/ml/datasets/Synthetic+Control+Chart+Time+Series
   * resides in a directory named "testdata", and writes output to a directory
   * named "output".
   * 
   * @param conf
   *          the Configuration to use
   * @param input
   *          the String denoting the input directory path
   * @param output
   *          the String denoting the output directory path
   * @param measure
   *          the DistanceMeasure to use
   * @param t1
   *          the canopy T1 threshold
   * @param t2
   *          the canopy T2 threshold
   * @param convergenceDelta
   *          the double convergence criteria for iterations
   * @param maxIterations
   *          the int maximum number of iterations
   */
  public static void run(Configuration conf, Path input, Path output,
                         DistanceMeasure measure, double t1, double t2, double convergenceDelta,
                         int maxIterations)
      throws IOException, InterruptedException, ClassNotFoundException {
    Path directoryContainingConvertedInput = new Path(output,
        DIRECTORY_CONTAINING_CONVERTED_INPUT);
    log.info("Preparing Input");
    InputDriver.runJob(input, directoryContainingConvertedInput,
        "org.apache.mahout.math.RandomAccessSparseVector");
    log.info("Running Canopy to get initial clusters");
    CanopyDriver.run(conf, directoryContainingConvertedInput, output, measure,
        t1, t2, false, false);
    log.info("Running KMeans");
    KMeansDriver.run(conf, directoryContainingConvertedInput, new Path(output,
        Cluster.INITIAL_CLUSTERS_DIR), output, measure, convergenceDelta,
        maxIterations, true, false);
    // run ClusterDumper
    ClusterDumper clusterDumper = new ClusterDumper(finalClusterPath(conf,
        output, maxIterations), new Path(output, "clusteredPoints"));
    clusterDumper.printClusters(null);
  }

  /**
   * Return the path to the final iteration's clusters
   */
  private static Path finalClusterPath(Configuration conf, Path output,
      int maxIterations) throws IOException {
    FileSystem fs = FileSystem.get(conf);
    for (int i = maxIterations; i >= 0; i--) {
      Path clusters = new Path(output, "clusters-" + i);
      if (fs.exists(clusters)) {
        return clusters;
      }
    }
    return null;
  }
}
