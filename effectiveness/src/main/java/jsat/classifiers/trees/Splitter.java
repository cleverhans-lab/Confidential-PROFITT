package jsat.classifiers.trees;

public interface Splitter {
    int getBestSplit(double[] accs, double[] fairs, Object... args);
}
