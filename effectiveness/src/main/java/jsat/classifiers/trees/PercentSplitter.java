package jsat.classifiers.trees;

import jsat.utils.PairedReturn;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.lang.Math;

public class PercentSplitter implements Splitter{
    public int getBestSplit(double[] accs, double[] fairs, Object... args) {
        double thresh = 0;
        try {
            thresh = (double) args[0];
        } catch (ClassCastException e) {
            System.out.println(e + "\nBad args type for splitting");
        }

        // FOR BASELINES, IGNORE fairs ENTIRELY 
        if (thresh==1) {
            PairedReturn<Double, Integer> tmp = getMax(accs);
            return tmp.getSecondItem();
        }


//        for (int i=0; i<accs.length; i++) {
//            System.out.println(accs[i] + ", " + fairs[i]);
//        }

        // count number 'useless'
        int useless = 0;
        for (int i=0; i<accs.length; i++) {
            if ((accs[i] == 0.0) && (fairs[i] == 0.0))
                useless++;
        }

        int useful_length = accs.length - useless;
        // int useful_length = accs.length;
        int top_n = (int) Math.round(thresh * useful_length);
        // System.out.println("Useful length: " + top_n);

        // find indicies of top n fairest, record best acc as we go
        double chosenAcc = -Double.MAX_VALUE;
        int index = -1;

        for (int i=0; i<top_n; i++) {
            //DoubleSummaryStatistics stat = Arrays.stream(fairs).summaryStatistics();
            //double curFair = stat.getMax();
            PairedReturn<Double, Integer> tmp = getMax(fairs);
            double curFair = tmp.getFirstItem();
            int fairIdx = tmp.getSecondItem();
            if (fairIdx == -1) { // issue with MIN things
                break;
            }
            fairs[fairIdx] = -Double.MAX_VALUE; // dont see it again
            if (accs[fairIdx] > chosenAcc) {
                chosenAcc = accs[fairIdx];
                index = fairIdx;
            }
        }

        //no good split, just get best fairness, that way we can at least get somewhere
        if (index == -1) {
            //System.out.println("Bad splitt :(");
            double minFair = Double.MAX_VALUE;
            for (int i =0; i<fairs.length; i++) {
                if (fairs[i] < minFair) {
                    minFair = fairs[i];
                    index = i;
                }
            }
        }
        return index;
    }

    /*
    Gets max value and its index, returns -Double.MAX_VALUE and -1 if it can't find anything that works.
     */
    private PairedReturn<Double, Integer> getMax(double[] arr) {
        double max = -Double.MAX_VALUE;
        int max_ind = -1;
        for (int i=0; i<arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
                max_ind = i;
            }
        }
        PairedReturn<Double, Integer> toret = new PairedReturn(max, max_ind);
        return toret;
    }

    private int getMaxInd(double[] arr, double max) {
        for (int i=0; i<arr.length; i++) {
            if (arr[i] == max)
                return i;
        }
        return -1;
    }
}
