
package jsat.classifiers.trees;

import java.util.Random;
import java.util.Set;
import jsat.classifiers.ClassificationDataSet;
import jsat.regression.RegressionDataSet;
import jsat.utils.ModifiableCountDownLatch;
import jsat.utils.random.RandomUtil;

/**
 * An extension of Decision Trees, it ignores the given set of features to use-
 * and selects a new random subset of features at each node for use. <br>
 * <br>
 * The Random Decision Tree supports missing values in training and prediction. 
 * 
 * @author Edward Raff
 */
public class RandomDecisionTree extends DecisionTree
{

    private static final long serialVersionUID = -809244056947507494L;
    private int numFeatures;

    public RandomDecisionTree(int fairAttribute, ImpurityScore.ImpurityMeasure gainMethod)
    {
        this(1, fairAttribute, gainMethod);
    }

    /**
     * Creates a new Random Decision Tree 
     * @param numFeatures the number of random features to use
     * @param fairAttribute the attribute to keep fair
     */
    public RandomDecisionTree(int numFeatures, int fairAttribute, ImpurityScore.ImpurityMeasure gainMethod)
    {
        super(numFeatures, fairAttribute, gainMethod);
        setRandomFeatureCount(numFeatures);
    }

    /**
     * Creates a new Random Decision Tree
     * @param numFeatures the number of random features to use
     * @param maxDepth the maximum depth of the tree to create
     * @param minSamples the minimum number of samples needed to continue branching
     * @param fairAttribute the attribute to keep fair
     * @param pruningMethod the method of pruning to use after construction 
     * @param testProportion the proportion of the data set to put aside to use for pruning
     */
    public RandomDecisionTree(int numFeatures, int maxDepth, int minSamples, int fairAttribute,
                              TreePruner.PruningMethod pruningMethod, double testProportion,
                              ImpurityScore.ImpurityMeasure gainMethod)
    {
        super(maxDepth, minSamples, fairAttribute, pruningMethod, testProportion, gainMethod);
        setRandomFeatureCount(numFeatures);
    }

    /**
     * Copy constructor
     * @param toCopy the object to copy
     */
    public RandomDecisionTree(RandomDecisionTree toCopy)
    {
        super(toCopy);
        this.numFeatures = toCopy.numFeatures;
    }
    
    /**
     * Sets the number of random features to and use at each node of
     * the decision tree
     * @param numFeatures the number of random features
     */
    public void setRandomFeatureCount(int numFeatures)
    {
        if(numFeatures < 1)
            throw new IllegalArgumentException("Number of features must be positive, not " + numFeatures);
        this.numFeatures = numFeatures;
    }

    /**
     * Returns the number of random features used at each node of the tree
     * @return the number of random features used at each node of the tree
     */
    public int getRandomFeatureCount()
    {
        return numFeatures;
    }

    @Override
    protected Node makeNodeC(ClassificationDataSet dataPoints, Set<Integer> options, int depth, boolean parallel,
                             ModifiableCountDownLatch mcdl, Splitter splitter, String IGS_fname, Object... args)
    {
	if(dataPoints.isEmpty())
        {
            mcdl.countDown();
            return null;
        }
        final int featureCount = dataPoints.getNumFeatures();
        fillWithRandomFeatures(options, featureCount);
        //Splitter split = new PercentSplitter();
        Object[] args2 = {(double) 1};
        return super.makeNodeC(dataPoints, options, depth, parallel, mcdl, splitter, IGS_fname, args2);
        //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Node makeNodeR(RegressionDataSet dataPoints, Set<Integer> options, int depth, boolean parallel,
                             ModifiableCountDownLatch mcdl)
    {
	if(dataPoints.isEmpty())
        {
            mcdl.countDown();
            return null;
        }
        final int featureCount = dataPoints.getNumFeatures();
        fillWithRandomFeatures(options, featureCount);
        return super.makeNodeR(dataPoints, options, depth, parallel, mcdl); //To change body of generated methods, choose Tools | Templates.
    }

    private void fillWithRandomFeatures(Set<Integer> options, final int featureCount)
    {
        options.clear();
        Random rand = RandomUtil.getRandom();
        
        while(options.size() < numFeatures)
        {
            options.add(rand.nextInt(featureCount));
        }
    }

    @Override
    public RandomDecisionTree clone()
    {
        return new RandomDecisionTree(this);
    }
     
}
