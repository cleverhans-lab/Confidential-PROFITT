package com.examples.jsatexamples;

import java.io.File;

import jsat.classifiers.*;
import jsat.classifiers.trees.*;
import jsat.io.ARFFLoader;
import jsat.DataSet;

import static java.lang.Math.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import jsat.classifiers.trees.TreePruner.PruningMethod;
import jsat.io.CSV;
import jsat.utils.PairedReturn;


public class run
{
    public static void main(String[] args) throws Exception {
//        debugging(args);
        fair_run(args);
    }

    public static void debugging(String[] args) throws FileNotFoundException {
        int label = 7;//22; // default paymnet
        int fairAttribute = 5; //23; // young
//        label = 1;
//        fairAttribute = 0;
        float threshold = (float) 1;
        ImpurityScore.ImpurityMeasure eodds = ImpurityScore.ImpurityMeasure.GINI_EODDS;
        ImpurityScore.ImpurityMeasure dp = ImpurityScore.ImpurityMeasure.GINI_DP;
        Splitter splitter = new PercentSplitter();
        String IGS_fname =  "~/IGS_throwaway.txt";
        // Splitter splitter = new ThresholdSplitter();
        //runme("adult", "test", 6, fairAttribute, label, threshold, dp, splitter, IGS_fname);
    }

    public static void fair_run(String[] a) throws Exception {
        String fair = a[0];
        String method = a[1];
        String date = a[2];
        int nseeds = Integer.parseInt(a[3]);
        int nsteps = Integer.parseInt(a[4]);
        double step = Double.parseDouble(a[5]);
        String dset = a[6];
		//String testtrain = a[7];
        String baseline = a[7];

        int label, fairAttribute;
        if (Objects.equals(dset, "COMPAS")) {
            label = 8; // two_year_recid
            fairAttribute = 2; // race
        } else if (Objects.equals(dset, "CrimeCommunity")) {
            label = 1; // high crime?
            fairAttribute = 0; // Afrcan american binarizes
        } else if (Objects.equals(dset, "default_credit")) {
            // categorical features are indexed before numeric
            label = 8;
            fairAttribute = 9;
        } else if (Objects.equals(dset, "adult")) {
            fairAttribute = 1;
            label = 0;
        } else if (dset.equals("diabetes")) {
            fairAttribute = 1;
            label = 0;
        } else {
            throw new Exception("Invalid data specifier (COMPAS or Crime or default_credit or adult)");
        }
        ImpurityScore.ImpurityMeasure eodds = ImpurityScore.ImpurityMeasure.GINI_EODDS;
        ImpurityScore.ImpurityMeasure dp = ImpurityScore.ImpurityMeasure.GINI_DP;
        ImpurityScore.ImpurityMeasure fairness;
        if (Objects.equals(fair, "EODDS")) {
            fairness = eodds;
        } else {
            fairness = dp;
        }

        Splitter splitter;
        switch (method) {
            case "lambda":
                splitter = new RaffSplitter();
                break;
            case "perc":
                splitter = new PercentSplitter(); //perc with 1.0 is no fairness
                break;
            case "thresh":
                splitter = new ThresholdSplitter();
                break;
            default:
                throw new Exception("Invalid splitter method (lambda, perc, thresh)");
        }

        double thresh = 0;
        int count = 0;
        String IGS_fname, IGS_train_fname, test_raws, train_raws;
        if (baseline.equals("baseline")) {
            IGS_train_fname = "./results/"+dset+"_"+fair+"/baseline_train_"+date+"_"+nseeds+"_"+method+"/"+"IGS"+".txt";
            IGS_fname = "./results/"+dset+"_"+fair+"/baseline_"+date+"_"+nseeds+"_"+method+"/"+"IGS"+".txt";
            File file = new File(IGS_fname);
            file.createNewFile();
            File trainFile = new File(IGS_train_fname);
            trainFile.createNewFile();
            thresh = 1.0;

            test_raws = "./raw_txts/"+dset+"_"+fair+"/baseline_"+date+"_"+nseeds+"_"+method+"/"+count+".txt";
            train_raws = "./raw_txts/"+dset+"_"+fair+"/baseline_train_"+date+"_"+nseeds+"_"+method+"/"+count+".txt";
        } else {
            IGS_train_fname = "./results/"+dset+"_"+fair+"/train_"+date+"_"+nseeds+"_"+method+"/"+"IGS"+".txt";
            IGS_fname = "./results/"+dset+"_"+fair+"/"+date+"_"+nseeds+"_"+method+"/"+"IGS"+".txt";
            File file = new File(IGS_fname);
            file.createNewFile();
            File trainFile = new File(IGS_train_fname);
            trainFile.createNewFile();

            test_raws = "./raw_txts/"+dset+"_"+fair+"/"+date+"_"+nseeds+"_"+method+"/"+count+".txt";
            train_raws = "./raw_txts/"+dset+"_"+fair+"/train_"+date+"_"+nseeds+"_"+method+"/"+count+".txt";
        }

        while (thresh <= step*nsteps) {
            //String fname = "./raw_txts/"+dset+"_"+fair+"/"+dateme+"_"+nseeds+"_"+method+"/"+count+".txt";
            StringBuilder towriteTest = new StringBuilder();
            StringBuilder towriteTrain = new StringBuilder();
            for (int i = 0; i < nseeds; i++) {
                towriteTest.append(i).append("\n");
                towriteTest.append(thresh).append("\n");
                towriteTrain.append(i).append("\n");
                towriteTrain.append(thresh).append("\n");
                PairedReturn<String, String> tmp = runme(dset, "", i, fairAttribute, label, (float) thresh, fairness, splitter,
                        IGS_fname);
                towriteTest.append(tmp.getSecondItem());
                towriteTrain.append(tmp.getFirstItem());
            }
            PrintWriter test_out = new PrintWriter(test_raws);
            test_out.println(towriteTest);
            test_out.close();
            PrintWriter train_out = new PrintWriter(train_raws);
            train_out.println(towriteTrain);
            train_out.close();
            thresh += step;
            count ++;
        }
    }

    public static void baseline_run(String[] a) throws FileNotFoundException {
        int numTrials = 5;
        int label = 8; // two_year_recid
        int fairAttribute = 2; // race
        ImpurityScore.ImpurityMeasure eodds = ImpurityScore.ImpurityMeasure.GINI_EODDS;
        ImpurityScore.ImpurityMeasure dp = ImpurityScore.ImpurityMeasure.GINI_DP;

        StringBuilder towrite = new StringBuilder();
        towrite.append(0).append("\n");
        towrite.append(1.0).append("\n");
        Splitter raff = new RaffSplitter();
        String IGS_fname = "~/IGS_throwaway.txt";
        towrite.append(runme("COMPAS","test", 0, fairAttribute, label, 1, eodds, raff, IGS_fname));
        String fname = "./raw_txts/COMPAS_EODDS/6-8_baseline/0.txt";
        PrintWriter out = new PrintWriter(fname);
        out.println(towrite);
        out.close();
    }


    /**
     * Trains and inferences.
     * @param dset COMPAS or Crime
     * @param i seed
     * @param fairAttribute attribute for fair groups
     * @param label label
     * @param threshold threshold for perc/raff/thresh splitter methods
     * @param impurityMeasure gini for DP or EODDS
     * @param splitter Splitter method (perc, raff, thresh)
     * @return String of true labels, predicted labels, and fair categories for testing set
     */
    public static PairedReturn<String, String> runme(String dset, String testtrain, int i, int fairAttribute, int label, float threshold,
                               ImpurityScore.ImpurityMeasure impurityMeasure, Splitter splitter, String IGS_fname) {
        // load data
        ClassificationDataSet cDataSet = null;
        int maxDepth = 0;
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        if (dset.equals("COMPAS")) {
            maxDepth = 6;
            File file = new File(Objects.requireNonNull(classloader.getResource("compas" + (i + 1) + ".csv")).getFile());
            Integer[] array = new Integer[]{0, 2, 7, 8};  // sex, race, c_charge_degree, two_year_recid are categorical
            Set<Integer> catCols = new TreeSet<>(Arrays.asList(array));
            try {
                cDataSet = CSV.readC(label, file.toPath(), 1, catCols);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(catCols.getClass().getName());
            }
        } else if (dset.equals("CrimeCommunity")) {
            maxDepth = 4;
            File file = new File(classloader.getResource("crimecommunity" + (i + 1) + ".arff").getFile());
            //System.out.println("131 " + file);
            DataSet dataSet = ARFFLoader.loadArffFile(file);
            cDataSet = new ClassificationDataSet(dataSet, label);
        }   else if (dset.equals("default_credit")) {
            maxDepth = 10;
            File file = new File(Objects.requireNonNull(classloader.getResource("default_credit" + (i + 1) + ".csv")).getFile());
            // sex, education, marraige, PAY 4_9, default payement, young
            Integer[] array = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 22, 23}; //22 is label
            Set<Integer> catCols = new TreeSet<>(Arrays.asList(array));
            try {
                cDataSet = CSV.readC(label, file.toPath(), 1, catCols);
//                System.out.println(cDataSet.getNumCategoricalVars());
//                System.out.println(cDataSet.getNumNumericalVars());
//                System.out.println(cDataSet.getDataPoint(0));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (dset.equals("adult")) {
            maxDepth = 10;
            File file = new File(Objects.requireNonNull(classloader.getResource("adult" + (i + 1) + ".csv")).getFile());
            // 1 workclass 3 education 5 marital_status 6 occupation 7 relationship 8 race 9 gender SENSITIVE  13 native_country 14 income LABEL
            Integer[] array = new Integer[]{0, 1, 3, 5, 7, 8, 9, 10, 14};
            Set<Integer> catCols = new TreeSet<>(Arrays.asList(array));
            try {
                cDataSet = CSV.readC(label, file.toPath(), 1, catCols);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(catCols.getClass().getName());
            }
        } else if (dset.equals("diabetes")) {
            maxDepth = 20;
            File file = new File(Objects.requireNonNull(classloader.getResource("diabetes" + (i + 1) + ".csv")).getFile());
            // 1 workclass 3 education 5 marital_status 6 occupation 7 relationship 8 race 9 gender SENSITIVE  13 native_country 14 income LABEL
            Integer[] array = new Integer[]{0, 1, 2, 4, 5, 6, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
                    29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,40, 41, 42, 43, 44};
            Set<Integer> catCols = new TreeSet<>(Arrays.asList(array));
            try {
                cDataSet = CSV.readC(label, file.toPath(), 1, catCols);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(catCols.getClass().getName());
            }
        }

//        System.out.println(cDataSet.getNumNumericalVars());
//        System.out.println(cDataSet.getNumCategoricalVars());
//        System.out.println(cDataSet.getDataPoint(0));
        // split data
        List<ClassificationDataSet> ret = cDataSet.split(.75, .25);
        ClassificationDataSet cDataTrain = ret.get(0);
        ClassificationDataSet cDataTest = ret.get(1);
        boolean parallel = false;

        // build tree
         DecisionTree classifier = new DecisionTree(maxDepth, 1, fairAttribute, PruningMethod.NONE,
               0, impurityMeasure);
        Object[] splitter_args = {(double) threshold};
        long startTime = System.currentTimeMillis();
        classifier.train(cDataTrain, parallel, splitter, IGS_fname, splitter_args);
        long endTime = System.currentTimeMillis();
        System.out.println("HI "+(endTime - startTime));


        //build r_forest
//        int max_forest = 10;
//        RandomForest classifier = new RandomForest(max_forest, maxDepth, fairAttribute, impurityMeasure);
//        //IGS_fname = "~/IGS_throwaway.txt";
//        Object[] splitter_args = {(double) threshold};
//        int seed = i + 1;  // match rf seed with dataset seed
//        classifier.train(cDataTrain, parallel, seed, splitter, IGS_fname, splitter_args);

        // get predictions
        int favourable_label = 0;
        String train, test;
        // test predictions
        SierrasMeasures measurer = new SierrasMeasures(cDataTest, classifier, fairAttribute, favourable_label);
        test = get_performances(measurer, fairAttribute);
        // train predictions
        SierrasMeasures measurerTrain = new SierrasMeasures(cDataTrain, classifier, fairAttribute, favourable_label);
        train = get_performances(measurerTrain, fairAttribute);

        return new PairedReturn<>(train, test);
    }


    public static String get_performances(SierrasMeasures measurer, int fairAttribute) {
        String trues = String.valueOf(measurer.getTrues());
        String preds = String.valueOf(measurer.getPreds());
        String fairs = String.valueOf(measurer.getFairs(fairAttribute));
        String toreturn = trues + "\n" + preds + "\n" + fairs + "\n";
        //return toreturn;

//        System.out.println("TP, FP, TN, FN: " + Arrays.toString(measurer.confusion_matrix()));
//        System.out.println("Grp 1 conf matrix: " + Arrays.toString(measurer.group_confusion_matrix().get(0)));
//        System.out.println("Grp 2 conf matrix: " + Arrays.toString(measurer.group_confusion_matrix().get(1)));
//        System.out.println("Supports: " + Arrays.toString(measurer.group_support()));
////        System.out.println(measurer.getFairs(fairAttribute));
////        System.out.println(measurer.getTrues());
//        double[] grp_accs = measurer.group_accuracy();
////        System.out.println("Entire accuracy: " + measurer.accuracy());
//        System.out.println("Grp accuracies: " + Arrays.toString(grp_accs));
////        System.out.println("Acc gap: " + measurer.accuracy_gap());
//        System.out.println("EODDS diff: " + measurer.equalized_odds_difference());
//        System.out.println("EODDS ratio: " + measurer.equalized_odds_ratio());
//        System.out.println("DP diff: " + measurer.demographic_parity_difference());
//        System.out.println("DP ratio: " + measurer.demographic_parity_ratio());
//        System.out.println("---------------------------");

           return toreturn;
    }

    public static double sum(Double[] arr)
    {
        double sum = 0; // initialize sum
        int i;

        // Iterate through all elements and add them to sum
        for (i = 0; i < arr.length; i++)
            sum +=  arr[i];

        return sum;
    }

    public static int sum(int[] arr)
    {
        int sum = 0; // initialize sum
        int i;

        // Iterate through all elements and add them to sum
        for (i = 0; i < arr.length; i++)
            sum +=  arr[i];

        return sum;
    }

    /**
     *
     * @param labelPredict predicted labels
     * @param fair_attribute index of fair attribute
     * @param protected_race 0, african american, 1 asian, 2 white, 3 hispanic, 4 native american, 5 other
     * @return discrimination wrt given race group
     */
    public static double discrimination(int[] labelPredict, int[] fair_attribute, int protected_race)
    {
        //int protected_race = 0; // 0, african american, 1 asian, 2 white, 3 hispanic, 4 native american, 5 other
        int num_c1 = 0; // num protected race group (fair attribute val = 0)
        int num_c2 = 0; // num other races
        //int num_c2 = sum(fair_attribute);
        for (int i = 0; i< fair_attribute.length; i++) {
            if (fair_attribute[i] == protected_race) { //
                num_c1++;
            } else {
                num_c2++;
            }
        }
        //int num_c1 = fair_attribute.length - num_c2;

        int sum_pred_c1 = 0;
        int sum_pred_c2 = 0;

        for(int i = 0; i < labelPredict.length; i++)
        {
            if(fair_attribute[i] == protected_race)
            {
                sum_pred_c1 += labelPredict[i];
            }
            else
            {
                sum_pred_c2 += labelPredict[i];
            }
        }

        return abs((double)sum_pred_c1/num_c1 - (double)sum_pred_c2/num_c2);
    }

    public static double normedDisparate(int[] labelPredict, int[] fair_attribute, int protected_race)
    {
        //int protected_race = 0; // 0, african american, 1 asian, 2 white, 3 hispanic, 4 native american, 5 other
        int num_c1 = 0; // num protected race group (fair attribute val = 0)
        int num_c2 = 0; // num other races
        //int num_c2 = sum(fair_attribute);
        for (int i = 0; i< fair_attribute.length; i++) {
            if (fair_attribute[i] == protected_race) { //
                num_c1++;
            } else {
                num_c2++;
            }
        }
        //int num_c1 = fair_attribute.length - num_c2;
        int sum_pred_c1 = 0;
        int sum_pred_c2 = 0;

        for(int i = 0; i < labelPredict.length; i++)
        {
            if(fair_attribute[i] == protected_race)
            {
                sum_pred_c1 += labelPredict[i];
            }
            else
            {
                sum_pred_c2 += labelPredict[i];
            }
        }
        if ((double)sum_pred_c1/num_c1 == (double)sum_pred_c2/num_c2)
        {
            return 1;
        }

        if ((double)sum_pred_c1/num_c1 < (double)sum_pred_c2/num_c2)
        {
            return 1 - (double)((double)sum_pred_c1/num_c1) / ((double)sum_pred_c2/num_c2);
        }
        else
        {
            return 1 - (double)((double)sum_pred_c2/num_c2) / ((double)sum_pred_c1/num_c1);
        }
    }
}

