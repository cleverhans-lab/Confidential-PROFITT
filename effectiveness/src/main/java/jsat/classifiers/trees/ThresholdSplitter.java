package jsat.classifiers.trees;

public class ThresholdSplitter implements Splitter {
    /**
     * Gets split that maxes the accuracy gain while unfairess gain is under some passed threshold
     * @param accs accuracy gains
     * @param fairs fair gains
     * @param args double threshold
     * @return array index for chosen split
     */
    public int getBestSplit(double[] accs, double[] fairs, Object... args) {
        double thresh = -1;
        try {
            thresh = (double) args[0];
        } catch (ClassCastException e) {
            System.out.println(e + "\nBad threshold type for splitting, need double." + args[0]);
        }

        int max_ind = -1;
        double maxAcc = -1;
        for (int i=0; i<fairs.length; i++) {
            if ((accs[i] != 0.0) || (fairs[i] != 0.0)) { // useful split
                if (fairs[i] < thresh) { //valid split
                    if (accs[i] > maxAcc) { //record best acc
                        maxAcc = accs[i];
                        max_ind = i;
                    }
                }
            }
        }

        //no good split, just get best fairness, that way we can at least get somewhere
        if (max_ind == -1) {
            //System.out.println("bad split");

            double minFair = Double.MAX_VALUE;
            for (int i = 0; i < fairs.length; i++) {
                if (fairs[i] < minFair) {
                    minFair = fairs[i];
                    max_ind = i;
                }
            }
        }

//        if (max_ind == -1) { //no good split, just get best acc
//            double maxAc = Double.MIN_VALUE;
//            for (int i =0; i<accs.length; i++) {
//                if (accs[i] > maxAc) {
//                    maxAc = accs[i];
//                    max_ind = i;
//                }
//            }
//        }


        return max_ind;
    }
}
