package com.examples.jsatexamples;

import jsat.classifiers.trees.ImpurityScore;

import java.util.Arrays;

public class ImpScore implements Cloneable{
    double weights;
    double[] counts;
    double[] fairCounts;

    public ImpScore(int numClasses, int numFairVals) {
        weights = 0;
        counts = new double[numClasses];
        fairCounts = new double[numFairVals];
    }

    private ImpScore(ImpScore toClone)
    {
        this.weights = toClone.weights;
        this.counts = Arrays.copyOf(toClone.counts, toClone.counts.length);
        this.fairCounts = Arrays.copyOf(toClone.fairCounts, toClone.fairCounts.length);
    }


    public void removePoint(double weight, int targetClass, int fairAttribute)
    {
        counts[targetClass] -= weight;
        fairCounts[fairAttribute] -= weight;
        weights -= weight;
    }

    public void addPoint(double weight, int targetClass, int fairAttribute)
    {
        counts[targetClass] += weight;
        fairCounts[fairAttribute] += weight;
        weights += weight;
    }

    @Override
    protected ImpScore clone()
    {
        return new ImpScore(this);
    }
}
