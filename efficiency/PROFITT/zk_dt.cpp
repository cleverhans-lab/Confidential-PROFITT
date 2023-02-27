#include <emp-tool/emp-tool.h>
#include <emp-zk/emp-zk.h>
#include "utils.cpp"
#include <iostream>
#include <fstream>
#include "constant.cpp"

using namespace emp;
using namespace std;

// first power of 2 bigger than input
int ind_sz_from_count(int x) {
	int curr = 1;
	for (int i=0; i<32; i++) {
		if ((curr<<i) > x) {
			return curr;
		}
	}
	cout << "Error while computing d_index_sz\n";
	return -1;
}

// index_sz should be height of tree
// val_sz should be 32 for storing 32-bit integers in ZKRAM
// d_index_sz should be log(size of row in dataset)
int tree_index_sz = TREE_HT+1;
//int d_index_sz = ind_sz_from_count(NUM_SAMPLES);
int ft_index_sz = ind_sz_from_count(NUM_FEATURES);
int val_sz=32, step_sz=14;
//int index_sz = 5, step_sz = 14, val_sz = 32, d_index_sz = 5;
const int IS_PROTECTED_INDEX = 0; // designates which column of dataset indicates protected class
const int TRUE_CLASS_INDEX = 1; // designates which column represents the ground truth prediction 



// first row of file gives height of the tree
// subsequent rows give Attr Threshold
// leaves should store Attr = 0, Threshold = 0 if negative outcome, 1 if positive outcome
void initialize_tree_from_file(string filename, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T) { 
	ifstream infile(filename);
	int dt_height;
	vector<Integer> A_data;
	vector<Integer> T_data;
	infile >> dt_height;
	int max_node = 1 << (dt_height+1);
	Integer ZERO(val_sz, 0, PUBLIC);
	A_data.push_back(ZERO); // 0th entry has no meaning
	T_data.push_back(ZERO);
	int32_t a;
	int32_t t;
	for (int i=0; i<max_node; i++) {
		infile >> a;
		infile >> t;
		A_data.push_back(Integer(val_sz, a, ALICE));
		T_data.push_back(Integer(val_sz, t, ALICE));
	}
	A.init(A_data);
	T.init(T_data);
}

// first line of file gives number of rows, number of columns
// subsequent lines give rows of data separated by spaces
vector<vector<int32_t>> initialize_dataset_from_file(string filename) {
	vector<vector<int32_t>> D;
	ifstream infile(filename);
	int nrow, ncol;
	infile >> nrow >> ncol;
	int32_t d;
	for (int i=0; i<nrow; i++) {
		vector<int32_t> row;
		for (int j=0; j<ncol; j++) {
			infile >> d;
			row.push_back(d);
		}
		D.push_back(row);
	}
	return D;
}

int num_nodes_from_ht(int tree_ht) {
	int total = 0;
	for (int i=0; i<=tree_ht; i++) {
		total += 1<<i;
	}
	return total;
}

// compute next index, update Cpro and Cun of that index, return it
// curr_index -- index of node in the tree path is currently at
// d_i -- piece of data currently being counted
// A -- attribute array
// T -- threshold array
// Cpro -- counter for data in protected class
// Cun -- counter for data in unprotected class
Integer update_counter(Integer & curr_index, ZKRAM<BoolIO<NetIO>> & d_i, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer attr_temp = A.read(curr_index); //index of splitting attr
	Integer attr = slice_integer(0, ft_index_sz, attr_temp);

	Integer thresh = T.read(curr_index); // threshold for split
	
	// look up whether given data point is in protected class or not
	Integer IS_PRO_IND(ft_index_sz, IS_PROTECTED_INDEX, PUBLIC); 
	Integer temp_is_protected = d_i.read(IS_PRO_IND); // 32 bit
	d_i.refresh();
	// d_i is private and stores 32 bit values, but this is effectively boolean
	// so we place the 0th bit of the lookup on a public Integer 0
	Bit b = temp_is_protected[0];
	Integer is_protected(val_sz, 0, PUBLIC);
	is_protected[0] = b;
	// negation of is_protected
	Integer neg_is_pro(val_sz, 0, PUBLIC);
	neg_is_pro[0] = !b;


	// next_index is 2*curr_index if the path goes left,
	// 2*curr_index+1 if the path goes right. 
	// so direction_temp is 1 if path goes right, 0 o/w
	Integer reg = d_i.read(attr);
	d_i.refresh();
	Bit temp = reg >= thresh;
	Integer direction_temp(tree_index_sz, 0, PUBLIC);
	direction_temp[0] = temp;
	Integer next_index = (curr_index << 1) + direction_temp;

	// update counters (NOTE: of next_index)
	Integer x = Cpro.read(next_index);
	Cpro.refresh();
	Cpro.write(next_index, x + is_protected);
	Cpro.refresh();
	x = Cun.read(next_index);
	Cun.refresh();
	Cun.write(next_index, x + neg_is_pro);
	Cun.refresh();

	return next_index;
}


// perform first counter update
// either need special case for the root or the leaves so I chose the root
void update_root(ZKRAM<BoolIO<NetIO>> & d_i, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer ONE(tree_index_sz, 1, PUBLIC);
	Integer IS_PRO_IND(ft_index_sz, IS_PROTECTED_INDEX, PUBLIC);
	// ensure counters are incremented by at most 1 by taking only LSB
	Integer temp_is_protected = d_i.read(IS_PRO_IND);
	d_i.refresh();
	Integer is_protected(32, 0, PUBLIC);
	is_protected[0] = temp_is_protected[0];
	// get negation of is_protected
	Integer neg_is_pro(32, 0, PUBLIC);
	neg_is_pro[0] = !temp_is_protected[0];

	// update counters (NOTE: of root)
	Integer temp_p = Cpro.read(ONE);
	Cpro.refresh();
	Cpro.write(ONE, temp_p + is_protected);
	Cpro.refresh();

	Integer temp_u = Cun.read(ONE);
	Cun.refresh();
	Cun.write(ONE, temp_u + neg_is_pro);
	Cun.refresh();
}


// helper functions to compute proper counter increments in EO
// puts the given bit in the least significant bit of a 32 bit 0 Integer 
Integer integerize_bit(const Bit & b) {
	Integer ret(32, 0, PUBLIC);
	ret[0] = b;
	return ret;
}

// truth table for incrementing each counter 
Integer pro_plus_inc_help(const Bit & is_plus_bit, const Bit & is_pro_bit) {
	return integerize_bit(is_plus_bit & is_pro_bit);
}
Integer un_plus_inc_help(Bit & is_plus_bit, Bit & is_pro_bit) {
	return integerize_bit( is_plus_bit & (!is_pro_bit) );
}
Integer pro_minus_inc_help(Bit & is_plus_bit, Bit & is_pro_bit) {
	return integerize_bit( (!is_plus_bit) & is_pro_bit );
}
Integer un_minus_inc_help(Bit & is_plus_bit, Bit & is_pro_bit) {
	return integerize_bit( (!is_plus_bit) & (!is_pro_bit) );
}


// perform first counter update
// either need special case for the root or the leaves so I chose the root
// updates ground truth class counters for equalized odds calculation
void update_root_eo(ZKRAM<BoolIO<NetIO>> & d_i, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, ZKRAM<BoolIO<NetIO>> & C_pro_plus, ZKRAM<BoolIO<NetIO>> & C_un_plus, ZKRAM<BoolIO<NetIO>> & C_pro_minus, ZKRAM<BoolIO<NetIO>> & C_un_minus) {
	Integer ONE(tree_index_sz, 1, PUBLIC);
	Integer IS_PRO_IND(ft_index_sz, IS_PROTECTED_INDEX, PUBLIC);
	// get protected class status
	// ensure counters are incremented by at most 1 by taking only LSB
	Integer temp_is_protected = d_i.read(IS_PRO_IND);
	d_i.refresh();
	Bit is_pro_bit = temp_is_protected[0];

	// get true_class
	Integer TRUE_CLASS_IND(ft_index_sz, TRUE_CLASS_INDEX, PUBLIC);
	Integer temp_true_class = d_i.read(TRUE_CLASS_IND);
	d_i.refresh();
	Bit is_plus_bit = temp_true_class[0];	

	// update counters (of root)
	// helper functions encode the proper truth table (see above)
	Integer temp = C_pro_plus.read(ONE);
	C_pro_plus.refresh();
	C_pro_plus.write(ONE, temp + pro_plus_inc_help(is_plus_bit, is_pro_bit));
	C_pro_plus.refresh();
	temp = C_pro_minus.read(ONE);
	C_pro_minus.refresh();
	C_pro_minus.write(ONE, temp + pro_minus_inc_help(is_plus_bit, is_pro_bit));
	C_pro_minus.refresh();
	temp = C_un_plus.read(ONE);
	C_un_plus.refresh();
	C_un_plus.write(ONE, temp + un_plus_inc_help(is_plus_bit, is_pro_bit));
	C_un_plus.refresh();
	temp = C_un_minus.read(ONE);
	C_un_minus.refresh();
	C_un_minus.write(ONE, temp + un_minus_inc_help(is_plus_bit, is_pro_bit));
	C_un_minus.refresh();
}


// updates ground truth class counters for equalized odds calculation
Integer update_counter_eo(Integer & curr_index, ZKRAM<BoolIO<NetIO>> & d_i, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, ZKRAM<BoolIO<NetIO>> & C_pro_plus, ZKRAM<BoolIO<NetIO>> & C_un_plus, ZKRAM<BoolIO<NetIO>> & C_pro_minus, ZKRAM<BoolIO<NetIO>> & C_un_minus) {
	Integer attr_temp = A.read(curr_index); //index of splitting attr
	Integer attr = slice_integer(0, ft_index_sz, attr_temp);
	Integer thresh = T.read(curr_index); // threshold for split
	
	// next_index is 2*curr_index if the path goes left,
	// 2*curr_index+1 if the path goes right. 
	// so direction_temp is 1 if path goes right, 0 o/w
	Bit temp = d_i.read(attr) >= thresh;
	d_i.refresh();
	Integer direction_temp(tree_index_sz, 0, PUBLIC);
	direction_temp[0] = temp;
	Integer next_index = (curr_index << 1) + direction_temp;

	// look up whether given data point is in protected class or not
	Integer IS_PRO_IND(ft_index_sz, IS_PROTECTED_INDEX, PUBLIC); 
	Integer temp_is_protected = d_i.read(IS_PRO_IND); // 32 bit
	d_i.refresh();
	// d_i is private and stores 32 bit values, but this is effectively boolean
	// so we place the 0th bit of the lookup on a public Integer 0
	Bit is_pro_bit = temp_is_protected[0];

	// get true_class
	Integer TRUE_CLASS_IND(ft_index_sz, TRUE_CLASS_INDEX, PUBLIC);
	Integer temp_true_class = d_i.read(TRUE_CLASS_IND);
	d_i.refresh();
	Bit is_plus_bit = temp_true_class[0];

	// update counters (of next_index)
	// helper functions encode the proper truth table (see above)
	
	Integer reg = C_pro_plus.read(next_index);
	C_pro_plus.refresh();
	C_pro_plus.write(next_index, reg + pro_plus_inc_help(is_plus_bit, is_pro_bit));
	C_pro_plus.refresh();
	reg = C_pro_minus.read(next_index);
	C_pro_minus.refresh();
	C_pro_minus.write(next_index, reg + pro_minus_inc_help(is_plus_bit, is_pro_bit));
	C_pro_minus.refresh();
	reg = C_un_plus.read(next_index);
	C_un_plus.refresh();
	C_un_plus.write(next_index, reg + un_plus_inc_help(is_plus_bit, is_pro_bit));
	C_un_plus.refresh();
	reg = C_un_minus.read(next_index);
	C_un_minus.refresh();
	C_un_minus.write(next_index, reg + un_minus_inc_help(is_plus_bit, is_pro_bit));
	C_un_minus.refresh();
	
	return next_index;
}


// non-optimized gini
Float gini(int node_ind, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer curr_ind(tree_index_sz, node_ind, PUBLIC);
	Integer p = Cpro.read(curr_ind);
	Cpro.refresh();
	Integer u = Cun.read(curr_ind);
	Cun.refresh();


	Float ONE(1.0, PUBLIC);
	Float p_num = Int32ToFloat(p);
	Float u_num = Int32ToFloat(u);
	Float denom = Int32ToFloat(p + u);

	Float temp1 = p_num / denom;
	Float temp2 = u_num / denom;
	Float ret = ONE - (temp1*temp1 + temp2*temp2);

	// convention: if the node is empty, its gini is 0
	Integer INT_ZERO(val_sz, 0, PUBLIC);
	Bit no_samples = (p+u == INT_ZERO);
	Float FLOAT_ZERO(0.0, PUBLIC);
	// if no samples, replace the result with 0
	ret = ret.If(no_samples, FLOAT_ZERO);

	return ret;
}


// non-optimized 
Float f_gain(int node_ind, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer parent_ind(tree_index_sz, node_ind, PUBLIC);
	Integer left_ind(tree_index_sz, node_ind*2, PUBLIC);
	Integer right_ind(tree_index_sz, node_ind*2 + 1, PUBLIC);
	


	Float parent_total = Int32ToFloat(Cpro.read(parent_ind) + Cun.read(parent_ind));
	Cpro.refresh();
	Cun.refresh();
	Float left_total = Int32ToFloat(Cpro.read(left_ind) + Cun.read(left_ind));
	Cpro.refresh();
	Cun.refresh();
	Float right_total = Int32ToFloat(Cpro.read(right_ind) + Cun.read(right_ind));
	Cpro.refresh();
	Cun.refresh();
	Float left_weight = left_total / parent_total;
	Float right_weight = right_total / parent_total;
	return gini(node_ind, Cpro, Cun) - (left_weight*gini(node_ind*2, Cpro, Cun) + right_weight*gini(node_ind*2+1, Cpro, Cun));
}

// (0 samples in node) OR (fgain < threshold)
// left side avoids divide by zero issues for nodes with no samples
Bit fgain_fairness_check(int node_ind, Float & fgain_threshold, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer ZERO(val_sz, 0, PUBLIC);
	Integer parent_ind(tree_index_sz, node_ind, PUBLIC);
	Integer samples_in_parent = Cpro.read(parent_ind) + Cun.read(parent_ind);
	Cpro.refresh();
	Cun.refresh();
	return ((samples_in_parent) == ZERO) | f_gain(node_ind, Cpro, Cun).less_than(fgain_threshold);
}

// optimized fairness check based on rearranging the inequality:
// fgain(node) < threshold
Bit op_f_check(int node_ind, Integer & int_fgain_threshold, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	Integer parent_ind(tree_index_sz, node_ind, PUBLIC);
	Integer left_ind(tree_index_sz, node_ind*2, PUBLIC);
	Integer right_ind(tree_index_sz, node_ind*2 + 1, PUBLIC);

	Integer Pp = Cpro.read(parent_ind);
	Integer Pu = Cun.read(parent_ind);
	Cpro.refresh();
	Cun.refresh();
	Integer Rp = Cpro.read(right_ind);
	Integer Ru = Cun.read(right_ind);
	Cpro.refresh();
	Cun.refresh();
	Integer Lp = Cpro.read(left_ind);
	Integer Lu = Cun.read(left_ind);
	Cpro.refresh();
	Cun.refresh();

	Integer P = Pp + Pu; //parent total samples
	Integer P_prod = Pp * Pu;
	Integer R = Rp + Ru; // right total samples
	Integer R_prod = Rp * Ru;
	Integer L = Lp + Lu;	// left total samples
	Integer L_prod = Lp * Lu;

	// computing equation for optimized fairness check
	// in total, the equation is:
	// 4 * (RLPuPp - P(RuRpL + LuLpR)) < thresh * P^2 * RL
	// also we multiply each side by 1000 so that the threshold can be represented as an integer
	// this is just a rewrite of the equation in Alg 4 that avoids division
	Integer RL = R * L;
	Integer FOUR_THOUSAND(val_sz, 4000, PUBLIC);
	// compute left hand side of equation
	Integer inner_term = R_prod * L + L_prod * R;
	inner_term = inner_term * P;
	Integer outer_term = RL * P_prod;
	Integer eq_lhs = FOUR_THOUSAND * (outer_term - inner_term);

	// right hand side
	Integer eq_rhs = int_fgain_threshold * P * P * RL;

	Integer ZERO(val_sz, 0, PUBLIC);
	return (P==ZERO) | (R==ZERO) | (L==ZERO) | (eq_lhs < eq_rhs);
}

// precompute Cpro[i] + Cun[i], Cpro[i] * Cun[i] for all nodes
void precompute_sum_prod(int num_nodes, vector<Integer> & sum_vec, vector<Integer> & prod_vec, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	// pad zeroth entry for indexing consistency
	Integer ZERO(val_sz, 0, PUBLIC);
	sum_vec.push_back(ZERO);
	prod_vec.push_back(ZERO); 
	// compute sum and products
	for (int i=1; i<num_nodes; i++) {
		Integer ind(val_sz, i, PUBLIC);
		Integer p = Cpro.read(ind);
		Integer u = Cun.read(ind);
		Cpro.refresh();
		Cun.refresh();
		sum_vec.push_back(p + u);
		prod_vec.push_back(p * u);
	}
}

// like op_f_check but taking advantage of precomputed Cpro[i] + Cun[i], Cpro[i] * Cun[i] for all nodes
Bit pre_op_f_check(int node_ind, Integer & int_fgain_threshold, vector<Integer> sum_vec, vector<Integer> prod_vec) {
	Integer parent_ind(tree_index_sz, node_ind, PUBLIC);
	Integer left_ind(tree_index_sz, node_ind*2, PUBLIC);
	Integer right_ind(tree_index_sz, node_ind*2 + 1, PUBLIC);

	Integer P = sum_vec[node_ind]; //parent total samples
	Integer P_prod = prod_vec[node_ind];
	Integer R = sum_vec[node_ind*2 + 1]; // right total samples
	Integer R_prod = prod_vec[node_ind*2 + 1];
	Integer L = sum_vec[node_ind*2];	// left total samples
	Integer L_prod = prod_vec[node_ind*2];

	// computing equation for optimized fairness check
	// in total, the equation is:
	// 4 * (RLPuPp - P(RuRpL + LuLpR)) < thresh * P^2 * RL
	// also we multiply each side by 1000 so that the threshold can be represented as an integer
	// this is just a rewrite of the equation in Alg 4 that avoids division
	Integer RL = R * L;
	Integer FOUR_THOUSAND(val_sz, 4000, PUBLIC);
	// compute left hand side of equation
	Integer inner_term = R_prod * L + L_prod * R;
	inner_term = inner_term * P;
	Integer outer_term = RL * P_prod;
	Integer eq_lhs = FOUR_THOUSAND * (outer_term - inner_term);

	// right hand side
	Integer eq_rhs = int_fgain_threshold * P * P * RL;

	// if no samples in node, no need to threshold fairness
	Integer ZERO(val_sz, 0, PUBLIC);
	return (P==ZERO) | (R==ZERO) | (L==ZERO) | (eq_lhs < eq_rhs);
}

// convention -- tree with only root has height 0
void print_leaf_ct(int ls_len, int dt_height, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	int num_lf = 1 << dt_height;
	int first_lf = ls_len - num_lf;
	// iterate through leaf indices
	for (int i=first_lf; i<ls_len; i++) {
		Integer ind(tree_index_sz, i, PUBLIC);
		cout << "(" << Cpro.read(ind).reveal<int32_t>() << ", " << Cun.read(ind).reveal<int32_t>() << ") ";
		Cpro.refresh();
		Cun.refresh();
	}
	cout << "\n";
}

void print_ct(int dt_height, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun) {
	for (int i=0; i<=dt_height; i++) {
		int curr_layer_start = 1 << i;
		int next_layer_start = 1 << (i+1);
		for (int j=curr_layer_start; j<next_layer_start; j++) {
			Integer ind(tree_index_sz, j, PUBLIC);
			cout << "(" << Cpro.read(ind).reveal<int32_t>() << ", " << Cun.read(ind).reveal<int32_t>() << ") ";
			Cpro.refresh();
			Cun.refresh();
		}
		cout << "\n";
	}
}

void print_ct_eo(int ls_len, int dt_height, ZKRAM<BoolIO<NetIO>> & Cpro, ZKRAM<BoolIO<NetIO>> & Cun, ZKRAM<BoolIO<NetIO>> & Cplus, ZKRAM<BoolIO<NetIO>> & Cminus) {
	for (int i=0; i<=dt_height; i++) {
		int curr_layer_start = 1 << i;
		int next_layer_start = 1 << (i+1);
		for (int j=curr_layer_start; j<next_layer_start; j++) {
			Integer ind(tree_index_sz, j, PUBLIC);
			cout << "(" << Cpro.read(ind).reveal<int32_t>() << ", " << Cun.read(ind).reveal<int32_t>() << " | " << Cplus.read(ind).reveal<int32_t>() << ", " << Cminus.read(ind).reveal<int32_t>() << ") ";
			Cpro.refresh();
			Cun.refresh();
		}
		cout << "\n";
	}
}

// prove decision tree fair training for demographic parity
void cpof_dp(int party, float f_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, int num_row, int num_col, vector<vector<int32_t>> & D) {
	// Initialize counting trees
	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	//print_leaf_ct(16, 3, *Cpro, *Cun);
	
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	// Count class distribution
	
	// outer loop: iterate through each data point
	// inner loop: iterate through each level of the tree
	for (int i=0; i<num_row; i++) {
		// load data point into d_i ZKRAM
		//cout << "i: " << i << "\n";
		for (int k=0; k<num_col; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			Integer data(val_sz, D[i][k], ALICE);
			d_i->write(ind, data);
			d_i->refresh();
		}

		update_root(*d_i, A, T, *Cpro, *Cun);
		//print_ct(dt_height, *Cpro, *Cun);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter(curr_index, *d_i, A, T, *Cpro, *Cun);
			curr_index = temp;
			//print_ct(dt_height, *Cpro, *Cun);
		}
		//print_ct(dt_height, *Cpro, *Cun);

	}
	//print_ct(dt_height, *Cpro, *Cun);

	// prove fairness gain bound
	int max_interior_node = 1 << dt_height;
	Float FAIRNESS_GAIN_BOUND(f_gain_bound, PUBLIC);
	Bit fgain_is_fair(1, PUBLIC);
	for (int i=1; i<max_interior_node; i++) {
		Bit curr_check = fgain_fairness_check(i, FAIRNESS_GAIN_BOUND, *Cpro, *Cun);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		fgain_is_fair = curr_check & fgain_is_fair;
	}
	cout << "Fairness gain bound holds?: " << fgain_is_fair.reveal() << "\n";

	d_i->check();
	Cpro->check();
	Cun->check();
	delete d_i;
	delete Cpro;
	delete Cun;
}


void print_help(vector<Integer> x) {
	cout << "[";
	for (int i=0, max=x.size(); i<max; i++) {
		cout << " " << x[i].reveal<uint32_t>() << " ";
	}
	cout << "]\n";
}

// DP fairness gain thresholding
// with optimized fairness check
void op_cpof_dp(int party, float f_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, int num_row, int num_col, vector<vector<int32_t>> & D) {
	// Initialize counting trees
	double c_load_data = 0.0;
	double c_update_ct = 0.0;
	auto init = clock_start();
	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	//print_leaf_ct(16, 3, *Cpro, *Cun);
	
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);
	double init_ct = time_from(init);
	// Count class distribution
	
	// outer loop: iterate through each data point
	// inner loop: iterate through each level of the tree
	for (int i=0; i<num_row; i++) {
		// load data point into d_i ZKRAM
		auto t2 = clock_start();
		for (int k=0; k<num_col; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			Integer data(val_sz, D[i][k], ALICE);
			d_i->write(ind, data);
			d_i->refresh();
		}
		double ld = time_from(t2);
		c_load_data = c_load_data + ld;

		auto t3 = clock_start();
		update_root(*d_i, A, T, *Cpro, *Cun);
		//print_ct(16, 3, *Cpro, *Cun);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter(curr_index, *d_i, A, T, *Cpro, *Cun);
			curr_index = temp;
			//print_ct(16, 3, *Cpro, *Cun);
		}
		double uc = time_from(t3);
		c_update_ct += uc;

	}
	//print_ct(dt_height, *Cpro, *Cun);


	auto t4 = clock_start();
	// prove fairness gain bound
	int max_interior_node = 1 << dt_height;
	int temp_int_fgain_bound = (int) 1000 * f_gain_bound;
	Integer INT_FGAIN_BOUND(val_sz, temp_int_fgain_bound, PUBLIC);
	//Float FAIRNESS_GAIN_BOUND(f_gain_bound, PUBLIC);
	Bit fgain_is_fair(1, PUBLIC);

	vector<Integer> sum_vec;
	vector<Integer> prod_vec;
	precompute_sum_prod(1 << (dt_height+1), sum_vec, prod_vec, *Cpro, *Cun);

	for (int i=1; i<max_interior_node; i++) {
		Bit curr_check = pre_op_f_check(i, INT_FGAIN_BOUND, sum_vec, prod_vec);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		fgain_is_fair = curr_check & fgain_is_fair;
	}
	cout << "Fairness gain bound holds?: " << fgain_is_fair.reveal() << "\n";
	double c_fair_check = time_from(t4);

	auto t5 = clock_start();
	d_i->check();
	Cpro->check();
	Cun->check();	
	double c_zk_check = time_from(t5);

	cout << "init_ct: " << init_ct * 1e-6 << endl;
	cout << "c_load_data: " << c_load_data * 1e-6 << endl;
	cout << "c_update_ct: " << c_update_ct * 1e-6 << endl;
	cout << "c_fair_check: " << c_fair_check * 1e-6 << endl;
	cout << "c_zk_check: " << c_zk_check * 1e-6 << endl;

	delete d_i;
	delete Cpro;
	delete Cun;
}



// EO info gain thresholding
void cpof_eo(int party, float eo_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, int num_row, int num_col, vector<vector<int32_t>> & D) {
	// Initialize counting trees
	ZKRAM<BoolIO<NetIO>> * C_un_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_un_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * C_pro_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_pro_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	// Count class distribution
	
	// outer loop: iterate through each data point
	// inner loop: iterate through each level of the tree
	for (int i=0; i<num_row; i++) {
		// load data point into d_i ZKRAM
		for (int k=0; k<num_col; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			Integer data(val_sz, D[i][k], ALICE);
			d_i->write(ind, data);
			d_i->refresh();
		}
		
		update_root_eo(*d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		//print_ct_eo(16, 3, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter_eo(curr_index, *d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
			curr_index = temp;
			//print_ct_eo(16, 3, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		}

	}

	// prove eo gain bound
	int max_interior_node = 1 << dt_height;
	Float EO_GAIN_BOUND(eo_gain_bound, PUBLIC);
	Bit eogain_is_fair(1, PUBLIC);
	Bit plus_is_fair(1, PUBLIC);
	Bit minus_is_fair(1, PUBLIC);
	for (int i=1; i<max_interior_node; i++) {
		Bit plus_curr_check = fgain_fairness_check(i, EO_GAIN_BOUND, *C_pro_plus, *C_un_plus);
		Bit minus_curr_check = fgain_fairness_check(i, EO_GAIN_BOUND, *C_pro_minus, *C_un_minus);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		plus_is_fair = plus_curr_check & plus_is_fair;
		minus_is_fair = minus_curr_check & minus_is_fair;
	}
	eogain_is_fair = plus_is_fair & minus_is_fair;
	cout << "EO gain bound holds?: " << eogain_is_fair.reveal() << "\n";
	
	d_i->check();
	C_pro_plus->check();
	C_un_plus->check();
	C_pro_minus->check();
	C_un_minus->check();
	delete d_i;
	delete C_pro_plus;
	delete C_un_plus;
	delete C_pro_minus;
	delete C_un_minus;
}


// EO info gain thresholding
// optimized version
void op_cpof_eo(int party, float eo_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, int num_row, int num_col, vector<vector<int32_t>> & D) {
	// Initialize counting trees
	double c_load_data = 0.0;
	double c_update_ct = 0.0;
	auto init = clock_start();
	ZKRAM<BoolIO<NetIO>> * C_un_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_un_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * C_pro_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_pro_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);
	double init_ct = time_from(init);
	// Count class distribution
	
	// outer loop: iterate through each data point
	// inner loop: iterate through each level of the tree
	for (int i=0; i<num_row; i++) {
		// load data point into d_i ZKRAM
		auto t2 = clock_start();
		for (int k=0; k<num_col; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			Integer data(val_sz, D[i][k], ALICE);
			d_i->write(ind, data);
			d_i->refresh();
		}
		double ld = time_from(t2);
		c_load_data = c_load_data + ld;

		auto t3 = clock_start();
		update_root_eo(*d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		//print_ct_eo(16, 3, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter_eo(curr_index, *d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
			curr_index = temp;
			//print_ct_eo(16, 3, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		}
		double uc = time_from(t3);
		c_update_ct += uc;

	}

	auto t4 = clock_start();
	// prove eo gain bound
	int max_interior_node = 1 << dt_height;
	int temp_int_eogain_bound = (int) 1000 * eo_gain_bound;
	Integer INT_EOGAIN_BOUND(val_sz, temp_int_eogain_bound, PUBLIC);
	//Float EO_GAIN_BOUND(eo_gain_bound, PUBLIC);

	vector<Integer> plus_sum_vec;
	vector<Integer> plus_prod_vec;
	precompute_sum_prod(1 << (dt_height+1), plus_sum_vec, plus_prod_vec, *C_pro_plus, *C_un_plus);

	vector<Integer> minus_sum_vec;
	vector<Integer> minus_prod_vec;
	precompute_sum_prod(1 << (dt_height+1), minus_sum_vec, minus_prod_vec, *C_pro_minus, *C_un_minus);

	Bit eogain_is_fair(1, PUBLIC);
	Bit plus_is_fair(1, PUBLIC);
	Bit minus_is_fair(1, PUBLIC);
	for (int i=1; i<max_interior_node; i++) {
		Bit plus_curr_check = pre_op_f_check(i, INT_EOGAIN_BOUND, plus_sum_vec, plus_prod_vec);
		Bit minus_curr_check = pre_op_f_check(i, INT_EOGAIN_BOUND, minus_sum_vec, minus_prod_vec);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		plus_is_fair = plus_curr_check & plus_is_fair;
		minus_is_fair = minus_curr_check & minus_is_fair;
	}
	eogain_is_fair = plus_is_fair & minus_is_fair;
	cout << "EO gain bound holds?: " << eogain_is_fair.reveal() << "\n";
	double c_fair_check = time_from(t4);

	auto t5 = clock_start();
	d_i->check();
	C_pro_plus->check();
	C_un_plus->check();
	C_pro_minus->check();
	C_un_minus->check();
	double c_zk_check = time_from(t5);

	cout << "init_ct: " << init_ct * 1e-6 << endl;
	cout << "c_load_data: " << c_load_data * 1e-6 << endl;
	cout << "c_update_ct: " << c_update_ct * 1e-6 << endl;
	cout << "c_fair_check: " << c_fair_check * 1e-6 << endl;
	cout << "c_zk_check: " << c_zk_check * 1e-6 << endl;
	delete d_i;
	delete C_pro_plus;
	delete C_un_plus;
	delete C_pro_minus;
	delete C_un_minus;
}


void commit_to_data(int nrow, int ncol, vector<vector<int32_t>> & D, vector<vector<Integer>> & comm) {
	for (int i=0; i<nrow; i++) {
		vector<Integer> sample;
		for (int j=0; j<ncol; j++) {
			Integer val(val_sz, D[i][j], ALICE);
			sample.push_back(val);
		}
		comm.push_back(sample);
	}
}

// proves fairness of a single tree in the forest
// modified version of op_cpof_dp with these exceptions:
// in RF, data is pre-committed, so D consists of emp::Integers
// each tree only considers a subset of the features in the dataset -- so attr_indices gives the indices of features from D that are considered by the present tree
// M is the size of attr_indices
// similarly, each tree recieves a random sample of data rows for training -- these rows are indexed by samp_indices
// L is the size of samp_indices
void rf_dp_help(int party, float f_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, vector<int> attr_indices, int M, vector<int> samp_indices, int L, vector<vector<Integer>> & D) {
	// Initialize counting trees
	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
		
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	// Count class distribution
	
	// outer loop: iterate through sampled data points (given by samp_indices)
	// inner loop: iterate through each level of the tree
	for (int i=0; i<L; i++) {
		// load data point into d_i ZKRAM (only take sampled attributes -- given by attr_indices)
		for (int k=0; k<M; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			//Integer data(val_sz, D[samp_indices[i]][attr_indices[k]], ALICE);
			d_i->write(ind, D[samp_indices[i]][attr_indices[k]]);
			d_i->refresh();
		}
		
		update_root(*d_i, A, T, *Cpro, *Cun);
		//print_ct(16, 3, *Cpro, *Cun);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter(curr_index, *d_i, A, T, *Cpro, *Cun);
			curr_index = temp;
			//print_ct(16, 3, *Cpro, *Cun);
		}
		//print_ct(16, 3, *Cpro, *Cun);

	}
	//print_ct(dt_height, *Cpro, *Cun);



	// prove fairness gain bound
	int max_interior_node = 1 << dt_height;
	int temp_int_fgain_bound = (int) 1000 * f_gain_bound;
	Integer INT_FGAIN_BOUND(val_sz, temp_int_fgain_bound, PUBLIC);
	//Float FAIRNESS_GAIN_BOUND(f_gain_bound, PUBLIC);
	Bit fgain_is_fair(1, PUBLIC);

	//cout << "1\n";
	vector<Integer> sum_vec;
	vector<Integer> prod_vec;
	precompute_sum_prod(1 << (dt_height+1), sum_vec, prod_vec, *Cpro, *Cun);
	//print_help(sum_vec);
	//print_help(prod_vec);
	//cout << "2\n";

	for (int i=1; i<max_interior_node; i++) {
		Bit curr_check = pre_op_f_check(i, INT_FGAIN_BOUND, sum_vec, prod_vec);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		fgain_is_fair = curr_check & fgain_is_fair;
	}
	cout << "Fairness gain bound holds?: " << fgain_is_fair.reveal() << "\n";

	d_i->check();
	Cpro->check();
	Cun->check();	
	delete d_i;
	delete Cpro;
	delete Cun;
}

// see rf_dp_help, the same differences apply here
void rf_eo_help(int party, float eo_gain_bound, int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T, vector<int> attr_indices, int M, vector<int> samp_indices, int L, vector<vector<Integer>> & D) {
	// Initialize counting trees
	ZKRAM<BoolIO<NetIO>> * C_un_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_un_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * C_pro_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_pro_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	
	// Initialize ZKRAM data container
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	// Count class distribution

	// outer loop: iterate through sampled data points (given by samp_indices)
	// inner loop: iterate through each level of the tree
	for (int i=0; i<L; i++) {
		// load data point into d_i ZKRAM (only take sampled attributes -- given by attr_indices)
		for (int k=0; k<M; k++) {
			Integer ind(ft_index_sz, k, PUBLIC);
			//Integer data(val_sz, D[samp_indices[i]][attr_indices[k]], ALICE);
			d_i->write(ind, D[samp_indices[i]][attr_indices[k]]);
			d_i->refresh();
		}
		
		update_root_eo(*d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
		//print_ct(16, 3, *Cpro, *Cun);
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<dt_height; j++) {
			Integer temp = update_counter_eo(curr_index, *d_i, A, T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
			curr_index = temp;
		}
	}


	// prove eo gain bound
	int max_interior_node = 1 << dt_height;
	int temp_int_eogain_bound = (int) 1000 * eo_gain_bound;
	Integer INT_EOGAIN_BOUND(val_sz, temp_int_eogain_bound, PUBLIC);
	//Float EO_GAIN_BOUND(eo_gain_bound, PUBLIC);

	vector<Integer> plus_sum_vec;
	vector<Integer> plus_prod_vec;
	precompute_sum_prod(1 << (dt_height+1), plus_sum_vec, plus_prod_vec, *C_pro_plus, *C_un_plus);

	vector<Integer> minus_sum_vec;
	vector<Integer> minus_prod_vec;
	precompute_sum_prod(1 << (dt_height+1), minus_sum_vec, minus_prod_vec, *C_pro_minus, *C_un_minus);

	Bit eogain_is_fair(1, PUBLIC);
	Bit plus_is_fair(1, PUBLIC);
	Bit minus_is_fair(1, PUBLIC);
	for (int i=1; i<max_interior_node; i++) {
		Bit plus_curr_check = pre_op_f_check(i, INT_EOGAIN_BOUND, plus_sum_vec, plus_prod_vec);
		Bit minus_curr_check = pre_op_f_check(i, INT_EOGAIN_BOUND, minus_sum_vec, minus_prod_vec);
		//cout << "i: " << i << "    check: " << curr_check.reveal() << "\n";
		plus_is_fair = plus_curr_check & plus_is_fair;
		minus_is_fair = minus_curr_check & minus_is_fair;
	}
	eogain_is_fair = plus_is_fair & minus_is_fair;
	cout << "EO gain bound holds?: " << eogain_is_fair.reveal() << "\n";
	
	d_i->check();
	C_pro_plus->check();
	C_un_plus->check();
	C_pro_minus->check();
	C_un_minus->check();
	delete d_i;
	delete C_pro_plus;
	delete C_un_plus;
	delete C_pro_minus;
	delete C_un_minus;
}

// prove fairness of a random forest
// N: number of trees in random forest
// M: number of sampled attributes per tree
// L: number of training samples per tree
void rf_dp(int party, float f_gain_bound, int h, int N, int M, int L, vector<ROZKRAM<BoolIO<NetIO>> *> & As, vector<ROZKRAM<BoolIO<NetIO>> *> & Ts, vector<vector<int>> & attr_is, vector<vector<int>> & samp_is, int tot_rows, int tot_cols, vector<vector<int32_t>> & D) {
	vector<vector<Integer>> comm;
	commit_to_data(tot_rows, tot_cols, D, comm);
	for (int i=0; i<N; i++){
		ROZKRAM<BoolIO<NetIO>> * A = As[i];
		ROZKRAM<BoolIO<NetIO>> * T = Ts[i];
		vector<int> attr_indices = attr_is[i];
		vector<int> samp_indices = samp_is[i];
		rf_dp_help(party, f_gain_bound, h, *A, *T, attr_indices, M, samp_indices, L, comm);
		A->check();
		T->check();
	}
}

void rf_eo(int party, float f_gain_bound, int h, int N, int M, int L, vector<ROZKRAM<BoolIO<NetIO>> *> & As, vector<ROZKRAM<BoolIO<NetIO>> *> & Ts, vector<vector<int>> & attr_is, vector<vector<int>> & samp_is, int tot_rows, int tot_cols, vector<vector<int32_t>> & D) {
	vector<vector<Integer>> comm;
	commit_to_data(tot_rows, tot_cols, D, comm);
	for (int i=0; i<N; i++){
		ROZKRAM<BoolIO<NetIO>> * A = As[i];
		ROZKRAM<BoolIO<NetIO>> * T = Ts[i];
		vector<int> attr_indices = attr_is[i];
		vector<int> samp_indices = samp_is[i];
		rf_eo_help(party, f_gain_bound, h, *A, *T, attr_indices, M, samp_indices, L, comm);
		A->check();
		T->check();
	}
}