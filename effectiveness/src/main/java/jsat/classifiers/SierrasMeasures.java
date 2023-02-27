package jsat.classifiers;

import jsat.DataSet;
import jsat.classifiers.trees.DecisionTree;
import jsat.utils.IntSet;
import jsat.utils.PairedReturn;

import java.util.*;
import java.lang.Math;
import java.util.stream.IntStream;

/**
 * Gets a bunch of useful info about a model's performance.
 */
public class SierrasMeasures {
    ClassificationDataSet dset; //with labels still attached
    Classifier model;
    List<Integer> trues;
    List<Integer> preds;
    List<Integer> fairs;
    List<List<Integer>> groupPreds;
    List<List<Integer>> groupTrues;
    double[] groupSupport;
    int good_outcome;

    public SierrasMeasures(ClassificationDataSet dset, Classifier model, int fairAttribute, int good_outcome_label) {
        this.dset = dset;
        this.good_outcome = good_outcome_label;
        this.model = model;
        this.trues = getTrues();
        this.preds = getPreds();
        this.fairs = getFairs(fairAttribute);
        PairedReturn<List<List<Integer>>, List<List<Integer>>> tmp = separate_groups();
        this.groupPreds = tmp.getFirstItem();
        this.groupTrues = tmp.getSecondItem();
        this.groupSupport = group_support();

    }

    public List<Integer> getTrues() {
        List<Integer> truths = new ArrayList<>();
        for (int j = 0; j < dset.size(); j++) {
            truths.add(dset.getDataPointCategory(j));
        }
        return truths;
    }

    public List<Integer> getPreds() {
        List<Integer> preds = new ArrayList<>();
        for (int j = 0; j < dset.size(); j++) {
            DataPoint dataPoint = dset.getDataPoint(j);
            CategoricalResults predictionResults = model.classify(dataPoint);
            int predicted = predictionResults.mostLikely();
            preds.add(predicted);
        }
        return preds;
    }

    public List<Integer> getFairs(int fairAttribute) {
        List<Integer> fairs = new ArrayList<>();
        for (int j = 0; j < dset.size(); j++) {
            DataPoint dataPoint = dset.getDataPoint(j);
            fairs.add(dataPoint.getCategoricalValue(fairAttribute));
        }
        return fairs;
    }

    public double accuracy() {
        double[] conf_matr = confusion_matrix();
        double sum = sum(conf_matr);
        double correct = conf_matr[0] + conf_matr[2]; // tp and tn
        return correct/sum;
    }

    public double accuracy(double[] conf_matr) {
        double sum = sum(conf_matr);
        double correct = conf_matr[0] + conf_matr[2]; // tp and tn
        return correct/sum;
    }

    public double[] group_accuracy() {
        List<double[]> grouped_matrixs = group_confusion_matrix();
        double[] accs = new double[grouped_matrixs.size()];
        for (int i=0; i<accs.length; i++) {
            accs[i] = accuracy(grouped_matrixs.get(i));
        }
        return accs;
    }

    public double accuracy_gap() {
        double[] grp_accs = group_accuracy();
        if (groupSupport[0] > groupSupport[1]) {
            return grp_accs[0] - grp_accs[1];
        }
        return grp_accs[1] - grp_accs[0];
    }

    public double precision() {
        double[] matrix = confusion_matrix();
        double num = matrix[0];
        double denom = matrix[0] + matrix[1];
        return num / denom;
    }

    public double precision(double[] matrix) {
        double num = matrix[0];
        double denom = matrix[0] + matrix[1];
        return num / denom;
    }

    public double[] group_precision() {
        List<double[]> grouped_matrixs = group_confusion_matrix();
        double[] pre = new double[grouped_matrixs.size()];
        for (int i=0; i<pre.length; i++) {
            pre[i] = precision(grouped_matrixs.get(i));
        }
        return pre;
    }



    /**
     * Majority group - minority group demo parity
     * @return
     */
    public double demographic_parity_difference() {
        List<double[]> matrs = group_confusion_matrix();
        double[] grp1 = matrs.get(0);
        double val1 = (grp1[0] + grp1[1]) / sum(grp1);
        double[] grp2 = matrs.get(1);
        double val2 = (grp2[0] + grp2[1]) / sum(grp2);
        // ensure getting majority - minority group value
        if (groupSupport[0] > groupSupport[1]) {
            return val1 - val2;
        }
        return val2 - val1;
    }

    /**
     * Minority/Majority group demo parity ratio
     * @return
     */
    public double demographic_parity_ratio() {
        List<double[]> matrs = group_confusion_matrix();
        double[] grp1 = matrs.get(0);
        double val1 = (grp1[0] + grp1[1]) / sum(grp1);
        double[] grp2 = matrs.get(1);
        double val2 = (grp2[0] + grp2[1]) / sum(grp2);
        // ensure getting mino/major group value
        if (groupSupport[0] > groupSupport[1]) {
            return val2 / val1;
        } else {
            return val1 / val2;
        }
    }

    /**
     * https://fairlearn.org/v0.7.0/api_reference/fairlearn.metrics.html
     * Majority group - minority group eodds
     * @return
     */
    public double equalized_odds_difference() {
        List<double[]> matrs = group_confusion_matrix();
        double[] grp1 = matrs.get(0);
        double[] grp2 = matrs.get(1);
        double tpr1 = grp1[0] / (grp1[0] + grp1[3]);
        double tpr2 = grp2[0] / (grp2[0] + grp2[3]);
        double fpr1 = grp1[1] / (grp1[1] + grp1[2]);
        double fpr2 = grp2[1] / (grp2[1] + grp2[2]);
        double val1, val2;
        if (Math.abs(tpr1 - tpr2) > Math.abs(fpr1 - fpr2)) {
            val1 = tpr1;
            val2 = tpr2;
        } else {
            val1 = fpr1;
            val2 = fpr2;
        }
        // ensure getting majority - minority group value
        if (groupSupport[0] > groupSupport[1]) {
            return val1 - val2;
        }
        return val2 - val1;
    }

    /**
     * https://fairlearn.org/v0.7.0/api_reference/fairlearn.metrics.html
     * Minority/Majority group demo parity ratio
     * @return
     */
    public double equalized_odds_ratio() {
        List<double[]> matrs = group_confusion_matrix();
        double[] grp1 = matrs.get(0);
        double[] grp2 = matrs.get(1);
        double tpr1 = grp1[0] / (grp1[0] + grp1[3]);
        double tpr2 = grp2[0] / (grp2[0] + grp2[3]);
        double fpr1 = grp1[1] / (grp1[1] + grp1[2]);
        double fpr2 = grp2[1] / (grp2[1] + grp2[2]);
        double tpr, fpr;

        if (groupSupport[0] < groupSupport[1]) {
            fpr = fpr1/fpr2;
            tpr = tpr1/tpr2;
        } else {
            fpr = fpr2/fpr1;
            tpr = tpr2/tpr1;
        }
        if (tpr > fpr)
            return fpr;
        return tpr;
    }

    public double[] group_support() {
        double[] support = new double[groupTrues.size()];
        List<double[]> matrs = group_confusion_matrix();
        for (int i=0; i<support.length; i++) {
            support[i] = sum(matrs.get(i));
        }
        return support;
    }

    /**
     *
     * @return TP, FP, TN, FN
     */
    public double[] confusion_matrix() {
        return confusion_matrix(preds, trues);
    }

    public double[] confusion_matrix(List<Integer> preds, List<Integer> trues) {
        double tp = 0, fp = 0, tn = 0, fn = 0;
        for (int i=0; i<preds.size(); i++) {
            if (preds.get(i) == this.good_outcome && trues.get(i)==this.good_outcome)
                tp++;
            else if (preds.get(i) != this.good_outcome && trues.get(i)==this.good_outcome)
                fn++;
            else if (preds.get(i) == this.good_outcome && trues.get(i)!=this.good_outcome)
                fp++;
            else if (preds.get(i) !=this.good_outcome && trues.get(i)!=this.good_outcome)
                tn++;
        }
        return new double[]{tp, fp, tn, fn};
    }

    public List<double[]> group_confusion_matrix() {
        List<double[]> toret = new ArrayList<>();
        for (int i=0; i<groupTrues.size(); i++) {
            double[] matrix = confusion_matrix(groupPreds.get(i), groupTrues.get(i));
            // System.out.println(Arrays.toString(matrix));
            toret.add(matrix);
        }
        return toret;
    }

    public PairedReturn<List<List<Integer>>, List<List<Integer>>> separate_groups() {
        int num_groups = countUniques(fairs);
        // return num_groups;
        List<List<Integer>> sep_preds = new ArrayList<>();
        List<List<Integer>> sep_trues = new ArrayList<>();
        for (int i=0; i<num_groups; i++) { // for each fairness group
            List<Integer> group_preds = new ArrayList<>();
            List<Integer> group_trues = new ArrayList<>();
            for (int j=0; j<preds.size(); j++) { //iterate over preds and trues
                if (fairs.get(j) == i) { // if datapoint in group, add it
                    group_preds.add(preds.get(j));
                    group_trues.add(trues.get(j));
                }
            }
            sep_preds.add(group_preds);
            sep_trues.add(group_trues);
        }
        return new PairedReturn<>(sep_preds, sep_trues);
    }

    public static double sum(double[] arr)
    {
        double sum = 0; // initialize sum
        int i;

        // Iterate through all elements and add them to sum
        for (i = 0; i < arr.length; i++)
            sum +=  arr[i];

        return sum;
    }

    public static int countUniques(List<Integer> arr) {
        Set<Integer> seen = new IntSet();
        for (int elem : arr) {
            boolean contains = seen.contains(elem);
            if (!contains) {
                seen.add(elem);
            }
        }
        return seen.size();
    }
}
