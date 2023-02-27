# Testing Fair Trees for PROFITT



## Running
Alter categories in `sbatch_scripts/run.sh` for various datasets, train/test
results, GINI fainresses formulations, etc. `sbatch_scripts/run_baselines.sh` 
gives decision trees or random forests trained without fairness.

1. These categories, along
with the `date` will create raw java files in `raw_txts`. This step 
occurs for both the `run.sh` and `baseline_run.sh` (the latter of 
which trains trees without fairness criteria).
2. The script 
will read these into nice `pandas.DataFrames` stored in the `results` 
directory under the appropriate dataset, fairness condition, date,
number of seeds, and GINI formulation. These are saved as `.csv` files.
This step is included in both of the aforementioned scripts. 
3. These csvs can then be plotted. This step is included in the `run.sh` script 
and also available in a separate `plotting.sh` script if you'd only
like to plot. Plots are generated in the same directory as their csvs
in a `<csv dir here>/plots/` subdirectory. 
4. Other plots (the ones in the paper) are computed in Jupyter notebooks (in
`notebooks/`). Accuracy vs unfairness plots are in `acc_v_fair.ipynb` and
IGS (unfairness info gain) plotting in `IGS_plotting.ipynb`




# Fair-Forest (Prior to PROFITT Project)


Extension of the JSAT library based on the "Fair forests: Regularized tree induction to minimize model bias" paper by Raff, Edward, Jared Sylvester, and Steven Mills.

## Citations

This project is based on the following paper:
Raff, Edward, Jared Sylvester, and Steven Mills. "Fair forests: Regularized tree induction to minimize model bias." Proceedings of the 2018 AAAI/ACM Conference on AI, Ethics, and Society. 2018.

It uses the JSAT library developed by Edward Raff under GPL 3. The link to the JSAT github page is [here](https://github.com/EdwardRaff/JSAT).

## Changes

I have altered the impurity score of classifiers so that a fairness constraint is added. I have also altered the arff reader to ignore missing values in datasets.

## Running

Example of a decision tree using the fairness constraint is found under src/main/java/com/examples/jsatexamples folder. This contains the relevant testing files for the Community Crime, Compas, and Credit Card data set. Random shuffles of the data sets are found in the resources folder.

