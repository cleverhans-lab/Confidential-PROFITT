import numpy as np
import pandas as pd
from matplotlib import pyplot as plt
import seaborn as sb
import argparse
import scipy.stats

# plt.rcParams['text.latex.preamble']=[r"\usepackage{lmodern}"]

params = {'axes.labelsize': 16,
          'axes.titlesize': 16,
          'xtick.labelsize': 16,
          'ytick.labelsize': 16,
          'legend.title_fontsize': 16,
          'legend.fontsize': 16,
          'legend.handlelength': 1,
          'figure.autolayout': 1,
          'font.size': 16,
          "figure.figsize": (12,6),
           'text.usetex' : True,
            'font.family' : 'lmodern'}
plt.rcParams.update(params)


def read_frames(dir_path, good_label):
	performances = pd.read_csv('{}overall_performance_seedwise.csv'.format(dir_path))
	# differences = pd.read_csv('{}group_differences_M-m_seedwise.csv'.format(dir_path))
	group_perfs = pd.read_csv('{}group_performances_seedwise.csv'.format(dir_path))

	# good label was passed to the script that formed these frames.
	if good_label == 0:
		# then selection rate is wrt label 0, we need wrt label 1
		performances['selection_rate_pos'] = 1 - performances['selection_rate']
		group_perfs['selection_rate_pos'] = 1 - group_perfs['selection_rate']
		# we also gotta get precision and recall for the 1 label
		# performances['recall'] = 1 - performances['recall']
		# performances['precision'] = 1 - performances['precision']
		# group_perfs['recall'] = 1 - group_perfs['recall']
		# group_perfs['precision'] = 1 - group_perfs['precision']
		performances = performances.rename(columns={'selection_rate': 'selection_rate_neg'})
		group_perfs = group_perfs.rename(columns={'selection_rate': 'selection_rate_neg'})
		# we also swapped pos/neg labels here :(
		oops_map = {'tnr': 'tpr', 'tpr': 'tnr', 'fpr': 'fnr', 'fnr': 'fpr'}
		performances = performances.rename(columns=oops_map)
		group_perfs = group_perfs.rename(columns=oops_map)
	else:
		performances['selection_rate_neg'] = 1 - performances['selection_rate']
		group_perfs['selection_rate_neg'] = 1 - group_perfs['selection_rate']
		performances = performances.rename(columns={'selection_rate': 'selection_rate_pos'})
		group_perfs = group_perfs.rename(columns={'selection_rate': 'selection_rate_pos'})
	return performances, group_perfs


def plot_performances(performances, base_perfs, save_dir, xlabel):
	metrics = list(performances.columns)
	print(metrics)
	unwanted = ['Unnamed: 0', 'support', 'seed', 'threshold', 'sensitive_feature_0', 'conf_matrix']
	for elem in unwanted:
		if elem in metrics:
			metrics.remove(elem)

	seeds = performances['seed'].unique()
	cm_subsection = np.linspace(0, 1, len(seeds))
	colors = [plt.cm.tab10(x) for x in cm_subsection]
	
	for y in ['dp_diff', 'eodds_diff', 'accuracy']:  # metrics
		sb.lineplot(x="threshold", y=y, data=performances)
		if base_perfs is not None:
			mean, low, upper = mean_confidence_interval(base_perfs[y])
			plt.axhline(y=mean, color='b', linestyle='--')
			x = sorted(performances['threshold'])
			plt.fill_between(x=x, y1=low, y2=upper, alpha=.1)
		xlab = xlabel
		if xlabel == "thresh":
			xlab = "Threshold"
		elif xlabel == "lambda":
			xlab = "Lambda"
		plt.xlabel(xlab)
		if y == 'dp_diff':
			plt.ylabel('Demographic Parity Gap')
		elif y == "eodds_diff":
			plt.ylabel("Equalized Odds Gap")
		else:
			plt.ylabel(y.capitalize())
		#plt.xlim((0, .05))
		plt.savefig('{}{}.png'.format(save_dir, y), dpi=300)
		print(y)
		plt.clf()


def mean_confidence_interval(data, confidence=0.95):
	a = 1.0 * np.array(data)
	n = len(a)
	m, se = np.mean(a), scipy.stats.sem(a)
	h = se * scipy.stats.t.ppf((1 + confidence) / 2., n-1)
	return m, m-h, m+h


def plot_grp_performances(df, base_df, save_dir, xlabel):
	metrics = list(df.columns)
	unwanted = ['group', 'Unnamed: 0', 'support', 'seed', 'threshold', 'sensitive_feature_0', 'conf_matrix']
	for elem in unwanted:
		if elem in metrics:
			metrics.remove(elem)

	for y in ['accuracy']: #metrics:
		sb.lineplot(x="threshold", y=y, data=df, hue="group")
		if base_df is not None:
			minors = base_df[base_df['sensitive_feature_0'] == 'Minority']
			majors = base_df[base_df['sensitive_feature_0'] == 'Majority']
			mean, low, upper = mean_confidence_interval(majors[y])
			plt.axhline(y=mean, color='b', linestyle='--')
			x = sorted(performances['threshold'])
			plt.fill_between(x=x, y1=low, y2=upper, color='b', alpha=.1)
			#plt.axhline(y=base_df.loc[0][y], color='b', linestyle='--')
			mean, low, upper = mean_confidence_interval(minors[y])
			plt.axhline(y=mean, color='orange', linestyle='--')
			x = sorted(performances['threshold'])
			plt.fill_between(x=x, y1=low, y2=upper, color='orange', alpha=.1)
			#plt.axhline(y=base_df.loc[1][y], color='orange', linestyle='--')
		xlab = xlabel
		if xlabel == "thresh":
			xlab = "Threshold"
		elif xlabel == "lambda":
			xlab = "Lambda"
		plt.xlabel(xlab)
		plt.legend(title='Group')
		if y == 'dp_diff':
			plt.ylabel('Demographic Parity Gap')
		elif y == "eodds_diff":
			plt.ylabel("Equalized Odds Gap")
		else:
			plt.ylabel(y.capitalize())
		plt.savefig('{}{}.png'.format(save_dir, y), dpi=300)
		print(y)
		plt.clf()


def IGS_plots(igs_path, overall_df, save_path):
	igs = pd.read_csv(igs_path, header=None)
	igs = igs.rename(columns={0: 'Threshold', 1:'IGS', 2: 'depth'})
	df = overall_df
	for thresh in igs['Threshold'].unique():
		plt.clf()
		fig, ax = plt.subplots()
		data = igs[igs['Threshold'] == thresh]
		nans = data[data['IGS'] == 1.7976931348623157e+308]
		xs = nans['depth'].value_counts().keys().to_list()
		ys = nans['depth'].value_counts().to_list()
		data = data.replace(1.7976931348623157e+308, np.nan)
		data = data.dropna()
		max_igs = []
		for depth in sorted(data['depth'].unique().tolist()):
			deep_data = data[data['depth'] == depth]
			max_igs.append(max(deep_data['IGS']))
		sb.lineplot(x='depth', y='IGS', data=data, ax=ax, label='IGS')
		ax.axhline(y=thresh,  color='black', linestyle='dashed', label='T')
		ax.axhline(y=data['IGS'].max(), color='red', linestyle='--', label='Effective T')
		g = ax.twinx()
		ax.plot(sorted(data['depth'].unique().tolist()), max_igs, color='green', label='Max IGS')
		d2 = df[df['threshold'] == thresh]
		dp_avg = d2['dp_diff'].mean()
		dp_std = np.std(d2['dp_diff'])
		X = np.linspace(0, igs['depth'].max())
		Y_top = dp_avg + dp_std
		Y_bot = dp_avg - dp_std

		g.axhline(dp_avg, color='purple',label='DP')
		g.fill_between(X, Y_bot, Y_top, color='purple', alpha=.05)

		g.set_ylabel('DP')
		g.set_ylim([0, .5])
		ax.set_xlim([0, igs['depth'].max()])
		ax.legend()
		g.legend()
		plt.savefig(save_dir+"{}.png".format(thresh), dpi=300)


if __name__ == '__main__':
	parser = argparse.ArgumentParser()
	parser.add_argument('dir_path', type=str, help="Path to directory where threshold results are '/'")
	parser.add_argument('base_dir_path', type=str, help="Path to directory where baseline results are '/'")
	parser.add_argument('good_label', type=int, help="0 or 1, whichever is the beneficial label (MUST be same as what"
													 "was passed to the results_to_csvs.py script.).")
	parser.add_argument('save_path', type=str, help="Path to directory to save to, must include final '/'",
						default=None)
	parser.add_argument('xlabel', type=str, help="Label for x-axis", default="Threshold")
	args = parser.parse_args()
	dir_path = args.dir_path
	base_path = args.base_dir_path
	if (base_path == 'None'):
		base_path = None
	good = args.good_label
	save_dir = args.save_path

	performances, group_perfs = read_frames(dir_path, good)
	if base_path is not None:
		base_perfs, base_grp_perfs = read_frames(base_path, good)
	else:
		base_perfs = None
		base_grp_perfs = None
	save_dir = '{}grp_'.format(save_dir)
	plot_grp_performances(group_perfs, base_grp_perfs, save_dir, args.xlabel)
# python ./python_scrips/plotting.py ../results/30-5_5_train/ ../results/5_seed_crime/baseline/raff_ 0 ..results/30-5_5_train/plots/
