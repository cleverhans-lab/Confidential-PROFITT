package jsat.classifiers.trees;

import static java.lang.Math.*;
import java.util.Arrays;
import jsat.classifiers.CategoricalResults;

/**
 * ImpurityScore provides a measure of the impurity of a set of data points 
 * respective to their class labels. The impurity score is maximized when the 
 * classes are evenly distributed, and minimized when all points belong to one 
 * class. <br>
 * The gain in purity can be computed using the static <i>gain</i> methods of
 * the class. However, not all impurity measures can be used for arbitrary data 
 * and splits. Some may only support binary splits, and some may only support 
 * binary target classes.
 * 
 * @author Edward Raff
 */
public class ImpurityScore implements Cloneable
{
    /**
     * Different methods of measuring the impurity in a set of data points 
     * based on nominal class labels
     */
    public enum ImpurityMeasure
    {
        INFORMATION_GAIN, 
        INFORMATION_GAIN_RATIO,
        /**
         * Normalized Mutual Information. The {@link #getScore() } value will be
         * the same as {@link #INFORMATION_GAIN}, however - the gain returned 
         * is considerably different - and is a normalization of the mutual 
         * information between the split and the class label by the class and 
         * split entropy. 
         */
        NMI,
        GINI_DP,
        GINI_EODDS,
        CLASSIFICATION_ERROR
    }
    
    private double sumOfWeights;
    private int numFairSplits;
    private final double[] fairCounts;
    private final double[] counts;
    private double[][] eoddsCrap; // each elem stores # of points in class i and group j
    private final ImpurityMeasure impurityMeasure;
    
    /**
     * Creates a new impurity score that can be updated
     * 
     * @param classCount the number of target class values
     * @param fairAttributeCount the number of attributes for the protected feature
     * @param impurityMeasure 
     */
    public ImpurityScore(int classCount, int fairAttributeCount, ImpurityMeasure impurityMeasure)
    {
        //TODO check init vals
        sumOfWeights = 0.0;
        numFairSplits = fairAttributeCount;
        counts = new double[classCount];
        fairCounts = new double[fairAttributeCount];
        eoddsCrap = new double[classCount][fairAttributeCount];
        this.impurityMeasure = impurityMeasure;
    }
    
    /**
     * Copy constructor
     * @param toClone 
     */
    private ImpurityScore(ImpurityScore toClone)
    {
        this.sumOfWeights = toClone.sumOfWeights;
        this.numFairSplits = toClone.numFairSplits;
        this.counts = Arrays.copyOf(toClone.counts, toClone.counts.length);
        this.fairCounts = Arrays.copyOf(toClone.fairCounts, toClone.fairCounts.length);
        this.eoddsCrap = deepCopyDoubleMatrix(toClone.eoddsCrap);
        this.impurityMeasure = toClone.impurityMeasure;
    }

    public static double[][] deepCopyDoubleMatrix(double[][] input) {
        if (input == null)
            return null;
        double[][] result = new double[input.length][];
        for (int r = 0; r < input.length; r++) {
            result[r] = input[r].clone();
        }
        return result;
    }
    
    /**
     * Removes one point from the impurity score
     * @param weight the weight of the point to add
     * @param targetClass the class of the point to add
     * @param fairAttribute the attribute to protect of the point to add
     */
    public void removePoint(double weight, int targetClass, int fairAttribute)
    {
        //System.out.println("RMVING POINT");
        counts[targetClass] -= weight;
        fairCounts[fairAttribute] -= weight;
        eoddsCrap[targetClass][fairAttribute] -= weight;
        sumOfWeights -= weight;
    }

    /**
     * removes bunch of points
     * @param weights weights to remove
     * @param targetClasses labels added
     * @param fairAttributes fair attr values added
     * @throws Exception if arrays are unmatching lengths
     */
    public void removePoints(double[] weights, int[] targetClasses, int[] fairAttributes) throws Exception {
        if ((weights.length != targetClasses.length) || (weights.length != fairAttributes.length)) {
            throw new Exception("Wrong array lengths for removing points");
        }
        for (int i=0; i<weights.length; i++) {
            removePoint(weights[i], targetClasses[i], fairAttributes[i]);
        }
    }
   
    /**
     * Adds one more point to the impurity score
     * @param weight the weight of the point to add
     * @param targetClass the class of the point to add
     * @param fairAttribute the attribute to protect of the point to add
     */
    public void addPoint(double weight, int targetClass, int fairAttribute)
    {
        counts[targetClass] += weight;
        eoddsCrap[targetClass][fairAttribute] += weight;
        fairCounts[fairAttribute] += weight;
        sumOfWeights += weight;
    }

    /**
     * Adds bunch of points to Impurity Score
     * @param weights weights of points to add
     * @param targetClasses target classes (labels) of points to add
     * @param fairAttributes protected attribute value of each point
     * @throws Exception if arrays are different lengths
     */
    public void addPoints(double[] weights, int[] targetClasses, int[] fairAttributes) throws Exception {
        if ((weights.length != targetClasses.length) || (weights.length != fairAttributes.length)) {
            throw new Exception("Wrong array lengths for adding points");
        }
        for (int i=0; i<weights.length; i++) {
            addPoint(weights[i], targetClasses[i], fairAttributes[i]);
        }
    }

    /**
     * Computes the current impurity score for the points that have been added.
     * A higher score is worse, a score of zero indicates a perfectly pure set 
     * of points (all one class). 
     * @return the impurity score
     */
    public double getScore()
    {
        if(sumOfWeights <= 0)
            return 0;
        double score = 0.0;

        if (impurityMeasure != null)
        switch (impurityMeasure) 
        {
            case INFORMATION_GAIN_RATIO:
            case INFORMATION_GAIN:
            case NMI:
                for (Double count : counts)
                {
                    double p = count / sumOfWeights;
                    if (p > 0)
                        score += p * log(p) / log(2);
                }   break;
            // getScore gets the class score, same for DP and eodds
            case GINI_DP:
            case GINI_EODDS:
                //score = sumOfWeights * sumOfWeights;
                score = 1;
                for (double count : counts)
                {
                    double p = count / sumOfWeights;
                    score -= p * p;
                }   break;
            case CLASSIFICATION_ERROR:
                double maxClass = 0;
                for (double count : counts)
                    maxClass = Math.max(maxClass, count / sumOfWeights);
                score = 1.0 - maxClass;
                break;
            default:
                break;
        }
        // TODO Raff normalisation term
        return abs(score / (1.0 - (1.0 / counts.length)));
        //System.out.println("gini: " + sumOfWeights);
        //return abs(score);//*sumOfWeights*sumOfWeights;
    }
    
    /**
     * Computes the current impurity score for the points that have been added.
     * A higher score is worse, a score of zero indicates a perfectly pure set 
     * of points (all one class). 
     * @return the impurity score
     */
    public double getGiniDP()  // GINI_DP
    {
        //printEoddsCrap();
        if(sumOfWeights <= 0)
            return 0;
        double score = 1.0;
        //double score = sumOfWeights * sumOfWeights;
        for (double count : fairCounts) // iterates through all possible values of protected feature
        {
            double p = count / sumOfWeights;
            score -= p * p;
        }
        // TODO Raff normalisation term/
        return abs(score / (1.0 - (1.0 / numFairSplits)));
        //System.out.println("fair: " + sumOfWeights);
        //return abs(score);// * sumOfWeights*sumOfWeights;
    }


    public double getGiniEOdds() {
        // TODO check 0, 1
        double score_p = 1;
        double score_n = 1;
        //printEoddsCrap();

        for (double fairCount : eoddsCrap[0]) {
            double p = fairCount / counts[0];
//            System.out.println("FC " + fairCount);
//            System.out.println("GRP0 " + counts[0]);
            score_n -= p*p;
        }
        score_n = score_n / (1.0 - 1.0 / numFairSplits);

        for (double fairCount : eoddsCrap[1]) {
            double p = fairCount / counts[1];
//            System.out.println("FC " + fairCount);
//            System.out.println("GRP1 " + counts[1]);
            score_p -= p*p;
        }
        score_p = score_p / (1.0 - 1.0 / numFairSplits);

        double gini_n=0;
        double gini;
        // assuming counts[0] are the neg samples
        gini = counts[0]/sumOfWeights*score_n + counts[1]/sumOfWeights*score_p;
//        System.out.println("SOW " +  sumOfWeights);
        return gini;
    }

    public void printEoddsCrap() { //class i group j
        String a = "C0 G0: " + eoddsCrap[0][0] +
                    "\nC0 G1: " + eoddsCrap[0][1] +
                    "\nC1 G0: " + eoddsCrap[1][0] +
                    "\nC1 G1: " + eoddsCrap[1][1];
        System.out.println(a);
    }

    /**
     * Returns the sum of the weights for all points currently in the impurity 
     * score
     * @return the sum of weights
     */
    public double getSumOfWeights()
    {
        return sumOfWeights;
    }
    
    /**
     * Returns the impurity measure being used 
     * @return the impurity measure being used 
     */
    public ImpurityMeasure getImpurityMeasure()
    {
        return impurityMeasure;
    }
    
    /**
     * Obtains the current categorical results by prior probability 
     * 
     * @return the categorical results for the current score
     */
    public CategoricalResults getResults()
    {
        CategoricalResults cr = new CategoricalResults(counts.length);
        for(int i = 0; i < counts.length; i++)
            cr.setProb(i, counts[i]/sumOfWeights);
        return cr;
    }
    
    /*
     * NOTE: for calulating the entropy in a split, if S is the current set of
     * all data points, and S_i denotes one of the subsets gained from splitting
     * The Gain for a split is
     *
     *                       n
     *                     ===== |S |
     *                     \     | i|
     * Gain = Entropy(S) -  >    ---- Entropy/S \
     *                     /      |S|        \ i/
     *                     =====
     *                     i = 1
     *
     *                   Gain
     * GainRatio = ----------------
     *             SplitInformation
     *
     *                        n
     *                      ===== |S |    /|S |\
     *                      \     | i|    || i||
     * SplitInformation = -  >    ---- log|----|
     *                      /      |S|    \ |S|/
     *                      =====
     *                      i = 1
     */
    
    /**
     * Computes the gain in score from a splitting of the data set
     * 
     * @param wholeData the score for the whole data set
     * @param splits the scores for each of the splits
     * @return the gain for the values given
     */
    public static double[] gain(ImpurityScore wholeData, ImpurityScore... splits)
    {
        return gain(wholeData, 1.0, splits);
    }
    
    /**
     * Computes the gain in score from a splitting of the data set
     * 
     * @param wholeData the score for the whole data set
     * @param wholeScale a constant to scale the wholeData counts and sums by, useful for handling missing value cases
     * @param splits the scores for each of the splits
     * @return the gain for the values given
     */
    public static double[] gain(ImpurityScore wholeData, double wholeScale, ImpurityScore... splits)
    {
        double sumOfAllSums = wholeScale*wholeData.sumOfWeights;

        // NMI
        if(splits[0].impurityMeasure == ImpurityMeasure.NMI)
        {
            double mi = 0, splitEntropy = 0.0, classEntropy = 0.0;
            
            for(int c = 0; c < wholeData.counts.length; c++)//c: class
            {
                final double p_c = wholeScale*wholeData.counts[c]/sumOfAllSums;
                if(p_c <= 0.0)
                    continue;
                
                double logP_c = log(p_c);
                
                classEntropy += p_c*logP_c;
                        
                for (ImpurityScore split : splits) //s: split
                {
                    final double p_s = split.sumOfWeights / sumOfAllSums;
                    if(p_s <= 0)
                        continue;
                    final double p_cs = split.counts[c] / sumOfAllSums;
                    if(p_cs <= 0)
                        continue;
                    mi += p_cs * (log(p_cs) - logP_c - log(p_s));
                    if(c == 0)
                        splitEntropy += p_s * log(p_s);
                }
            }
            
            splitEntropy = abs(splitEntropy);
            classEntropy = abs(classEntropy);
            double[] toret = {0, 0};
            return toret;
            //return 2*mi/(splitEntropy+classEntropy);
            
        }
        //Else, normal cases
        double splitScore = 0.0;
        double gain;
        
        boolean useSplitInfo = splits[0].impurityMeasure == ImpurityMeasure.INFORMATION_GAIN_RATIO;
        ImpurityMeasure measure = splits[0].impurityMeasure;

        // INFORMATION GAIN RATIO
        if(useSplitInfo)  // only if using info gain ratio
        {
            /*
             * TODO should actualy be 0, but performance bug is consistently 
             * occuring if I use another value. Needs serious investigation. 
             * I was testing on (Oracle) 1.7u51 & u20 smoething and both had the
             * issue, on OSX and Windows. 
             * 
             * I was unable to replicate the issue with a smaller self contained
             * program. So I suspect I might be at some threshold / corner case 
             * of the optimizer
             * 
             * Adding a -1 at the final results causes the performance 
             * degredation agian. Occures with both client and server JVM
             * 
             * Using the same code with an if stament seperating the 2 (see old revision)
             * was originally backwards. Changing the correct way revealed the behavior.
             * I'm leaving them seperated to ease investiation later.
             */
            double splitInfo = 1.0;
            for(ImpurityScore split : splits)
            {
                double p = split.getSumOfWeights()/sumOfAllSums;
                if(p <= 0)//log(0) is -Inft, so skip and treat as zero
                    continue;
                splitScore += p * split.getScore();
                splitInfo += p * -log(p);
            }

            gain = (wholeData.getScore()-splitScore)/splitInfo;
        }
        else
        { // GET CLASS IMPURITY GAIN
            for(ImpurityScore split : splits)
            {
                double p = split.getSumOfWeights()/sumOfAllSums;
                if(p <= 0)//log(0) is -Inft, so skip and treat as zero
                    continue;
                splitScore += p*split.getScore();
            }
            // entire nodeset data score - split score is the gain
            gain = wholeData.getScore()-splitScore;
        }
        
        double fairSplitScore = 0.0;
        double fairGain;

        // GET FAIRNESS IMPURITY GAIN
        for (ImpurityScore split : splits) {
            double p = split.getSumOfWeights() / sumOfAllSums;
            if (p <= 0)//log(0) is -Inft, so skip and treat as zero
                continue;
            if (measure == ImpurityMeasure.GINI_DP)
                fairSplitScore += p * split.getGiniDP();
            else
                fairSplitScore += p * split.getGiniEOdds();
//            System.out.println("SP: " + split.getGiniEOdds());
        }
        // fairness over parent - split fairness scores
        if (measure == ImpurityMeasure.GINI_DP)
            fairGain = wholeData.getGiniDP() - fairSplitScore;
        else
            fairGain = wholeData.getGiniEOdds() - fairSplitScore;
//        System.out.println("WH: " + wholeData.getGiniEOdds());
//        System.out.println("SS: " + fairSplitScore);
        // SUBTRACT EM
        double[] toret = {gain, fairGain};
//        System.out.println(Double.toString(fairGain));
        return toret;
        //return gain - fairGain;
        //return gain / fairGain;
        //return gain;
    }
    
    @Override
    protected ImpurityScore clone()
    {
        return new ImpurityScore(this);
    }
}
