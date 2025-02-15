package org.apache.mahout.classifier.sgd;

import com.google.common.collect.Lists;
import org.apache.mahout.classifier.AbstractVectorClassifier;
import org.apache.mahout.classifier.OnlineLearner;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.BinaryFunction;
import org.apache.mahout.math.function.Functions;
import org.apache.mahout.math.stats.OnlineAuc;

import java.util.List;

/**
 * Does cross-fold validation of log-likelihood and AUC on several online logistic regression
 * models. Each record is passed to all but one of the models for training and to the remaining
 * model for evaluation.  In order to maintain proper segregation between the different folds across
 * training data iterations, data should either be passed to this learner in the same order each
 * time the training data is traversed or a tracking key such as the file offset of the training
 * record should be passed with each training example.
 */
public class CrossFoldLearner extends AbstractVectorClassifier implements OnlineLearner {
  private int record;
  // minimum score to be used for computing log likelihood
  private static final double MIN_SCORE = 1e-50;
  private OnlineAuc auc = new OnlineAuc();
  private double logLikelihood;
  private final List<OnlineLogisticRegression> models = Lists.newArrayList();

  // lambda, learningRate, perTermOffset, perTermExponent
  private double[] parameters = new double[4];
  private int numFeatures;
  private PriorFunction prior;
  private double percentCorrect;

  private int windowSize = Integer.MAX_VALUE;

  // pretty much just for GSON
  public CrossFoldLearner() {
  }

  public CrossFoldLearner(int folds, int numCategories, int numFeatures, PriorFunction prior) {
    this.numFeatures = numFeatures;
    this.prior = prior;
    for (int i = 0; i < folds; i++) {
      OnlineLogisticRegression model = new OnlineLogisticRegression(numCategories, numFeatures, prior);
      model.alpha(1).stepOffset(0).decayExponent(0);
      models.add(model);
    }
  }

  // -------- builder-like configuration methods

  public CrossFoldLearner lambda(double v) {
    for (OnlineLogisticRegression model : models) {
      model.lambda(v);
    }
    return this;
  }

  public CrossFoldLearner learningRate(double x) {
    for (OnlineLogisticRegression model : models) {
      model.learningRate(x);
    }
    return this;
  }

  public CrossFoldLearner stepOffset(int x) {
    for (OnlineLogisticRegression model : models) {
      model.stepOffset(x);
    }
    return this;
  }

  public CrossFoldLearner decayExponent(double x) {
    for (OnlineLogisticRegression model : models) {
      model.decayExponent(x);
    }
    return this;
  }

  public CrossFoldLearner alpha(double alpha) {
    for (OnlineLogisticRegression model : models) {
      model.alpha(alpha);
    }
    return this;
  }

  // -------- training methods
  @Override
  public void train(int actual, Vector instance) {
    train(record, actual, instance);
  }

  @Override
  public void train(long trackingKey, int actual, Vector instance) {
    record++;
    int k = 0;
    for (OnlineLogisticRegression model : models) {
      if (k == trackingKey % models.size()) {
        Vector v = model.classifyFull(instance);
        double score = Math.max(v.get(actual), MIN_SCORE);
        logLikelihood += (Math.log(score) - logLikelihood) / Math.min(record, windowSize);

        int correct = v.maxValueIndex() == actual ? 1 : 0;
        percentCorrect += (correct - percentCorrect) / Math.min(record, windowSize);
        if (numCategories() == 2) {
          auc.addSample(actual, v.get(1));
        }
      } else {
        model.train(trackingKey, actual, instance);
      }
      k++;
    }
  }

  @Override
  public void close() {
    for (OnlineLogisticRegression m : models) {
      m.close();
    }
  }

  public void resetLineCounter() {
    record = 0;
  }

  public boolean validModel() {
    boolean r = true;
    for (OnlineLogisticRegression model : models) {
      r &= model.validModel();
    }
    return r;
  }

  // -------- classification methods

  @Override
  public Vector classify(Vector instance) {
    Vector r = new DenseVector(numCategories() - 1);
    BinaryFunction scale = Functions.plusMult(1.0 / models.size());
    for (OnlineLogisticRegression model : models) {
      r.assign(model.classify(instance), scale);
    }
    return r;
  }

  public Vector classifyNoLink(Vector instance) {
    Vector r = new DenseVector(numCategories() - 1);
    BinaryFunction scale = Functions.plusMult(1.0 / models.size());
    for (OnlineLogisticRegression model : models) {
      r.assign(model.classifyNoLink(instance), scale);
    }
    return r;
  }

  @Override
  public double classifyScalar(Vector instance) {
    double r = 0;
    int n = 0;
    for (OnlineLogisticRegression model : models) {
      n++;
      r += model.classifyScalar(instance);
    }
    return r / n;
  }

  // -------- status reporting methods
  
  @Override
  public int numCategories() {
    return models.get(0).numCategories();
  }

  public double auc() {
    return auc.auc();
  }

  public double logLikelihood() {
    return logLikelihood;
  }

  public double percentCorrect() {
    return percentCorrect;
  }

  // -------- evolutionary optimization

  public CrossFoldLearner copy() {
    CrossFoldLearner r = new CrossFoldLearner(models.size(), numCategories(), numFeatures, prior);
    r.models.clear();
    for (OnlineLogisticRegression model : models) {
      model.close();
      OnlineLogisticRegression newModel = new OnlineLogisticRegression(model.numCategories(), model.numFeatures(), model.prior);
      newModel.copyFrom(model);
      r.models.add(newModel);
    }
    return r;
  }

  public int getRecord() {
    return record;
  }

  public void setRecord(int record) {
    this.record = record;
  }

  public OnlineAuc getAuc() {
    return auc;
  }

  public void setAuc(OnlineAuc auc) {
    this.auc = auc;
  }

  public double getLogLikelihood() {
    return logLikelihood;
  }

  public void setLogLikelihood(double logLikelihood) {
    this.logLikelihood = logLikelihood;
  }

  public List<OnlineLogisticRegression> getModels() {
    return models;
  }

  public void addModel(OnlineLogisticRegression model) {
    models.add(model);
  }

  public double[] getParameters() {
    return parameters;
  }

  public void setParameters(double[] parameters) {
    this.parameters = parameters;
  }

  public int getNumFeatures() {
    return numFeatures;
  }

  public void setNumFeatures(int numFeatures) {
    this.numFeatures = numFeatures;
  }

  public void setWindowSize(int windowSize) {
    this.windowSize = windowSize;
  }

  public PriorFunction getPrior() {
    return prior;
  }

  public void setPrior(PriorFunction prior) {
    this.prior = prior;
  }
}
