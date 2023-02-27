package com.examples.jsatexamples;

import jsat.classifiers.trees.PercentSplitter;
import jsat.classifiers.trees.ThresholdSplitter;
import jsat.classifiers.trees.Splitter;


public class testing {
    public static void main(String[] a) {
        percentTest(1);
        //thresholdTest((double) 2.0);
    }


    public static void percentTest(double thresh) {
        Object[] args = {thresh};
        Splitter perc = new PercentSplitter();
        double[] accs = {0, 1, 10, 3, 14, 5};
        //double[] fairs = {0, 4, 2, 1, 3, 0};
        double[] fairs ={0, 0, 0,  0, 0,  0};

        System.out.println(perc.getBestSplit(accs, fairs, args));
    }

    public static void thresholdTest(double thresh) {
        Object[] args = {thresh};
        Splitter perc = new ThresholdSplitter();
        double[] accs = {0, 1, 10, 13, 14, 5};
        double[] fairs = {0, 4, 2, 1, 3, 0};

        System.out.println(perc.getBestSplit(accs, fairs, args));
    }
}
