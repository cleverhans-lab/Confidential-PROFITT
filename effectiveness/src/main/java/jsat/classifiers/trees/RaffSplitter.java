package jsat.classifiers.trees;

public class RaffSplitter implements Splitter{
    public int getBestSplit(double[] accs, double[] fairs, Object... args) {
        int min_i, max_i;
        double lambda;
        try {
            min_i = (int) args[0];
            max_i = (int) args[1];
            lambda = (double) args[2];
        } catch (ClassCastException e) {
            System.out.println(e + "\nBad args type for splitting");
            min_i = 0;
            max_i = 0;
            lambda = 1.0;
        }

        //System.out.println("raff called");
        double[] subed = new double[accs.length];
        double max = Double.MIN_VALUE;
        int max_ind = -1;
        for (int i=min_i; i<max_i; i++) {
            subed[i] = accs[i] - (lambda * fairs[i]);
            if (subed[i] > max) {
                max = subed[i];
                max_ind = i;
            }
        }

        //no good split, just get best fairness, that way we can at least get somewhere
//        if (max_ind == -1) {
//            System.out.println("Bad split :(" + max_i + ", " + min_i);
//            double minFair = Double.MAX_VALUE;
//            for (int i =0; i<fairs.length; i++) {
//                if (fairs[i] < minFair) {
//                    minFair = fairs[i];
//                    max_ind = i;
//                }
//            }
//        }

        return max_ind;
    }
}
