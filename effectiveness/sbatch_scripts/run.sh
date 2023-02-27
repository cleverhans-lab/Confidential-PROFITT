#!/bin/bash
source ~/.bashrc
conda activate CPoF
# workon CPoF
module load maven
mvn clean compile package

NUMDATE="rf_10-9"
DATASETS=("adult" "default_credit" "CrimeCommunity" "COMPAS")
METHODS=("thresh" "lambda")
FAIRS=("DP" "EODDS")
NUMSEEDS=10
NUMTHRESH=250
BASELINE="nah"

run_experiment() {
  # run java
  java -cp target/classes/ com.examples.jsatexamples.run $FAIR $METHOD $NUMDATE $NUMSEEDS $NUMTHRESH $STEP $DSET $BASELINE
  echo "trained"

  # make frames
  python python_scripts/results_to_csvs.py $RAWS $NUMTHRESH 0 -save_path $FRAMES
  python python_scripts/results_to_csvs.py $RAWSTRAIN $NUMTHRESH 0 -save_path $FRAMESTRAIN -is_baseline "True"

  echo "framed"
  echo $FRAMES
  # plotting
  # python python_scripts/plotting.py $FRAMES None 0 $PLOTS $METHOD
}

for DSET in "${DATASETS[@]}"; do
  for METHOD in "${METHODS[@]}"; do
    # set higher range sweep for lambda splitter
    echo $METHOD
    if [ $METHOD = "lambda" ]
    then
      STEP=.02
      #STEP=.005
    else
      STEP=.004
      #STEP=.001
    fi

    for FAIR in "${FAIRS[@]}"; do
			DATE="${NUMDATE}_${NUMSEEDS}_${METHOD}"
      RAWS="./raw_txts/${DSET}_${FAIR}/$DATE/"
      RAWSTRAIN="./raw_txts/${DSET}_${FAIR}/train_$DATE/"
      echo $RAWS
      echo $RAWSTRAIN
      mkdir -p $RAWS
      mkdir -p $RAWSTRAIN
      ./java_sux.sh $RAWS
      ./java_sux.sh $RAWSTRAIN

      FRAMES="./results/${DSET}_${FAIR}/$DATE/"
      FRAMESTRAIN="./results/${DSET}_${FAIR}/train_$DATE/"

      PLOTS="./results/${DSET}_${FAIR}/$DATE/plots/"
      IGS_PLOTS="./results/${DSET}_${FAIR}/$DATE/IGS_plots/"

      mkdir -p $FRAMES
      mkdir -p $FRAMESTRAIN
		  run_experiment
    	done
  done
done


