package jsat.classifiers.trees;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsat.DataSet;
import jsat.classifiers.*;
import jsat.classifiers.trees.ImpurityScore.ImpurityMeasure;
import jsat.exceptions.FailedToFitException;
import jsat.linear.Vec;
import jsat.math.OnLineStatistics;
import jsat.parameters.Parameterized;
import jsat.regression.RegressionDataSet;
import jsat.regression.Regressor;
import jsat.utils.*;
import jsat.utils.concurrent.AtomicDouble;
import jsat.utils.concurrent.AtomicDoubleArray;
import jsat.utils.concurrent.ParallelUtils;

/**
 * This class is a 1-rule. It creates one rule that is used to classify all
 * inputs, making it a decision tree with only one node. It can be used as a
 * weak learner for ensemble learners, or as the nodes in a true decision tree.
 * <br><br>
 * Categorical values are handled similarly under all circumstances. <br>
 * During classification, numeric attributes are separated based on most likely
 * probability into their classes. <br>
 * During regression, numeric attributes are done with only binary splits,
 * finding the split that minimizes the total squared error sum. <br>
 * <br>
 * The Decision Stump supports missing values in training and prediction.
 *
 * @author Edward Raff
 */
public class DecisionStump implements Classifier, Regressor, Parameterized {

    private static final long serialVersionUID = -2849268862089019514L;
    
    /**
     * Indicates the fair attribute
     */
    private static int fairAttribute;

    /**
     * Indicates which attribute to split on
     */
    private int splittingAttribute;
    /**
     * Used only when trained for classification. Contains information about the
     * class being predicted
     */
    private CategoricalData predicting;
    /**
     * Contains the information about the attributes in the data set
     */
    private CategoricalData[] catAttributes;
    /**
     * The number of numeric features in the dataset that this Stump was trained
     * from
     */
    private int numNumericFeatures;
    /**
     * Used only in classification. Contains the numeric boundaries to split on
     */
    private List<Double> boundries;
    /**
     * Used only in classification. Contains the most likely class corresponding
     * to each boundary split
     */
    private List<Integer> owners;
    /**
     * Used only in classification. Contains the results for each of the split
     * options
     */
    private CategoricalResults[] results;
    /**
     * How much of the data went to each path
     */
    protected double[] pathRatio;
    /**
     * Only used during regression. Contains the averages for each branch in the
     * first and 2nd index. 3rd index contains the split value. If no split
     * could be done, the length is zero and it contains only the return value
     */
    private double[] regressionResults;
    private ImpurityMeasure gainMethod;
    private boolean removeContinuousAttributes;
    /**
     * The minimum number of points that must be inside the split result for a
     * split to occur.
     */
    private int minResultSplitSize = 10;

    /**
     * Creates a new decision stump
     */
    public DecisionStump() {
        gainMethod = ImpurityMeasure.GINI_DP;
        removeContinuousAttributes = false;
    }

    /**
     * Unlike categorical values, when a continuous attribute is selected to
     * split on, not all values of the attribute become the same. It can be
     * useful to split on the same attribute multiple times. If set true,
     * continuous attributes will be removed from the options list. Else, they
     * will be left in the options list.
     *
     * @param removeContinuousAttributes whether or not to remove continuous
     */
    public void setRemoveContinuousAttributes(boolean removeContinuousAttributes) {
        this.removeContinuousAttributes = removeContinuousAttributes;
    }
    
    public void setFairAttribute(int fairAttribute) {
        this.fairAttribute = fairAttribute;
    }

    public void setGainMethod(ImpurityMeasure gainMethod) {
        this.gainMethod = gainMethod;
    }

    public ImpurityMeasure getGainMethod() {
        return gainMethod;
    }

    /**
     *
     * @return The number of numeric features in the dataset that this Stump was
     * trained from
     */
    protected int numNumeric() {
        return numNumericFeatures;
    }

    /**
     *
     * @return the number of categorical features in the dataset that this Stump
     * was trained from.
     */
    protected int numCategorical() {
        return catAttributes.length;
    }

    /**
     * When a split is made, it may be that outliers cause the split to
     * segregate a minority of points from the majority. The min result split
     * size parameter specifies the minimum allowable number of points to end up
     * in one of the splits for it to be admissible for consideration.
     *
     * @param minResultSplitSize the minimum result split size to use
     */
    public void setMinResultSplitSize(int minResultSplitSize) {
        if (minResultSplitSize <= 1) {
            throw new ArithmeticException("Min split size must be a positive value ");
        }
        this.minResultSplitSize = minResultSplitSize;
    }

    /**
     * Returns the minimum result split size that may be considered for use as
     * the attribute to split on.
     *
     * @return the minimum result split size in use
     */
    public int getMinResultSplitSize() {
        return minResultSplitSize;
    }

    /**
     * Returns the attribute that this stump has decided to use to compute
     * results. Numeric features start from 0, and categorical features start
     * from the number of numeric features.
     *
     * @return the attribute that this stump has decided to use to compute
     * results.
     */
    public int getSplittingAttribute() {
        //TODO refactor the splittingAttribute to just be in this order already
        if (splittingAttribute < catAttributes.length)//categorical feature
        {
            return numNumericFeatures + splittingAttribute;
        }
        //else, is Numerical attribute
        int numerAttribute = splittingAttribute - catAttributes.length;
        return numerAttribute;
    }

    /**
     * Sets the DecisionStump's predicting information. This will be set
     * automatically by calling {@link #train(jsat.classifiers.ClassificationDataSet)
     * } or 
     *
     *
     * @param predicting the information about the attribute that will be
     * predicted by this classifier
     */
    public void setPredicting(CategoricalData predicting) {
        this.predicting = predicting;
    }


    @Override
    public double regress(DataPoint data) {
        if (regressionResults == null) {
            throw new RuntimeException("Decusion stump has not been trained for regression");
        }
        int path = whichPath(data);
        if (path >= 0) {
            return regressionResults[path];
        }
        //else, was missing, average
        double avg = 0;
        for (int i = 0; i < pathRatio.length; i++) {
            avg += pathRatio[i] * regressionResults[i];
        }
        return avg;
    }

    @Override
    public void train(RegressionDataSet dataSet, boolean parallel) {
        Set<Integer> options = new IntSet(dataSet.getNumFeatures());
        for (int i = 0; i < dataSet.getNumFeatures(); i++) {
            options.add(i);
        }
        List<RegressionDataSet> split = trainR(dataSet, options, parallel);
        if (split == null) {
            throw new FailedToFitException("Tree could not be fit, make sure your data is good. Potentially file a bug");
        }
    }

    /**
     * From the score for the original set that is being split, this computes
     * the gain as the improvement in classification from the original split.
     *
     * @param origScore the score of the unsplit set
     * @param source
     * @param aSplit the splitting of the data points
     * @return the gain score for this split
     */
    protected double[] getGain(ImpurityScore origScore, ClassificationDataSet source, List<IntList> aSplit) {

        ImpurityScore[] scores = getSplitScores(source, aSplit);

        return ImpurityScore.gain(origScore, scores);
    }

    private ImpurityScore[] getSplitScores(ClassificationDataSet source, List<IntList> aSplit) {
        ImpurityScore[] scores = new ImpurityScore[aSplit.size()];
        for (int i = 0; i < aSplit.size(); i++) {
            scores[i] = getClassGainScore(source, aSplit.get(i));
        }
        return scores;
    }

    /**
     * A value that is just above zero
     */
    private static final double almost0 = 1e-6;
    /**
     * A value that is just below one
     */
    private static final double almost1 = 1.0 - almost0;

    /**
     * Determines which split path this data point would follow from this
     * decision stump. Works for both classification and regression.
     *
     * @param data the data point in question
     * @return the integer indicating which path to take. -1 returned if stump
     * is not trained
     */
    public int whichPath(DataPoint data) {
        int paths = getNumberOfPaths();
        if (paths < 0) {
            return paths;//Not trained
        } else if (paths == 1)//ONLY one option, entropy was zero
        {
            return 0;
        } else if (splittingAttribute < catAttributes.length)//Same for classification and regression
        {
            return data.getCategoricalValue(splittingAttribute);
        }
        //else, is Numerical attribute - but regression or classification?
        int numerAttribute = splittingAttribute - catAttributes.length;
        double val = data.getNumericalValues().get(numerAttribute);
        if (Double.isNaN(val)) {
            return -1;//missing
        }
        if (results != null)//Categorical!
        {
            int pos = Collections.binarySearch(boundries, val);
            pos = pos < 0 ? -pos - 1 : pos;
            return owners.get(pos);
        } else//Regression! It is trained, it would have been grabed at the top if not
        {
            if (regressionResults.length == 1) {
                return 0;
            } else if (val <= regressionResults[2]) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    /**
     * Returns the number of paths that this decision stump leads to. The stump
     * may not ever direct a data point on some of the paths. A result of 1 path
     * means that all data points will be given the same decision, and is
     * generated when the entropy of a set is 0.0.
     * <br><br>
     * -1 is returned for an untrained stump
     *
     * @return the number of paths this decision stump has stored
     */
    public int getNumberOfPaths() {
        if (results != null)//Categorical!
        {
            return results.length;
        } else if (catAttributes != null)//Regression!
        {
            if (regressionResults.length == 1) {
                return 1;
            } else if (splittingAttribute < catAttributes.length)//Categorical
            {
                return catAttributes[splittingAttribute].getNumOfCategories();
            } else//Numerical is always binary
            {
                return 2;
            }
        }
        return Integer.MIN_VALUE;//Not trained!
    }

    @Override
    public CategoricalResults classify(DataPoint data) {
        if (results == null) {
            throw new RuntimeException("DecisionStump has not been trained for classification");
        }
        int path = whichPath(data);
        if (path >= 0) {
            return results[path];
        } else//missing value case, so average
        {
            Vec tmp = results[0].getVecView().clone();
            tmp.mutableMultiply(pathRatio[0]);
            for (int i = 1; i < results.length; i++) {
                tmp.mutableAdd(pathRatio[i], results[i].getVecView());
            }
            return new CategoricalResults(tmp.arrayCopy());
        }
    }

    @Override
    // if no splitter specified, roll with default raff
    public void train(ClassificationDataSet dataSet, boolean parallel) {
        Set<Integer> splitOptions = new IntSet(dataSet.getNumFeatures());
        for (int i = 0; i < dataSet.getNumFeatures(); i++) {
            splitOptions.add(i);
        }

        this.predicting = dataSet.getPredicting();
        Splitter splitter = new RaffSplitter();
        Object[] args = {1.0};
        trainC(dataSet, splitOptions, parallel, splitter, args);
    }

    /**
     * Returns the categorical result of the i'th path.
     *
     * @param i the path to get the result for
     * @return the result that would be returned if a data point went down the
     * given path
     * @throws IndexOutOfBoundsException if an invalid path is given
     * @throws NullPointerException if the stump has not been trained for
     * classification
     */
    public CategoricalResults result(int i) {
        if (i < 0 || i >= getNumberOfPaths()) {
            throw new IndexOutOfBoundsException("Invalid path, can to return a result for path " + i);
        }
        return results[i];
    }

    //@Override
    public void train(ClassificationDataSet dataSet, boolean parallel, Splitter splitter, Object... args) {
        Set<Integer> splitOptions = new IntSet(dataSet.getNumFeatures());
        for (int i = 0; i < dataSet.getNumFeatures(); i++) {
            splitOptions.add(i);
        }

        this.predicting = dataSet.getPredicting();

        trainC(dataSet, splitOptions, parallel, splitter, args);
    }

    /**
     * This is a helper function that does the work of training this stump. It
     * may be called directly by other classes that are creating decision trees
     * to avoid redundant repackaging of lists.
     *
     * @param dataPoints the lists of datapoint to train on, paired with the
     * true category of each training point
     * @param options the set of attributes that this classifier may choose
     * from. The attribute it does choose will be removed from the set. TODO will it really? or only categorical
     * @return the a list of lists, containing all the datapoints that would
     * have followed each path. Useful for training a decision tree
     */
    public PairedReturn<List<ClassificationDataSet>, Double> trainC(ClassificationDataSet dataPoints, Set<Integer> options, Splitter splitter, Object... args) {
        return trainC(dataPoints, options, false, splitter, args);
    }

    public PairedReturn<List<ClassificationDataSet>, Double> trainC(ClassificationDataSet dataPoints, Set<Integer> options) {
        Splitter splitter = new RaffSplitter();
        Object[] args = {1.0};
        return trainC(dataPoints, options, false, splitter, args);
    }

    public PairedReturn<List<ClassificationDataSet>, Double> trainC(final ClassificationDataSet data, Set<Integer> options,
                                                              boolean parallel, Splitter splitter, Object... args) {
        //TODO remove paths that have zero probability of occuring, so that stumps do not have an inflated branch value 
        if (predicting == null) {
            throw new RuntimeException("Predicting value has not been set");
        }

        // get scores for the whole dataset
        catAttributes = data.getCategories();
        numNumericFeatures = data.getNumNumericalVars();
        final ImpurityScore origScoreObj = getClassGainScore(data);
        double origScore = origScoreObj.getScore();
        int max_opt = Collections.max(options);
        //System.out.println("Orig score " + origScore);

        // Then all data points belond to the same category!
        if (origScore == 0.0 || data.size() < minResultSplitSize * 2)
        {
            results = new CategoricalResults[1];//Only one path! 
            results[0] = new CategoricalResults(predicting.getNumOfCategories());
            results[0].setProb(data.getDataPointCategory(0), 1.0);
            pathRatio = new double[]{0};
            List<ClassificationDataSet> toReturn = new ArrayList<>();
            toReturn.add(data);
            PairedReturn<List<ClassificationDataSet>, Double> toret = new PairedReturn(toReturn, Double.MAX_VALUE);
            return toret;
        }

        double[] bestAccGains = new double[max_opt+1];
        double[] bestFairGains = new double[max_opt+1];
        ArrayList<ImpurityScore[]> splits = new ArrayList<>(max_opt+1);
        List<List<ClassificationDataSet>> aSplits = new ArrayList<>(max_opt+1);
        List<List<Double>> bounds = new ArrayList<>(max_opt+10);
        owners = Arrays.asList(0, 1);
        for (int i=0; i<max_opt+1; i++) {
            ImpurityScore[] temp = new ImpurityScore[2];
            splits.add(temp);
            List<ClassificationDataSet> tempy = new ArrayList<>(2);
            List<Double> temp2 = new ArrayList<>(2);
            for (int j=0; j<2; j++) {
                temp2.add(-1.0);
                tempy.add(data); // TODO this is a pretty crappy way of initing a list of lists so maybe fix that
            }
            aSplits.add(tempy);
            bounds.add(temp2);
        }
        DoubleList bestRatio = new DoubleList();
        /*
         * The best attribute to split on
         */
        splittingAttribute = -1;

        final CountDownLatch latch = new CountDownLatch(max_opt+1);

        final ThreadLocal<ClassificationDataSet> localList = ThreadLocal.withInitial(() -> data.shallowClone());

        // Iterate over attributes
        for (final int attribute_to_consider : options) {
                try {
                    ClassificationDataSet DPs = localList.get();
                    int attribute = attribute_to_consider;
                    final double[] gainRet = new double[] {Double.NaN, Double.NaN};
                    List<ClassificationDataSet> aSplit;
                    PairedReturn<List<Double>, List<Integer>> tmp = null;//Used on numerical attributes

                    ImpurityScore[] split_scores = null;//used for cat
                    double weightScale = 1.0;

                    // Categorical Attribute
                    if (attribute < catAttributes.length)
                    {
                        //Create a list of lists to hold the split variables
                        aSplit = listOfLists(data, catAttributes[attribute].getNumOfCategories());
                        split_scores = new ImpurityScore[aSplit.size()];
                        for (int i = 0; i < split_scores.length; i++) {
                            split_scores[i] = new ImpurityScore(predicting.getNumOfCategories(), catAttributes[fairAttribute].getNumOfCategories(), gainMethod);
                        }

                        IntList wasMissing = new IntList();
                        double missingSum = 0.0;
                        //Now seperate the values in our current list into their proper split bins
                        for (int i = 0; i < data.size(); i++) {
                            int val = data.getDataPoint(i).getCategoricalValue(attribute);
                            double weight = data.getWeight(i);
                            if (val >= 0) {
                                aSplit.get(val).addDataPoint(data.getDataPoint(i), data.getDataPointCategory(i), weight);
                                // also considers fair attribute in addPoint
                                split_scores[val].addPoint(weight, data.getDataPointCategory(i), data.getDataPoint(i).getCategoricalValue(fairAttribute));
                            } else {
                                wasMissing.add(i);
                                missingSum += weight;
                            }

                        }
                        int pathsTaken = 0;
                        for (ClassificationDataSet split : aSplit) {
                            if (split.size() > 0) {
                                pathsTaken++;
                            }
                        }
                        if (missingSum > 0)//move missing values into others
                        {
                            double newSum = (origScoreObj.getSumOfWeights() - missingSum);
                            weightScale = newSum / origScoreObj.getSumOfWeights();
                            double[] fracs = new double[split_scores.length];
                            for (int i = 0; i < fracs.length; i++) {
                                fracs[i] = split_scores[i].getSumOfWeights() / newSum;
                            }

                            distributMissing(aSplit, fracs, data, wasMissing);
                        }
                    }

                    // Splitting on a numerical value
                    else {
                        attribute -= catAttributes.length;
                        int N = predicting.getNumOfCategories();

                        //Create a list of lists to hold the split variables
                        aSplit = listOfLists(data, 2);//Size at least 2
                        split_scores = new ImpurityScore[2];

                        tmp = createNumericCSplit(DPs, N, attribute, aSplit,
                                origScoreObj, gainRet, split_scores, splitter, args);
                        // split_scores holds best split ImpurityScores for that atribute
                        if (tmp == null) { // no good split
                             latch.countDown();
                             // skip this attr
                             continue;
                        } else {
                            bounds.set(attribute+catAttributes.length, tmp.getFirstItem());
                        }
                        //Fix it back so it can be used below
                        attribute += catAttributes.length;
                    }

                    //Now everything is seperated!
                    // Unpack gain and fair-gain values
                    double gain, fairGain;
                    double [] rets = ImpurityScore.gain(origScoreObj, weightScale, split_scores);
                    gain = rets[0];
                    fairGain = rets[1];
                    //synchronized (bestRatio) {
                    if (options.contains(attribute)) {
                        bestFairGains[attribute] = fairGain;
                        bestAccGains[attribute] = gain;
                    } else {
                        System.out.println("ayo whaaaat?");
                        bestFairGains[attribute] = Double.MAX_VALUE;
                        bestAccGains[attribute] = -Double.MAX_VALUE;
                    }
                    aSplits.set(attribute, aSplit);
                    if (split_scores == null) { // always false
                        split_scores = getClassGainScore(aSplit);//best split's classification dataset impurity scrore obj
                    }
                    splits.set(attribute, split_scores);
                    latch.countDown();

                } catch (Exception easx) {
                    easx.printStackTrace();
                    System.out.println();
                    latch.countDown();
                }
            //});
        } // end attribute loop
//        System.out.println(Arrays.toString(bestFairGains));
//        System.out.println(Arrays.toString(bestAccGains));
        // Splitter raffy = new RaffSplitter();
        Object[] new_args = new Object[10];

        if (splitter instanceof RaffSplitter) {
            new_args[0] = 0;
            new_args[1] = bestAccGains.length;
            new_args[2] = args[0]; // keeep the same lambda across values
        } else {
            System.arraycopy(args, 0, new_args, 0, args.length);
        }
        List<ClassificationDataSet> bestSplit = new ArrayList<>();
        //System.out.println(Arrays.toString(bestFairGains));
        splittingAttribute = splitter.getBestSplit(bestAccGains, bestFairGains, new_args);
        // System.out.println("SA: " + splittingAttribute);
        if (splittingAttribute == -1)//We could not find a good split at all
        {
            bestSplit.clear();
            bestSplit.add(data);
            CategoricalResults badResult = new CategoricalResults(data.getPriors());
            results = new CategoricalResults[]{badResult};
            pathRatio = new double[]{1};
            PairedReturn<List<ClassificationDataSet>, Double> toret = new PairedReturn(bestSplit, Double.MAX_VALUE);
            return toret;
        }
        try {
            ImpurityScore[] bestSplitScores = splits.get(splittingAttribute);
            double sum = 1e-8;
            for (ImpurityScore split_score : bestSplitScores) {
                sum += split_score.getSumOfWeights(); // TODO annoying raff error
                bestRatio.add(split_score.getSumOfWeights());
            }
        } catch (NullPointerException e) {
            // System.out.println("Bad SA");
            bestSplit.clear();
            bestSplit.add(data);
            CategoricalResults badResult = new CategoricalResults(data.getPriors());
            results = new CategoricalResults[]{badResult};
            pathRatio = new double[]{1};
            PairedReturn<List<ClassificationDataSet>, Double> toret = new PairedReturn(bestSplit, Double.MAX_VALUE);
            return toret;
        }
//        long startTime = System.nanoTime();
        while (!options.contains(splittingAttribute)) {
            //System.out.println("recomping splitting attr");
            bestFairGains[splittingAttribute] = Double.MAX_VALUE;
            bestAccGains[splittingAttribute] = Double.MAX_VALUE;
            splittingAttribute = splitter.getBestSplit(bestAccGains, bestFairGains, new_args);
        }
//        long endTime = System.nanoTime();
//        long duration = (endTime - startTime);
//        System.out.println("Annoying time: " + duration);
        double chosen_fair_gain = bestFairGains[splittingAttribute];
        // System.out.println("split attr: " + splittingAttribute);
        //System.out.println(options);

        List<ClassificationDataSet> aSplit = aSplits.get(splittingAttribute);

        bestSplit.addAll(aSplit);
        boundries = bounds.get(splittingAttribute);  // holds actual splitting values
        //System.out.println("x");
        // DoubleList bestRatio = new DoubleList();
        // gets child nodes impurity score info

        //System.out.println("xx");
        ImpurityScore[] bestSplitScores = splits.get(splittingAttribute);
        double sum = 1e-8;
        for (ImpurityScore split_score : bestSplitScores) {
            sum += split_score.getSumOfWeights(); // TODO annoying raff error
            bestRatio.add(split_score.getSumOfWeights());
        }
        for (int i = 0; i < bestSplitScores.length; i++) {
            bestRatio.set(i, bestRatio.getD(i) / sum);
        }
        //System.out.println("xxx");

        //boolean alive = false;
//        long startTime2 = System.nanoTime();
//        try {
//            //System.out.println("xxxx");
//
//            alive = latch.await(1L, TimeUnit.SECONDS);
//            //System.out.println("xxxxx");
//
//        } catch (InterruptedException ex1) {
//            Logger.getLogger(DecisionStump.class.getName()).log(Level.SEVERE, null, ex1);
//            throw new FailedToFitException(ex1);
//        }
//        long endTime2 = System.nanoTime();
//        long duration2 = (endTime2 - startTime2);
//        System.out.println("Latch cooldown: " + duration2);
//        if (!alive) {
//            InterruptedException ex1 = new InterruptedException();
//            throw new FailedToFitException(ex1);
//        }
        //System.out.println("xxxxxx");



        //System.out.println("xxxxxxx");

        if (splittingAttribute < catAttributes.length || removeContinuousAttributes) {
            options.remove(splittingAttribute);
        }
        results = new CategoricalResults[bestSplit.size()];
        pathRatio = bestRatio.getVecView().arrayCopy();
        //System.out.println("xxxxxxxx");

        for (int i = 0; i < bestSplit.size(); i++) {
            results[i] = new CategoricalResults(bestSplit.get(i).getPriors());
        }
        //System.out.println("got here");
        PairedReturn<List<ClassificationDataSet>, Double> toret = new PairedReturn(bestSplit, chosen_fair_gain);
        return toret;
    }

    /**
     *
     * @param dataPoints the original list of data points
     * @param N number of predicting target options
     * @param attribute the numeric attribute to try and find a split on
     * @param aSplit the list of lists to place the results of splitting in
     * @param origScore the score value for the data set we are splitting
     * @param finalGain array used to reference a double that can be returned.
     * If this method determined the gain in order to find the split, it sets
     * the value at index zero to the gain it computed. May be null, in which
     * case it is ignored.
     * @return A pair of lists of the same size. The list of doubles containing
     * the split boundaries, and the integers containing the path number.
     * Multiple splits could go down the same path.
     */
    private PairedReturn<List<Double>, List<Integer>> createNumericCSplit(
            ClassificationDataSet dataPoints, int N, final int attribute,
            List<ClassificationDataSet> aSplit, ImpurityScore origScore, double[] finalGain, ImpurityScore[] subScores,
            Splitter splitterMethod, Object... args) throws Exception {
        //TODO check remove point calls
        //cache misses are killing us, move data into a double[] to get more juice!
        double[] vals = new double[dataPoints.size()];//TODO put this in a thread local somewhere and re-use
        IntList workSet = new IntList(dataPoints.size());
        IntList wasNaN = new IntList();

        for (int i = 0; i < dataPoints.size(); i++) {
            double val = dataPoints.getDataPoint(i).getNumericalValues().get(attribute);
            if (!Double.isNaN(val)) {
                vals[i - wasNaN.size()] = val;
                workSet.add(i);  //stores dataset indicies
            } else {
                wasNaN.add(i);
            }
        }
        // System.out.println("WAS NAN " + wasNaN.size());

        if (workSet.size() < minResultSplitSize * 2)//Too many values were NaN for us to do any more splitting
        {
            return null;
        }

        //do what i want! Sort workSet based on "vals" array
        Collection<List<?>> paired = (Collection<List<?>>) (Collection<?>) Arrays.asList(workSet);
        QuickSort.sort(vals, 0, vals.length - wasNaN.size(), paired);
        //sort the numeric values and put our original list of data points in the correct order at the same time

//        double bestGain = Double.NEGATIVE_INFINITY;
//        double bestSplit = Double.NEGATIVE_INFINITY;
//        int splitIndex = -1;

        ImpurityScore rightSide = origScore.clone();
        ImpurityScore leftSide = new ImpurityScore(N, catAttributes[fairAttribute].getNumOfCategories(), gainMethod);
        //remove any Missing Value nodes from considering from the start 
        double nanWeightRemoved = 0;
        for (int i : wasNaN) {
            double weight = dataPoints.getWeight(i);
            int truth = dataPoints.getDataPointCategory(i);

            nanWeightRemoved += weight;
            rightSide.removePoint(weight, truth, dataPoints.getDataPoint(i).getCategoricalValue(fairAttribute));
        }
        double wholeRescale = rightSide.getSumOfWeights() / (rightSide.getSumOfWeights() + nanWeightRemoved);

        // set args for Splitter
        int min_i, max_i;
        min_i = minResultSplitSize;
        max_i = dataPoints.size() - minResultSplitSize - 1 - wasNaN.size();
        Object[] toPass = new Object[10];
        if (max_i <= min_i) {
            return null;
        }

        //for (Object arg : args) System.out.println(arg);

        if (splitterMethod instanceof RaffSplitter) {  // raff is special
            toPass[0] = min_i;
            toPass[1] = max_i;
            toPass[2] = args[0]; // copy in lambda
        } else { // for other splitters pass this stuff directly
            System.arraycopy(args, 0, toPass, 0, args.length);
        }


        double[] weights = new double[dataPoints.size()];
        int[] truths = new int[dataPoints.size()];
        int[] fairs = new int[dataPoints.size()];

        // fill leftside with min number points for splitting
        for (int i = 0; i < min_i; i++) {
            if (i >= dataPoints.size()) {
                System.out.println("WHAT?");
            }
            int indx = workSet.getI(i);
            weights[i] = dataPoints.getWeight(indx);
            truths[i] = dataPoints.getDataPointCategory(indx);
            fairs[i] = dataPoints.getDataPoint(indx).getCategoricalValue(fairAttribute);

            leftSide.addPoint(weights[i], truths[i], fairs[i]);
            rightSide.removePoint(weights[i], truths[i], fairs[i]);
        }

        double[] thresh_gains = new double[dataPoints.size()];
        double[] fair_thresh_gains = new double[dataPoints.size()];
        // int index = 0; // stores best seen split

        // iterate over splitting points
        for (int i = min_i; i < max_i; i++) {
            int indx = workSet.getI(i);
            weights[i] = dataPoints.getWeight(indx);
            truths[i] = dataPoints.getDataPointCategory(indx);
            fairs[i] = dataPoints.getDataPoint(indx).getCategoricalValue(fairAttribute);
            rightSide.removePoint(weights[i], truths[i], fairs[i]);
            leftSide.addPoint(weights[i], truths[i], fairs[i]);
            double leftVal = vals[i];
            double rightVal = vals[i + 1];
            if ((rightVal - leftVal) < 1e-14)//Values are too close!
            {
                continue;
            }

            double[] rets = ImpurityScore.gain(origScore, wholeRescale, leftSide, rightSide);
            double curUtilGain = rets[0];
            double curFairGain = rets[1];
            //System.out.println(Double.toString(curUtilGain)+", "+Double.toString(curFairGain));
            thresh_gains[i] = curUtilGain;
            fair_thresh_gains[i] = curFairGain;
            // compute final gain, store best seen
//            double curGain = curUtilGain - curFairGain;
//
//            if (curGain >= bestGain2) {
//                index = i;
//                double curSplit = (leftVal + rightVal) / 2;
//                bestGain2 = curGain;
//                bestSplit2 = curSplit;
//                splitIndex2 = i + 1;
//                subScores[0] = leftSide.clone();
//                subScores[1] = rightSide.clone();
//            }
        } // end splitting val loop

        int max_ind = splitterMethod.getBestSplit(thresh_gains, fair_thresh_gains, toPass);
        int splitIndex = max_ind + 1;
        // -1 is badbadnotgood
        splitIndex = max_ind == -1 ? -1 : splitIndex;
        if (splitIndex == -1) {
            return null;
        }
        double leftval = vals[max_ind];
        double rightval = vals[max_ind + 1];
        double bestSplit = (leftval + rightval) / 2.0;
        // returned via pointer for gain comparison across attributes
        //double bestGain = thresh_gains[max_ind] - fair_thresh_gains[max_ind];

        ImpurityScore left = new ImpurityScore(N, catAttributes[fairAttribute].getNumOfCategories(), gainMethod);
        //System.out.println("Clone Origin");

        ImpurityScore right = origScore.clone();
        left.addPoints(Arrays.copyOfRange(weights, 0, splitIndex), Arrays.copyOfRange(truths, 0, splitIndex),
                Arrays.copyOfRange(fairs, 0, splitIndex));
        right.removePoints(Arrays.copyOfRange(weights, 0, splitIndex), Arrays.copyOfRange(truths, 0, splitIndex),
                Arrays.copyOfRange(fairs, 0, splitIndex));
        // System.out.println(left.getSumOfWeights());
        // System.out.println(right.getSumOfWeights());

        //System.out.println("Clone L");
        subScores[0] = left.clone();
        //System.out.println("Clone R");
        subScores[1] = right.clone();

        if (finalGain != null) {
            //finalGain[0] = bestGain;
            finalGain[0] = thresh_gains[max_ind];
            finalGain[1] = fair_thresh_gains[max_ind];
        }
        ClassificationDataSet cds_left = dataPoints.emptyClone();
        ClassificationDataSet cds_right = dataPoints.emptyClone();
        for (int i : workSet.subList(0, splitIndex)) {
            cds_left.addDataPoint(dataPoints.getDataPoint(i), dataPoints.getDataPointCategory(i), dataPoints.getWeight(i));
        }
        for (int i : workSet.subList(splitIndex, workSet.size())) {
            cds_right.addDataPoint(dataPoints.getDataPoint(i), dataPoints.getDataPointCategory(i), dataPoints.getWeight(i));
        }

        aSplit.set(0, cds_left);
        aSplit.set(1, cds_right);
        if (wasNaN.size() > 0) {
            double weightScale = leftSide.getSumOfWeights() / (leftSide.getSumOfWeights() + rightSide.getSumOfWeights() + 0.0);
            distributMissing(aSplit, new double[]{weightScale, 1 - weightScale}, dataPoints, wasNaN);
        }
        PairedReturn<List<Double>, List<Integer>> tmp
                = new PairedReturn<>(
                        Arrays.asList(bestSplit, Double.POSITIVE_INFINITY),
                        Arrays.asList(0, 1));

        return tmp;
    }



    /**
     * Distributes a list of data points that had missing values to each split,
     * re-weighted by the indicated fractions
     *
     * @param <T>
     * @param splits a list of lists, where each inner list is a split
     * @param fracs the fraction of weight to each split, should sum to one
     * @param source
     * @param hadMissing the list of data points that had missing values
     */
    static protected <T> void distributMissing(List<ClassificationDataSet> splits, double[] fracs, ClassificationDataSet source, IntList hadMissing) {
        hadMissing.forEach((i) -> {
            DataPoint dp = source.getDataPoint(i);

            for (int j = 0; j < fracs.length; j++) {
                double nw = fracs[j] * source.getWeight(i);
                if (Double.isNaN(nw))//happens when no weight is available
                {
                    continue;
                }
                if (nw <= 1e-13) {
                    continue;
                }

                splits.get(j).addDataPoint(dp, source.getDataPointCategory(i), nw);
            }
        });
    }

    static protected <T> void distributMissing(List<RegressionDataSet> splits, double[] fracs, RegressionDataSet source, IntList hadMissing) {
        hadMissing.forEach((i) -> {
            DataPoint dp = source.getDataPoint(i);

            for (int j = 0; j < fracs.length; j++) {
                double nw = fracs[j] * source.getWeight(i);
                if (Double.isNaN(nw))//happens when no weight is available
                {
                    continue;
                }
                if (nw <= 1e-13) {
                    continue;
                }

                splits.get(j).addDataPoint(dp, source.getTargetValue(i), nw);
            }
        });
    }

    public List<RegressionDataSet> trainR(final RegressionDataSet dataPoints, Set<Integer> options) {
        return trainR(dataPoints, options, false);
    }

    public List<RegressionDataSet> trainR(final RegressionDataSet data, Set<Integer> options, boolean parallel) {
        catAttributes = data.getCategories();
        numNumericFeatures = data.getNumNumericalVars();
        //Not enough points for a split to occur
        if (data.size() <= minResultSplitSize * 2) {
            splittingAttribute = catAttributes.length;
            regressionResults = new double[1];
            double avg = 0.0;
            double sum = 0.0;
            for (int i = 0; i < data.size(); i++) {
                double weight = data.getWeight(i);
                avg += data.getTargetValue(i) * weight;
                sum += weight;
            }
            regressionResults[0] = avg / sum;

            List<RegressionDataSet> toRet = new ArrayList<>(1);
            toRet.add(data);
            return toRet;
        }

        final List<RegressionDataSet> bestSplit = new ArrayList<>();
        final AtomicDouble lowestSplitSqrdError = new AtomicDouble(Double.MAX_VALUE);

        final ThreadLocal<RegressionDataSet> localList = ThreadLocal.withInitial(() -> data.shallowClone());
        ExecutorService ex = parallel ? ParallelUtils.CACHED_THREAD_POOL : new FakeExecutor();
        final CountDownLatch latch = new CountDownLatch(options.size());
        for (int attribute_to_consider : options) {
            final int attribute = attribute_to_consider;
            ex.submit(()
                    -> {
                final RegressionDataSet DPs = localList.get();
                List<RegressionDataSet> thisSplit = null;
                //The squared error for this split
                double thisSplitSqrdErr = Double.MAX_VALUE;
                //Contains the means of each split
                double[] thisMeans = null;
                double[] thisRatio;

                if (attribute < catAttributes.length) {
                    thisSplit = listOfLists(DPs, catAttributes[attribute].getNumOfCategories());
                    OnLineStatistics[] stats = new OnLineStatistics[thisSplit.size()];
                    thisRatio = new double[thisSplit.size()];
                    for (int i = 0; i < thisSplit.size(); i++) {
                        stats[i] = new OnLineStatistics();
                    }
                    //Now seperate the values in our current list into their proper split bins
                    IntList wasMissing = new IntList();
                    for (int i = 0; i < DPs.size(); i++) {
                        int category = DPs.getDataPoint(i).getCategoricalValue(attribute);
                        if (category >= 0) {
                            thisSplit.get(category).addDataPoint(DPs.getDataPoint(i), DPs.getTargetValue(i), DPs.getWeight(i));
                            stats[category].add(DPs.getTargetValue(i), DPs.getWeight(i));
                        } else//was negative, missing value
                        {
                            wasMissing.add(i);
                        }
                    }
                    thisMeans = new double[stats.length];
                    thisSplitSqrdErr = 0.0;
                    double sum = 0;
                    for (int i = 0; i < stats.length; i++) {
                        sum += (thisRatio[i] = stats[i].getSumOfWeights());
                        thisSplitSqrdErr += stats[i].getVarance() * stats[i].getSumOfWeights();
                        thisMeans[i] = stats[i].getMean();
                    }
                    for (int i = 0; i < stats.length; i++) {
                        thisRatio[i] /= sum;
                    }

                    if (!wasMissing.isEmpty()) {
                        distributMissing(thisSplit, thisRatio, DPs, wasMissing);
                    }
                } else//Findy a binary split that reduces the variance!
                {
                    final int numAttri = attribute - catAttributes.length;

                    //2 passes, first to sum up the right side, 2nd to move down the grow the left side
                    OnLineStatistics rightSide = new OnLineStatistics();
                    OnLineStatistics leftSide = new OnLineStatistics();

                    //We need our list in sorted order by attribute!
                    DoubleList att_vals = new DoubleList(DPs.size());
                    IntList order = new IntList(DPs.size());
                    DoubleList weights = new DoubleList(DPs.size());
                    DoubleList targets = new DoubleList(DPs.size());
                    IntList wasNaN = new IntList();
                    for (int i = 0; i < DPs.size(); i++) {
                        double v = DPs.getDataPoint(i).getNumericalValues().get(numAttri);
                        if (Double.isNaN(v)) {
                            wasNaN.add(i);
                        } else {
                            rightSide.add(DPs.getTargetValue(i), DPs.getWeight(i));

                            att_vals.add(v);
                            order.add(i);
                            weights.add(DPs.getWeight(i));
                            targets.add(DPs.getTargetValue(i));
                        }
                    }
                    QuickSort.sort(att_vals.getBackingArray(), 0, att_vals.size(), Arrays.asList(order, weights, targets));

                    int bestS = 0;
                    thisSplitSqrdErr = Double.POSITIVE_INFINITY;

                    final double allWeight = rightSide.getSumOfWeights();
                    thisMeans = new double[3];
                    thisRatio = new double[2];

                    for (int i = 0; i < att_vals.size(); i++) {
                        double weight = weights.getD(i);
                        double val = targets.getD(i);
                        rightSide.remove(val, weight);
                        leftSide.add(val, weight);

                        if (i < minResultSplitSize) {
                            continue;
                        } else if (i > att_vals.size() - minResultSplitSize) {
                            break;
                        }

                        double tmpSVariance = rightSide.getVarance() * rightSide.getSumOfWeights()
                                + leftSide.getVarance() * leftSide.getSumOfWeights();
                        if (tmpSVariance < thisSplitSqrdErr && !Double.isInfinite(tmpSVariance))//Infinity can occur once the weights get REALY small
                        {
                            thisSplitSqrdErr = tmpSVariance;
                            bestS = i;
                            thisMeans[0] = leftSide.getMean();
                            thisMeans[1] = rightSide.getMean();
                            //Third spot contains the split value!
                            thisMeans[2] = (att_vals.get(bestS) + att_vals.get(bestS + 1)) / 2.0;
                            thisRatio[0] = leftSide.getSumOfWeights() / allWeight;
                            thisRatio[1] = rightSide.getSumOfWeights() / allWeight;
                        }
                    }

                    if (att_vals.size() >= minResultSplitSize) {
                        //Now we have the binary split that minimizes the variances of the 2 sets,
                        thisSplit = listOfLists(DPs, 2);
                        for (int i : order.subList(0, bestS)) {
                            thisSplit.get(0).addDataPoint(DPs.getDataPoint(i), DPs.getTargetValue(i), DPs.getWeight(i));
                        }
                        for (int i : order.subList(bestS + 1, order.size())) {
                            thisSplit.get(1).addDataPoint(DPs.getDataPoint(i), DPs.getTargetValue(i), DPs.getWeight(i));
                        }

                        if (wasNaN.size() > 0) {
                            distributMissing(thisSplit, thisRatio, DPs, wasNaN);
                        }
                    } else//not a good split, we can't trust it
                    {
                        thisSplitSqrdErr = Double.NEGATIVE_INFINITY;
                    }
                }

                //numerical issue check. When we get a REALLy good split, error can be a tiny negative value due to numerical instability. Check and swap sign if small
                if (Math.abs(thisSplitSqrdErr) < 1e-13)//no need to check sign, make simpler
                {
                    thisSplitSqrdErr = Math.abs(thisSplitSqrdErr);
                }
                //Now compare what weve done
                if (thisSplitSqrdErr >= 0 && thisSplitSqrdErr < lowestSplitSqrdError.get())//how did we get -Inf?
                {
                    synchronized (bestSplit) {
                        if (thisSplitSqrdErr < lowestSplitSqrdError.get()) {
                            lowestSplitSqrdError.set(thisSplitSqrdErr);
                            bestSplit.clear();
                            bestSplit.addAll(thisSplit);
                            splittingAttribute = attribute;
                            regressionResults = thisMeans;
                            pathRatio = thisRatio;
                        }
                    }
                }

                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ex1) {
            Logger.getLogger(DecisionStump.class.getName()).log(Level.SEVERE, null, ex1);
            throw new FailedToFitException(ex1);
        }

        //Removal of attribute from list if needed
        if (splittingAttribute < catAttributes.length || removeContinuousAttributes) {
            options.remove(splittingAttribute);
        }

        if (bestSplit.isEmpty())//no good option selected. Keep old behavior, return null in that case
        {
            return null;
        }
        return bestSplit;
    }

    private static <T extends DataSet<T>> List<T> listOfLists(T type, int n) {
        List<T> aSplit = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            aSplit.add((T) type.emptyClone());
        }
        return aSplit;
    }

    @Override
    public boolean supportsWeightedData() {
        return true;
    }

    private ImpurityScore getClassGainScore(ClassificationDataSet dataPoints, IntList subset) {
        ImpurityScore cgs = new ImpurityScore(predicting.getNumOfCategories(), catAttributes[fairAttribute].getNumOfCategories(), gainMethod);

        subset.forEach((i) -> {
            cgs.addPoint(dataPoints.getWeight(i), dataPoints.getDataPointCategory(i), dataPoints.getDataPoint(i).getCategoricalValue(fairAttribute));
        });

        return cgs;
    }

    private ImpurityScore[] getClassGainScore(List<ClassificationDataSet> splits) {
        ImpurityScore[] toRet = new ImpurityScore[splits.size()];
        for (int i = 0; i < toRet.length; i++) {
            toRet[i] = getClassGainScore(splits.get(i));
        }
        return toRet;
    }

    private ImpurityScore getClassGainScore(ClassificationDataSet dataPoints) {
        ImpurityScore cgs = new ImpurityScore(predicting.getNumOfCategories(), catAttributes[fairAttribute].getNumOfCategories(), gainMethod);

        for (int i = 0; i < dataPoints.size(); i++) {
            cgs.addPoint(dataPoints.getWeight(i), dataPoints.getDataPointCategory(i), dataPoints.getDataPoint(i).getCategoricalValue(fairAttribute));
        }

        return cgs;
    }

    @Override
    public DecisionStump clone() {
        DecisionStump copy = new DecisionStump();
        if (this.catAttributes != null) {
            copy.catAttributes = CategoricalData.copyOf(catAttributes);
        }
        if (this.results != null) {
            copy.results = new CategoricalResults[this.results.length];
            for (int i = 0; i < this.results.length; i++) {
                copy.results[i] = this.results[i].clone();
            }
        }
        copy.removeContinuousAttributes = this.removeContinuousAttributes;
        copy.splittingAttribute = this.splittingAttribute;
        if (this.boundries != null) {
            copy.boundries = new DoubleList(this.boundries);
        }
        if (this.owners != null) {
            copy.owners = new IntList(this.owners);
        }
        if (this.predicting != null) {
            copy.predicting = this.predicting.clone();
        }
        if (regressionResults != null) {
            copy.regressionResults = Arrays.copyOf(this.regressionResults, this.regressionResults.length);
        }
        if (pathRatio != null) {
            copy.pathRatio = Arrays.copyOf(this.pathRatio, this.pathRatio.length);
        }
        copy.minResultSplitSize = this.minResultSplitSize;
        copy.gainMethod = this.gainMethod;
        copy.numNumericFeatures = this.numNumericFeatures;
        return copy;
    }
}
