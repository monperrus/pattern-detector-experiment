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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.common.AbstractJob;
import org.apache.mahout.common.commandline.DefaultOptionCreator;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.MatrixSlice;
import org.apache.mahout.math.OrthonormalityVerifier;
import org.apache.mahout.math.SparseRowMatrix;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorIterable;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.math.decomposer.EigenStatus;
import org.apache.mahout.math.decomposer.SimpleEigenVerifier;
import org.apache.mahout.math.decomposer.SingularVectorVerifier;
import org.apache.mahout.math.hadoop.DistributedRowMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Class for taking the output of an eigendecomposition (specified as a Path location), and verifies correctness,
 * in terms of the following: if you have a vector e, and a matrix m, then let e' = m.timesSquared(v); the error
 * w.r.t. eigenvector-ness is the cosine of the angle between e and e':</p>
 * <pre>
 *   error(e,e') = e.dot(e') / (e.norm(2)*e'.norm(2))
 * </pre>
 * <p>A set of eigenvectors should also all be very close to orthogonal, so this job computes all inner products
 * between eigenvectors, and checks that this is close to the identity matrix.
 * </p>
 * <p>
 * Parameters used in the cleanup (other than in the input/output path options) include --minEigenvalue, which
 * specifies the value below which eigenvector/eigenvalue pairs will be discarded, and --maxError, which specifies
 * the maximum error (as defined above) to be tolerated in an eigenvector.</p>
 * <p>
 * If all the eigenvectors can fit in memory, --inMemory allows for a speedier completion of this task by doing so.
 * </p>
 */
public class EigenVerificationJob extends AbstractJob {

  public static final String CLEAN_EIGENVECTORS = "cleanEigenvectors";

  private static final Logger log = LoggerFactory.getLogger(EigenVerificationJob.class);

  private SingularVectorVerifier eigenVerifier;

  private VectorIterable eigensToVerify;

  private VectorIterable corpus;

  private double maxError;

  private double minEigenValue;

  private boolean loadEigensInMemory;

  private Path tmpOut;

  private Path outPath;

  private int maxEigensToKeep;

  private Path cleanedEigensPath;

  public void setEigensToVerify(VectorIterable eigens) {
    eigensToVerify = eigens;
  }

  @Override
  public int run(String[] args) throws Exception {
    Map<String, String> argMap = handleArgs(args);
    if (argMap == null) {
      return -1;
    } else if (argMap.isEmpty()) {
      return 0;
    }
    // parse out the arguments
    runJob(getConf(),
           new Path(argMap.get("--eigenInput")),
           new Path(argMap.get("--corpusInput")),
           getOutputPath(),
           argMap.get("--inMemory") != null,
           Double.parseDouble(argMap.get("--maxError")),
           Double.parseDouble(argMap.get("--minEigenvalue")), Integer.parseInt(argMap.get("--maxEigens")));
    return 0;
  }

  /**
   * Run the job with the given arguments
   * @param corpusInput the corpus input Path
   * @param eigenInput the eigenvector input Path
   * @param output the output Path
   * @param tempOut temporary output Path
   * @param maxError a double representing the maximum error
   * @param minEigenValue a double representing the minimum eigenvalue
   * @param inMemory a boolean requesting in-memory preparation
   * @param config the JobConf to use, or null if a default is ok (saves referencing JobConf in calling classes unless needed)
   * @throws IOException
   */
  public int run(Path corpusInput,
                 Path eigenInput,
                 Path output,
                 Path tempOut,
                 double maxError,
                 double minEigenValue,
                 boolean inMemory,
                 JobConf config) throws IOException {
    this.outPath = output;
    this.tmpOut = tempOut;
    this.maxError = maxError;
    this.minEigenValue = minEigenValue;

    if (eigenInput != null && eigensToVerify == null) {
      prepareEigens(config, eigenInput, inMemory);
    }
    DistributedRowMatrix c = new DistributedRowMatrix(corpusInput, tempOut, 1, 1);
    c.configure(config);
    corpus = c;

    // set up eigenverifier and orthoverifier TODO: allow multithreaded execution

    eigenVerifier = new SimpleEigenVerifier();

    //VectorIterable pairwiseInnerProducts = computePairwiseInnerProducts();
    computePairwiseInnerProducts();

    Map<MatrixSlice, EigenStatus> eigenMetaData = verifyEigens();

    List<Map.Entry<MatrixSlice, EigenStatus>> prunedEigenMeta = pruneEigens(eigenMetaData);

    saveCleanEigens(new Configuration(), prunedEigenMeta);
    return 0;
  }

  private Map<String, String> handleArgs(String[] args) {
    addOutputOption();
    addOption("eigenInput",
              "ei",
              "The Path for purported eigenVector input files (SequenceFile<WritableComparable,VectorWritable>.",
              null);
    addOption("corpusInput", "ci", "The Path for corpus input files (SequenceFile<WritableComparable,VectorWritable>.");
    addOption(DefaultOptionCreator.outputOption().create());
    addOption(DefaultOptionCreator.helpOption());
    addOption("inMemory", "mem", "Buffer eigen matrix into memory (if you have enough!)", "false");
    addOption("maxError", "err", "Maximum acceptable error", "0.05");
    addOption("minEigenvalue", "mev", "Minimum eigenvalue to keep the vector for", "0.0");

    return parseArguments(args);
  }

  private VectorIterable computePairwiseInnerProducts() {
    return OrthonormalityVerifier.pairwiseInnerProducts(eigensToVerify);
  }

  private void saveCleanEigens(Configuration conf, List<Map.Entry<MatrixSlice, EigenStatus>> prunedEigenMeta) throws IOException {
    Path path = new Path(outPath, CLEAN_EIGENVECTORS);
    FileSystem fs = FileSystem.get(conf);
    SequenceFile.Writer seqWriter = new SequenceFile.Writer(fs, conf, path, IntWritable.class, VectorWritable.class);
    IntWritable iw = new IntWritable();
    for (Map.Entry<MatrixSlice, EigenStatus> pruneSlice : prunedEigenMeta) {
      MatrixSlice s = pruneSlice.getKey();
      EigenStatus meta = pruneSlice.getValue();
      EigenVector ev = new EigenVector((DenseVector) s.vector(), meta.getEigenValue(), Math.abs(1 - meta.getCosAngle()), s.index());
      log.info("appending {} to {}", ev, path);
      VectorWritable vw = new VectorWritable(ev);
      iw.set(s.index());
      seqWriter.append(iw, vw);

      int numEigensWritten = 0;
      // increment the number of eigenvectors written and see if we've
      // reached our specified limit, or if we wish to write all eigenvectors
      // (latter is built-in, since numEigensWritten will always be > 0
      numEigensWritten++;
      if (numEigensWritten == maxEigensToKeep) {
        log.info("{} of the {} total eigens have been written", maxEigensToKeep, prunedEigenMeta.size());
        break;
      }
    }
    seqWriter.close();
    cleanedEigensPath = path;
  }

  private List<Map.Entry<MatrixSlice, EigenStatus>> pruneEigens(Map<MatrixSlice, EigenStatus> eigenMetaData) {
    List<Map.Entry<MatrixSlice, EigenStatus>> prunedEigenMeta = new ArrayList<Map.Entry<MatrixSlice, EigenStatus>>();

    for (Map.Entry<MatrixSlice, EigenStatus> entry : eigenMetaData.entrySet()) {
      if (Math.abs(1 - entry.getValue().getCosAngle()) < maxError && entry.getValue().getEigenValue() > minEigenValue) {
        prunedEigenMeta.add(entry);
      }
    }

    Collections.sort(prunedEigenMeta, new Comparator<Map.Entry<MatrixSlice, EigenStatus>>() {
      @Override
      public int compare(Map.Entry<MatrixSlice, EigenStatus> e1, Map.Entry<MatrixSlice, EigenStatus> e2) {
        return e1.getKey().index() - e2.getKey().index();
      }
    });
    return prunedEigenMeta;
  }

  private Map<MatrixSlice, EigenStatus> verifyEigens() {
    Map<MatrixSlice, EigenStatus> eigenMetaData = new HashMap<MatrixSlice, EigenStatus>();

    for (MatrixSlice slice : eigensToVerify) {
      EigenStatus status = eigenVerifier.verify(corpus, slice.vector());
      eigenMetaData.put(slice, status);
    }
    return eigenMetaData;
  }

  private void prepareEigens(JobConf conf, Path eigenInput, boolean inMemory) {
    DistributedRowMatrix eigens = new DistributedRowMatrix(eigenInput, tmpOut, 1, 1);
    eigens.configure(conf);
    if (inMemory) {
      List<Vector> eigenVectors = new ArrayList<Vector>();
      for (MatrixSlice slice : eigens) {
        eigenVectors.add(slice.vector());
      }
      eigensToVerify = new SparseRowMatrix(new int[] { eigenVectors.size(), eigenVectors.get(0).size() }, eigenVectors
          .toArray(new Vector[eigenVectors.size()]), true, true);

    } else {
      eigensToVerify = eigens;
    }
  }

  public Path getCleanedEigensPath() {
    return cleanedEigensPath;
  }

  public static void main(String[] args) throws Exception {
    ToolRunner.run(new EigenVerificationJob(), args);
  }

  /**
   * Progammatic invocation of run()
   * @param conf TODO
   * @param eigenInput Output of LanczosSolver
   * @param corpusInput Input of LanczosSolver
   * @param output
   * @param inMemory
   * @param maxError
   * @param minEigenValue
   * @param maxEigens
   */
  public void runJob(Configuration conf,
                     Path eigenInput,
                     Path corpusInput,
                     Path output,
                     boolean inMemory,
                     double maxError,
                     double minEigenValue, int maxEigens) throws IOException {
    // no need to handle command line arguments
    outPath = output;
    tmpOut = new Path(outPath, "tmp");
    maxEigensToKeep = maxEigens;
    this.maxError = maxError;
    if (eigenInput != null && eigensToVerify == null) {
      prepareEigens(new JobConf(conf), eigenInput, inMemory);
    }

    DistributedRowMatrix c = new DistributedRowMatrix(corpusInput, tmpOut, 1, 1);
    c.configure(new JobConf(conf));
    corpus = c;

    eigenVerifier = new SimpleEigenVerifier();
    //OrthonormalityVerifier orthoVerifier = new OrthonormalityVerifier();
    VectorIterable pairwiseInnerProducts = computePairwiseInnerProducts();
    // FIXME: Why is the above vector computed if it is never used?

    Map<MatrixSlice, EigenStatus> eigenMetaData = verifyEigens();
    List<Map.Entry<MatrixSlice, EigenStatus>> prunedEigenMeta = pruneEigens(eigenMetaData);
    saveCleanEigens(conf, prunedEigenMeta);
  }
}
