import numpy as np
import pandas as pd
from matplotlib import pyplot as plt
import fairlearn.metrics as flm
import sklearn.metrics as skm
from fairlearn.metrics import MetricFrame
import functools
import argparse
import time
import multiprocessing
from joblib import Parallel, delayed

pd.set_option("display.max_rows", None, "display.max_columns", None)


def frame_from_files(dir_path, num_threshes, is_baseline):
    """
    Builds dataframe from the file outputted by the java runme function, which is a bit of a special format.
    :param dir_path: Directory path, with final '/'
    :type dir_path: String
    :param num_threshes: Number thresholds gathered data for.
    :type num_threshes: int
    :return: dataframe with pd.multiIndex: (threshold, seed, vals (true, predicted labels and fair attribute values)).
    :rtype: pd.DataFrame
    """
    thresholds = []
    columns = []
    num_seeds = 50
    for j in range(num_threshes):
        if is_baseline:
            path = dir_path
        else:
            path = '{}{}.txt'.format(dir_path, j)
        data = []
        with open(path) as file:
            for line in file:
                clean_line = line.rstrip()
                if len(clean_line) > 100:
                    clean_line = clean_line.replace("[", "").replace("]", "")
                    clean_line = clean_line.split(", ")
                data.append(clean_line)
        data = data[:-1]  # empty line at end of file
        num_seeds = len(data[0::5])
        threshold = data[1::5]
        tru = data[2::5]
        pre = data[3::5]
        fai = data[4::5]
        for i in range(len(tru)):
            columns.append(tru[i])
            columns.append(pre[i])
            columns.append(fai[i])
            # threshes precision in thousandths, and three recorded values per seed and threshold
            thresholds.append([round(float(threshold[i]), 4)] * 3)
    thresholds = np.asarray(thresholds).flatten()
    labels = np.asarray([["trues", "preds", "fairs"]] * num_seeds * num_threshes).flatten()
    # use index to prep frame
    seeds = list(range(0, num_seeds))
    # 3 recorded values for each seed (trues, preds, and fairs)
    seeds = np.asarray([[val] * 3 for val in seeds] * num_threshes).flatten()
    names = list(zip(thresholds, seeds, labels))
    index = pd.MultiIndex.from_tuples(names, names=["threshold", "seed", "vals"])
    columns = np.asarray(columns).astype(int)
    frame = pd.DataFrame(columns.T, columns=index)
    return frame


def get_metrics(labels):
    """
    Helper for seaborn csvs
    :param labels: [0, 1] or [1, 0], where the first value is the bad decision. (e.g. in COMPAS, 1=recidivate,
                   so [1, 0] should get used)
    :type labels:  list
    :return:       Grabs the metrics (used in MetricFrame) and extra_metrics, which don't play well with MetricFrame so
                   need to evaluate separately
    :rtype:        dict, dict
    """
    # have to format these especially for MetricFrame cause they need extra params.
    # by defn, precision and recall are for the best value outcome, not worst
    precision_alt = functools.partial(skm.precision_score, labels=labels, pos_label=labels[1], zero_division=0, average='binary')
    recall_alt = functools.partial(skm.recall_score, labels=labels, pos_label=labels[1], average='binary')
    log_loss_alt = functools.partial(skm.log_loss, labels=labels)
    cm_alt = functools.partial(skm.confusion_matrix, labels=labels,
                                normalize=None)  # normalize='true')  # sets nomlaization for KL divs
    # dp_alt = functools.partial(flm.demographic_parity_difference, sensitive_features=sensitive)
    metrics = {
        'support': flm.count,
        'accuracy': skm.accuracy_score,
        'precision': precision_alt,
        'recall': recall_alt,
        'conf_matrix': cm_alt, # doesn't play well with pandas.mean()
        'log_loss': log_loss_alt,
        'selection_rate': flm.selection_rate,
        'mean_prediction': flm.mean_prediction,
        'tnr': flm.true_negative_rate,
        'tpr': flm.true_positive_rate,
        'fpr': flm.false_positive_rate,
        'fnr': flm.false_negative_rate
    }

    # for some reason Metric Frame doesn't like these, so call directly (only for overall)
    # as it doesn't make sense to take the difference after already differed em.
    extra_metrics = {
        'dp_ratio': flm.demographic_parity_ratio,
        'dp_diff': flm.demographic_parity_difference,
        'eodds_ratio': flm.equalized_odds_ratio,
        'eodds_diff': flm.equalized_odds_difference
    }
    return metrics, extra_metrics


def get_seaborn_csvs(frame, labels, save_dir=None):
    """
    Makes (and possibly saves) csvs for seaborn plotting. Returns the frames too. First frame is the overall
    model performances, second is model performances for each group, and third is differences in model performances in
    each group. All of these are over thresholds and seeds. ASSUMES BINARY GROUPS
    :param frame:    frame from files
    :type frame:     pd.DataFrame
    :param labels:   [0, 1] or [1, 0], where the first value is the bad descision. (e.g. in COMPAS, 1=recidivate,
                     so [1, 0] should get used)
    :type labels:    list
    :param save_dir: Optional, path to save to, with final '/'
    :type save_dir:  String
    :return:
    :rtype:
    """
    # this takes a minute
    metrics, extra_metrics = get_metrics(labels)
    threshes = frame.columns.get_level_values('threshold').to_numpy()
    # threshes = [0.0, .002, .004]
    num_seeds = len(np.unique(frame.columns.get_level_values('seed').to_numpy()))
    num_threshes = len(np.unique(threshes))

    base = [None] * num_seeds
    temp = base
    all_diffs = [None] * num_seeds * num_threshes
    num_grps = 2
    # ugly but gets number of seeds from frame column multiIndex

    jobs = multiprocessing.cpu_count() - 1
    results = Parallel(n_jobs=jobs)(
        delayed(get_row)(temp, thresh_num, threshes, num_seeds, frame, metrics, extra_metrics)
        for thresh_num in range(len(np.unique(threshes)))
    )

    alls = []
    all_grps = []
    for thresh_frame in results:
        for seed_frame in thresh_frame:
            alls.append(seed_frame.overall.T)
            all_grps.append(seed_frame.by_group.T)

    # packages things up nicely
    all_data = pd.concat(list(alls), axis=1).T
    all_data["seed"] = np.asarray([list(range(num_seeds))] * len(np.unique(threshes))).flatten()
    mean_index = np.asarray([[t] * num_seeds for t in np.unique(threshes)]).flatten()
    all_data["threshold"] = mean_index

    #all_diff_data = pd.concat(all_diffs)
    #all_diff_data["seed"] = all_data.index.to_list()
    #all_diff_data["threshold"] = mean_index

    all_grp_data = pd.concat(all_grps, axis=1).T
    all_grp_data["group"] = all_grp_data.index.to_list()
    all_grp_data["seed"] = np.asarray([list(range(num_seeds))] * len(np.unique(threshes)) * num_grps).flatten()
    mean_index = np.asarray([[thresh] * num_seeds * num_grps for thresh in np.unique(threshes)]).flatten()
    all_grp_data["threshold"] = mean_index

    if save_dir is not None:
        all_data.to_csv('{}overall_performance_seedwise.csv'.format(save_dir))
        #all_diff_data.to_csv('{}group_differences_M-m_seedwise.csv'.format(save_dir))
        all_grp_data.to_csv('{}group_performances_seedwise.csv'.format(save_dir))

    return alls, all_grps#, #all_diffs


def get_row(temp, i, threshes, num_seeds, frame, metrics, extra_metrics):
    #for i in range(len(np.unique(threshes))):
    thresh = np.unique(threshes)[i]
    #print(thresh)
    for seed in list(range(num_seeds)):

        # sensitives must be strings for fairlearn
        sensitive = frame[thresh, seed, "fairs"].to_list()
        major = frame[thresh, seed, "fairs"].value_counts().keys()[0]  # more populous group
        for k in range(len(sensitive)):
            sensitive[k] = "Majority" if sensitive[k] == major else "Minority"

        # metric frame handles all the sklearns
        met_frame = MetricFrame(metrics=metrics, y_true=frame[thresh, seed, "trues"],
                                y_pred=frame[thresh, seed, "preds"],
                                sensitive_features=sensitive)

        # from lamentable Metric Frame behaviours here
        fair_metrics = get_fair_metrics(extra_metrics, frame[thresh, seed, "trues"],
                                        frame[thresh, seed, "preds"], sensitive)

        overall_seed_frame = met_frame.overall
        for key in list(fair_metrics.keys()):
            overall_seed_frame[key] = fair_metrics[key]

        # record seed data
        index = seed
        temp[index] = met_frame
        # temp[0][index] = overall_seed_frame.T
        # temp[1][index] = met_frame.by_group.T
    return temp


def get_fair_metrics(extra_metrics, trues, preds, sensitive):
    # Helper for get_seaborn_csvs
    to_ret = {}
    for key in list(extra_metrics.keys()):
        val = extra_metrics[key](y_true=trues,
                                 y_pred=preds,
                                 sensitive_features=sensitive)
        to_ret[key] = val
    return to_ret


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('dir_path', type=str, help="Path to directory to read from, must include final '/'")
    parser.add_argument('num_thresholds', type=int, help="Number files to read, really.")
    parser.add_argument('good_label', type=int, help="0 or 1, whichever if the beneficial label.")
    parser.add_argument('-save_path', type=str, help="Path to directory to save to, must include final '/'",
                        default=None)
    parser.add_argument('-is_baseline', type=bool, help="If true, dir_path should point to baseline file", default=False)
    args = parser.parse_args()

    baseline = False
    if args.is_baseline == "True":
        baseline = True

    frame = frame_from_files(args.dir_path, args.num_thresholds, baseline)
    labels = [1, 0] if args.good_label == 0 else [0, 1]
    overall, grouped = get_seaborn_csvs(frame, labels, args.save_path)

    # frame = frame_from_files("../raw_txts/COMPAS/4-6_1_train/", 500, False)
    # #labels = [1, 0] if args.good_label == 0 else [0, 1]
    # overall, grouped = get_seaborn_csvs(frame, [1, 0], "~/Desktop/test/")

# python ./python_scripts/results_to_csvs.py ./Crime_train/ 300 0 ./results/someting 
