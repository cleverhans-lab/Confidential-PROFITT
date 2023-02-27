#!/bin/bash
source ~/.bashrc
conda activate CPoF
#workon CPoF
module load maven
mvn clean compile package

NUMDATE="dt_27-9"
DATASETS=("default_credit", "adult" "COMPAS" "CrimeCommunity")
FAIRS=("DP", "EODDS")
NUMSEEDS=10
NUMTHRESH=1
BASELINE="baseline"
METHOD="perc"
STEP=1

run_experiment() {
  # run java
  java -cp target/classes/ com.examples.jsatexamples.run $FAIR $METHOD $NUMDATE $NUMSEEDS $NUMTHRESH $STEP $DSET $BASELINE
  echo "trained"
  python python_scripts/results_to_csvs.py $RAWS $NUMTHRESH 0 -save_path $FRAMES -is_baseline "True"
  python python_scripts/results_to_csvs.py $RAWSTRAIN $NUMTHRESH 0 -save_path $FRAMESTRAIN -is_baseline "True"
}


for DSET in "${DATASETS[@]}"; do
  FAIR="DP"
#  # set up dirs
  DATE="${NUMDATE}_${NUMSEEDS}_${METHOD}"
  RAWS="./raw_txts/${DSET}_${FAIR}/baseline_$DATE/"
  RAWSTRAIN="./raw_txts/${DSET}_${FAIR}/baseline_train_$DATE/"
  echo $RAWS
  echo $RAWSTRAIN
  mkdir -p $RAWS
  mkdir -p $RAWSTRAIN
  touch "${RAWS}0.txt"
  touch "${RAWSTRAIN}0.txt"
  FRAMES="./results/${DSET}_${FAIR}/baseline_$DATE/"
  FRAMESTRAIN="./results/${DSET}_${FAIR}/baseline_train_$DATE/"
  PLOTS="./results/${DSET}_${FAIR}/$DATE/plots/"
  ./java_sux.sh $RAWS
  mkdir -p $FRAMES
  mkdir -p $FRAMESTRAIN
  mkdir $PLOTS
  run_experiment
done


