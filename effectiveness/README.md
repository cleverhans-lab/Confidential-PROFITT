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
will read these into `pandas.DataFrames` stored in the `results` 
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
