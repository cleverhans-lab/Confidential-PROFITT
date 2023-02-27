package com.examples.jsatexamples;
import static java.lang.Math.*;
import java.io.IOException;
import java.util.*;
import java.io.File;
import jsat.DataSet;
import jsat.classifiers.CategoricalData;
import jsat.classifiers.CategoricalResults;
import jsat.classifiers.ClassificationDataSet;
import jsat.classifiers.DataPoint;
import jsat.classifiers.trees.DecisionStump;
import jsat.classifiers.trees.DecisionTree;
import jsat.classifiers.trees.ImpurityScore;
import jsat.classifiers.trees.TreeNodeVisitor;
import jsat.io.ARFFLoader;
import jsat.io.CSV;
import jsat.linear.Vec;
import jsat.parameters.Parameter;
import jsat.utils.*;
import jsat.utils.concurrent.AtomicDouble;
//import ImpScore;

import java.io.File;

public class rewriting {
    public static void main(String[] args) {
        //We specify '4' as the class we would like to make the target class.
        int label = 8;

        // Fair attribute is gender? race? TODO verify dataset features
        // seems to process categorical features first, we lateer define race as the second categorical, so it gets index 1.
        int fair_index = 0;

        // max of 50 random splits of the Compas dataset (in main/java/resources)
        int numTrials = 1;

        Double[] errorRate;
        errorRate = new Double[numTrials];

        Double[] discrim;
        discrim = new Double[numTrials];

        Double[] normDisp;
        normDisp = new Double[numTrials];

        for (int i = 0; i < numTrials; i++) {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            //File file = new File(classloader.getResource("compas" + (i+1) + ".arff").getFile());
            //DataSet dataSet = ARFFLoader.loadArffFile(file);
            //ClassificationDataSet cDataSet = new ClassificationDataSet(dataSet, label);
            // target is 8 (two year recidivism)
            File file = new File(classloader.getResource("compas.csv").getFile());

            // getting categorical data indicies
            Set<Integer> catCols = new TreeSet<>();
            Integer[] array = new Integer[]{0, 2, 7};  // race, sex, and c_charge_degree are categorical
            catCols.addAll(Arrays.asList(array));
            // init and build dataset
            ClassificationDataSet cDataSet = null;
            try {
                System.out.println(catCols.getClass().getName());
                cDataSet = CSV.readC(8, file.toPath(), 1, catCols);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(catCols.getClass().getName());
            }
            System.out.println("Dataset total size: " + cDataSet.size()); //7214
            //CategoricalData predicting = cDataSet.getPredicting();
            //Vec[] numerics = cDataSet.getNumericColumns();

            //cDataSet = ClassificationDataSet(numerics, predicting);
            System.out.println("Num features:" + cDataSet.getNumFeatures()); //8 + label

            //splitting
            List<ClassificationDataSet> ret = cDataSet.split(.75, .25);
            ClassificationDataSet cDataTrain = ret.get(0);
            ClassificationDataSet cDataTest = ret.get(1);
            System.out.println("Train size: " + cDataTrain.size());
            System.out.println("Test size: " + cDataTest.size());

            // getting all the features (indicies) into set options for splitting
            int[] options = new int[cDataSet.getNumNumericalVars()];
            // options is categorical indicies, then numerical indicies
            for (int j = 0; j < options.length; j++) {
                options[j] = j;
                System.out.println(options[j]);
            }
//            for (int j = cDataSet.getNumCategoricalVars(); j < cDataSet.getNumFeatures(); j++) {
//                options.add(j);
//                System.out.println(j);
//            }
            DecisionStump baseStump = new DecisionStump();
            DecisionStump stump = baseStump.clone();
            stump.setPredicting(cDataTrain.getPredicting());
            final List<ClassificationDataSet> leafs;
            leafs = trainCC(cDataTrain, fair_index, options, array);

            for (ClassificationDataSet leaf : leafs) {
                System.out.println(leaf.size());
                double[] priors = leaf.getPriors();
                for (int j=0; j< priors.length;j++) {
                    System.out.println(priors[j]);
                }

            }

//            for (int j=0; j<leafs.size(); j++) {
//                System.out.println(leafs[j].size());
//            }
//            final DecisionTree.Node node = new DecisionTree.Node(stump);
//            if (stump.getNumberOfPaths() > 1)//If there is 1 path, we are perfectly classifier - nothing more to do
//                for (int j = 0; j < node.paths.length; j++) {
//
//                }
//

        }
    }

    public static List<ClassificationDataSet> trainCC(ClassificationDataSet data, int fair_index, int[] options,
                                                      Integer[] array) {
        // recorders
        double bestGain = Double.NEGATIVE_INFINITY;
        int bestSplittingAtrribute;
        List<ClassificationDataSet> bestSplit = null;

        // gini impurity over entire node
        ImpScore ogScore = new ImpScore(data.getClassSize(), data.getCategories()[fair_index].getNumOfCategories());
        for (int i = 0; i < data.size(); i++) {
            double w = data.getWeight(i);
            int y = data.getDataPointCategory(i);
            int fair = data.getDataPoint(i).getCategoricalValue(fair_index);
            ogScore.addPoint(w, y, fair);
        }
        double original_gini_score = gini_score(ogScore);

        PairedReturn<Double, Double> bests = null; //will hold best split then best gain
        double gain = 0.0;
        // begin iterating over
        for (int k=0; k<options.length; k++) {
            int attribute = options[k];
            //System.out.println(attribute);
            List<ClassificationDataSet> aSplit = listOfLists(data, 2);
            // different process for categorical data (binning)
            if (attribute < array.length) {
                // categorical split, IGNORE
            } else {
                int N = data.getClassSize(); //predicting.getNumOfCategories();


                ImpScore[] split_scores = new ImpScore[2];
                gain = createNumericCSplit(data, N, attribute, aSplit, ogScore, split_scores, fair_index);
            }

            if (gain > bestGain) {
                bestGain = gain;
                bestSplittingAtrribute = attribute;
                bestSplit = aSplit;
            }
        }

//        if (splittingAttribute < catAttributes.length || removeContinuousAttributes) {
//            options.remove(splittingAttribute);
//        }

        return bestSplit;
    }
//PairedReturn<Double, Double>
    private static double createNumericCSplit(
            ClassificationDataSet data, int N, final int attribute,
            List<ClassificationDataSet> aSplit, ImpScore origScore, ImpScore[] subScores,
            int fair_index) {
        // recorders
        double bestGain = Double.NEGATIVE_INFINITY;
        double bestSplit = Double.NEGATIVE_INFINITY;
        int splitIndex = -1;

        int minResultSplits = 10; // their weird hyperparameter

        IntList workSet = new IntList(data.size()); // hold indexes of unsorted dset
        int nans = 0;
        double[] vals = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            //System.out.println(attribute);
            //for (int j=0; j<5; j++) {
                double val = data.getDataPoint(i).getNumericalValues().get(attribute);
                vals[i] = val;
                //System.out.println(val);
            //}

            workSet.add(i);
        }
        //System.out.println("nans number: " + nans);
        Collection<List<?>> paired = (Collection<List<?>>) (Collection<?>) Arrays.asList(workSet);
        QuickSort.sort(vals, 0, vals.length, paired);
        //sort the numeric values and put our original list of data points in the correct order at the same time

        // init left and right sides
        ImpScore leftSide = new ImpScore(N, data.getCategories()[fair_index].getNumOfCategories());
        ImpScore rightSide = origScore.clone();

        for (int i = 0; i < data.size()-1; i++) { //iterate over sorted attribute values
            int indx = workSet.getI(i); //get dataset (undsorted) index from sorted val array
            double w = data.getWeight(indx);
            int y = data.getDataPointCategory(indx);
            int fair = data.getDataPoint(indx).getCategoricalValue(fair_index);
            rightSide.removePoint(w, y, fair);
            leftSide.addPoint(w, y, fair);
            if (i >= minResultSplits) {
                double leftVal = vals[i];
                double rightVal = vals[i + 1];
                //get gain
                double curGain = gain(origScore, leftSide, rightSide);
                //System.out.println(curGain);
                //record gain stuff if best seen
                if (curGain >= bestGain) {
                    double curSplit = (leftVal + rightVal) / 2;
                    bestGain = curGain;
                    bestSplit = curSplit;

                    splitIndex = i + 1;
                    subScores[0] = leftSide.clone();
                    subScores[1] = rightSide.clone();
                }
            }
        }
        //make split
        ClassificationDataSet cds_left = data.emptyClone();
        ClassificationDataSet cds_right = data.emptyClone();
        for (int i : workSet.subList(0, splitIndex)) {
            cds_left.addDataPoint(data.getDataPoint(i), data.getDataPointCategory(i), data.getWeight(i));
        }
        for (int i : workSet.subList(splitIndex, workSet.size())) {
            cds_right.addDataPoint(data.getDataPoint(i), data.getDataPointCategory(i), data.getWeight(i));
        }
        //pass leafs out
        aSplit.set(0, cds_left);
        aSplit.set(1, cds_right);
        return bestGain;
        //PairedReturn<Double, Double> bests = new PairedReturn<>(bestSplit, bestGain);
        //return bests;
    }


    public static double gini_score(ImpScore dataScore) {
        double score = 1;

        for (double count : dataScore.counts) {
            double p = count / dataScore.weights;
            score -= p * p;
        }
        return abs(score / (1.0 - (1.0 / dataScore.counts.length)));
    }

    public static double fair_gini(ImpScore dataScore) {
        double score = 1;

        for (double count : dataScore.fairCounts) {
            double p = count / dataScore.weights;
            score -= p * p;
        }
        return abs(score / (1.0 - (1.0 / dataScore.fairCounts.length)));
    }

    public static double gain(ImpScore nodeScore, ImpScore... splits) {
        double sumOfAllSums = nodeScore.weights;
        double splitScore = 0.0;
        double gain;

        for (ImpScore split : splits) {
            double p = split.weights / sumOfAllSums;
            if (p <= 0)//log(0) is -Inft, so skip and treat as zero
                continue;
            splitScore += p * gini_score(split);
        }
        // entire nodeset data score - split score is the gain
        gain = gini_score(nodeScore) - splitScore;

        double fairSplitScore = 0.0;

        // GET FAIRNESS IMPURITY GAIN
        for (ImpScore split : splits) {
            double p = split.weights / sumOfAllSums;
            if (p <= 0)//log(0) is -Inft, so skip and treat as zero
                continue;
            fairSplitScore += p * fair_gini(split);
        }
        double fairGain = fair_gini(nodeScore) - fairSplitScore;
        return gain - fairGain;

    }

    private static <T extends DataSet<T>> List<T> listOfLists(T type, int n) {
        List<T> aSplit = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            aSplit.add((T) type.emptyClone());
        }
        return aSplit;
    }


//    protected static class Noode extends TreeNodeVisitor {
//        private static final long serialVersionUID = -7507748424627088734L;
//        final protected DecisionStump stump;
//        protected DecisionTree.Noode[] paths;
//
//        public Node(DecisionStump stump) {
//            this.stump = stump;
//            paths = new DecisionTree.Node[stump.getNumberOfPaths()];
//        }
//
//        @Override
//        public double getPathWeight(int path) {
//            return stump.pathRatio[path];
//        }
//
//        @Override
//        public boolean isLeaf() {
//            if (paths == null)
//                return true;
//            for (DecisionTree.Node path : paths) {
//                if (path != null) {
//                    return false;
//                }
//            }
//            return true;
//        }
//
//        @Override
//        public int childrenCount() {
//            return paths.length;
//        }
//
//        @Override
//        public CategoricalResults localClassify(DataPoint dp) {
//            return stump.classify(dp);
//        }
//
//        @Override
//        public double localRegress(DataPoint dp) {
//            return stump.regress(dp);
//        }
//
//        @Override
//        public DecisionTree.Node clone() {
//            DecisionTree.Node copy = new DecisionTree.Node((DecisionStump) this.stump.clone());
//            for (int i = 0; i < this.paths.length; i++)
//                copy.paths[i] = this.paths[i] == null ? null : this.paths[i].clone();
//
//            return copy;
//        }
//
//        @Override
//        public TreeNodeVisitor getChild(int child) {
//            if (isLeaf())
//                return null;
//            else
//                return paths[child];
//        }
//
//        @Override
//        public void setPath(int child, TreeNodeVisitor node) {
//            if (node instanceof DecisionTree.Node)
//                paths[child] = (DecisionTree.Node) node;
//            else
//                super.setPath(child, node);
//        }
//
//        @Override
//        public void disablePath(int child) {
//            paths[child] = null;
//        }
//
//        @Override
//        public int getPath(DataPoint dp) {
//            return stump.whichPath(dp);
//        }
//
//        @Override
//        public boolean isPathDisabled(int child) {
//            if (isLeaf())
//                return true;
//            return paths[child] == null;
//        }
//
//        @Override
//        public Collection<Integer> featuresUsed() {
//            IntList used = new IntList(1);
//            used.add(stump.getSplittingAttribute());
//            return used;
//        }
//    }

}

