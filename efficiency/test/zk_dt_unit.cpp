#include<chrono>
#include "cpof/zk_dt.cpp"

// tree w/ heigh 4
ROZKRAM<BoolIO<NetIO>>* init_example_A(int party) {
	vector<Integer> data;
	data.push_back(Integer(val_sz, 0, PUBLIC)); // not meaningful
	data.push_back(Integer(val_sz, 3, PUBLIC)); //1 (root)
	data.push_back(Integer(val_sz, 2, PUBLIC)); //2
	data.push_back(Integer(val_sz, 4, PUBLIC)); //3
	data.push_back(Integer(val_sz, 5, PUBLIC)); //4
	data.push_back(Integer(val_sz, 4, PUBLIC)); //5
	data.push_back(Integer(val_sz, 5, PUBLIC)); //6
	data.push_back(Integer(val_sz, 2, PUBLIC)); //7
	data.push_back(Integer(val_sz, 0, PUBLIC)); //8 neg (first leaf)
	data.push_back(Integer(val_sz, 0, PUBLIC)); //9 neg
	data.push_back(Integer(val_sz, 0, PUBLIC)); //10 neg
	data.push_back(Integer(val_sz, 1, PUBLIC)); //11 pos
	data.push_back(Integer(val_sz, 1, PUBLIC)); //12 pos
	data.push_back(Integer(val_sz, 0, PUBLIC)); //13 neg
	data.push_back(Integer(val_sz, 1, PUBLIC)); //14 pos
	data.push_back(Integer(val_sz, 1, PUBLIC)); //15 pos

	ROZKRAM<BoolIO<NetIO>> *A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
	A->init(data);
	return A;
}


ROZKRAM<BoolIO<NetIO>>* init_example_T(int party) {
	vector<Integer> data;
	data.push_back(Integer(val_sz, 0, PUBLIC)); // not meaningful
	data.push_back(Integer(val_sz, 15, PUBLIC)); //1 (root)
	data.push_back(Integer(val_sz, 10, PUBLIC)); //2
	data.push_back(Integer(val_sz, 18, PUBLIC)); //3
	data.push_back(Integer(val_sz, 5, PUBLIC)); //4
	data.push_back(Integer(val_sz, 7, PUBLIC)); //5
	data.push_back(Integer(val_sz, 20, PUBLIC)); //6
	data.push_back(Integer(val_sz, 5, PUBLIC)); //7
	data.push_back(Integer(val_sz, 0, PUBLIC)); //8 neg (first leaf)
	data.push_back(Integer(val_sz, 0, PUBLIC)); //9 neg
	data.push_back(Integer(val_sz, 0, PUBLIC)); //10 neg
	data.push_back(Integer(val_sz, 1, PUBLIC)); //11 pos
	data.push_back(Integer(val_sz, 1, PUBLIC)); //12 pos
	data.push_back(Integer(val_sz, 0, PUBLIC)); //13 neg
	data.push_back(Integer(val_sz, 1, PUBLIC)); //14 pos
	data.push_back(Integer(val_sz, 1, PUBLIC)); //15 pos

	ROZKRAM<BoolIO<NetIO>> *T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
	T->init(data);
	return T;
}

vector<vector<int32_t>> init_example_D() {
	vector<vector<int32_t>> D;
	
	D.push_back({0,0,0,0,0,0});
	D.push_back({1,0,0,0,0,0});
	D.push_back({0,100,100,100,100,100});
	D.push_back({1,100,100,100,100,100});
	return D;
}

vector<vector<int32_t>> generate_toy_data(int nrow, int ncol) {
	vector<int32_t> v;
	for (int col=0; col<ncol; col++) {
		v.push_back(0);
	}
	vector<vector<int32_t>> D;
	for (int row=0; row<nrow; row++) {
		D.push_back(v);
	}
	return D;
}

void generate_toy_tree(int dt_height, ROZKRAM<BoolIO<NetIO>> & A, ROZKRAM<BoolIO<NetIO>> & T) { 
	vector<Integer> A_data;
	vector<Integer> T_data;
	int max_node = 1 << (dt_height+1);
	Integer ZERO(val_sz, 0, PUBLIC);
	A_data.push_back(ZERO); // 0th entry has no meaning
	T_data.push_back(ZERO);
	// dummy values
	//int32_t a=3;
	//int32_t t=100;
	for (int i=1; i<max_node; i++) {
		//Integer x(val_sz, 3, ALICE);
		//Integer y(val_sz, 100, ALICE);
		//A_data.push_back(x);
		//T_data.push_back(y);
		A_data.push_back(Integer(val_sz, 3, ALICE));
		T_data.push_back(Integer(val_sz, 100, ALICE));
	}
	A.init(A_data);
	T.init(T_data);
}

// uses ZKRAMs rather than RORAMs
void generate_toy_tree_v2(int dt_height, ZKRAM<BoolIO<NetIO>> & A, ZKRAM<BoolIO<NetIO>> & T) { 
	vector<Integer> A_data;
	vector<Integer> T_data;
	int max_node = 1 << (dt_height+1);
	Integer ZERO(val_sz, 0, PUBLIC);
	A_data.push_back(ZERO); // 0th entry has no meaning
	T_data.push_back(ZERO);
	// dummy values
	int32_t a=3;
	int32_t t=100;
	for (int i=0; i<max_node; i++) {
		//A_data.push_back(Integer(val_sz, a, ALICE));
		//T_data.push_back(Integer(val_sz, t, ALICE));

		Integer ind(tree_index_sz, i, PUBLIC);
		Integer x(val_sz, a, ALICE);
		Integer y(val_sz, t, ALICE);
		A.write(ind, x);
		T.write(ind, y);
	}
	//A.init(A_data);
	//T.init(T_data);
}

float microsec_to_sec(int x) {
	float ret = (float) x;
	ret = ret / 1000000;
	return ret;
}


void test_update_root(int party, int iters) {
	cout << "running test_update_root(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}
	cout << "d_i check 1\n";
	d_i->check();

	for (int i=0; i<iters; i++) {
		// load data point into d_i ZKRAM
		//cout << "i: " << i << "\n";
		update_root(*d_i, *A, *T, *Cpro, *Cun);
	}
	//cout << "d_i check 2\n";
	//d_i->check();

	Integer ONE_IND(tree_index_sz, 1, PUBLIC);
	Integer res_pro = Cpro->read(ONE_IND);
	Integer res_un = Cun->read(ONE_IND);
	Cpro->refresh();
	Cun->refresh();

	int num_nodes = num_nodes_from_ht(TREE_HT);
	bool FINE = 1;
	bool un_test = 0;
	bool pro_test = 0;
	for (int i=0; i<num_nodes; i++) {
		Integer IND(tree_index_sz, i, PUBLIC);
		Integer res_pro = Cpro->read(IND);
		Integer res_un = Cun->read(IND);
		Cpro->refresh();
		Cun->refresh();
		if (i==1) {
			un_test = res_un.reveal<int32_t>() == iters;
			pro_test = res_pro.reveal<int32_t>() == 0;
		} else {
			un_test = res_un.reveal<int32_t>() == 0;
			pro_test = res_pro.reveal<int32_t>() == 0;
		}

		FINE = FINE && un_test && pro_test;
	}

	cout << "pro: " << res_pro.reveal<int32_t>() << "\n";
	cout << "un: " << res_un.reveal<int32_t>() << "\n";
	cout << "test_update_root(): " << (FINE?"PASS":"FAIL") << "\n";
	
	
	//cout << "check A: \n";
	//A->check();
	//cout << "check T: \n";
	//T->check();
	//cout << "check d_i: \n";
	//d_i->check();
	//cout << "check Cpro: \n";
	//Cpro->check();
	cout << "check Cun: \n";
	Cun->check();
	delete A;
	delete T;
	delete Cpro;
	delete Cun;
	delete d_i;
}

void test_write_di(int party, int iters) {
	cout << "running test_write_di(). iters: " << iters << "\n";
	//ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	//ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	//generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	//ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	//ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}
	cout << "d_i check 1\n";
	d_i->check();

	delete d_i;
}

void test_update_counter_single(int party, int iters) {
	cout << "running test_update_counter_single(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	for (int i=0; i<iters; i++) {
		Integer temp = update_counter(curr_index, *d_i, *A, *T, *Cpro, *Cun);
		//curr_index = temp;
	}

	Integer TWO_IND(tree_index_sz, 2, PUBLIC);
	Integer two_pro = Cpro->read(TWO_IND);
	Integer two_un = Cun->read(TWO_IND);
	Cpro->refresh();
	Cun->refresh();
	Integer THREE_IND(tree_index_sz, 2, PUBLIC);
	Integer three_pro = Cpro->read(THREE_IND);
	Integer three_un = Cun->read(THREE_IND);
	Cpro->refresh();
	Cun->refresh();

	int num_nodes = num_nodes_from_ht(TREE_HT);
	bool FINE = 1;
	bool un_test = 0;
	bool pro_test = 0;
	for (int i=0; i<num_nodes; i++) {
		Integer IND(tree_index_sz, i, PUBLIC);
		Integer res_pro = Cpro->read(IND);
		Integer res_un = Cun->read(IND);
		Cpro->refresh();
		Cun->refresh();
		if (i==2) {
			un_test = res_un.reveal<int32_t>() == iters;
			pro_test = res_pro.reveal<int32_t>() == 0;
		} else {
			un_test = res_un.reveal<int32_t>() == 0;
			pro_test = res_pro.reveal<int32_t>() == 0;
		}

		FINE = FINE && un_test && pro_test;
	}

	//cout << "2p: " << two_pro.reveal<int32_t>();
	//cout << " 2u: " << two_un.reveal<int32_t>() << "\n";
	//cout << "3p: " << three_pro.reveal<int32_t>();
	//cout << " 3u: " << three_un.reveal<int32_t>() << "\n";
	cout << "test_update_counter_single(): " << (FINE?"PASS":"FAIL") << "\n";

	cout << "check A: \n";
	A->check();
	cout << "check T: \n";
	T->check();
	cout << "check d_i: \n";
	d_i->check();
	cout << "check Cpro: \n";
	Cpro->check();
	cout << "check Cun: \n";
	Cun->check();	
	//print_ct(TREE_HT, *Cpro, *Cun);
	delete A;
	delete T;
	delete Cpro;
	delete Cun;
	delete d_i;
}


void test_update_counter_tree(int party, int iters) {
	cout << "running test_update_counter_tree(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	ZKRAM<BoolIO<NetIO>> * Cpro = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * Cun = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	for (int i=0; i<iters; i++) {
		if ((i % (iters/10))==0) {cout << i << "/" << iters <<"\n";}
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<TREE_HT; j++) {
			Integer temp = update_counter(curr_index, *d_i, *A, *T, *Cpro, *Cun);
			curr_index = temp;
			//print_ct(dt_height, *Cpro, *Cun);
		}
	}

	/*
	int num_nodes = num_nodes_from_ht(TREE_HT);
	bool FINE = 1;
	bool un_test = 0;
	bool pro_test = 0;
	for (int i=0; i<num_nodes; i++) {
		Integer IND(tree_index_sz, i, PUBLIC);
		Integer res_pro = Cpro->read(IND);
		Integer res_un = Cun->read(IND);
		if (i==2) {
			un_test = res_un.reveal<int32_t>() == iters;
			pro_test = res_pro.reveal<int32_t>() == 0;
		} else {
			un_test = res_un.reveal<int32_t>() == 0;
			pro_test = res_pro.reveal<int32_t>() == 0;
		}

		FINE = FINE && un_test && pro_test;
	}
	*/

	//cout << "test_update_counter_single(): " << (FINE?"PASS":"FAIL") << "\n";
	
	print_ct(TREE_HT, *Cpro, *Cun);

	cout << "check A: \n";
	A->check();
	cout << "check T: \n";
	T->check();
	cout << "check d_i: \n";
	d_i->check();
	cout << "check Cpro: \n";
	Cpro->check();
	cout << "check Cun: \n";
	Cun->check();	


	delete A;
	delete T;
	delete Cpro;
	delete Cun;
	delete d_i;
}


void test_update_eo_counter_tree(int party, int iters) {
	cout << "running test_update_counter_tree(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	ZKRAM<BoolIO<NetIO>> * C_un_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_un_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * C_pro_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_pro_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	for (int i=0; i<iters; i++) {
		if ((i % (iters/10))==0) {cout << i << "/" << iters <<"\n";}
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<TREE_HT; j++) {
			Integer temp = update_counter_eo(curr_index, *d_i, *A, *T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
			curr_index = temp;
			//print_ct(dt_height, *Cpro, *Cun);
		}
	}

	/*
	int num_nodes = num_nodes_from_ht(TREE_HT);
	bool FINE = 1;
	bool un_test = 0;
	bool pro_test = 0;
	for (int i=0; i<num_nodes; i++) {
		Integer IND(tree_index_sz, i, PUBLIC);
		Integer res_pro = Cpro->read(IND);
		Integer res_un = Cun->read(IND);
		if (i==2) {
			un_test = res_un.reveal<int32_t>() == iters;
			pro_test = res_pro.reveal<int32_t>() == 0;
		} else {
			un_test = res_un.reveal<int32_t>() == 0;
			pro_test = res_pro.reveal<int32_t>() == 0;
		}

		FINE = FINE && un_test && pro_test;
	}
	*/

	//cout << "test_update_counter_single(): " << (FINE?"PASS":"FAIL") << "\n";
	
	print_ct(TREE_HT, *C_pro_plus, *C_un_plus);
	print_ct(TREE_HT, *C_pro_minus, *C_un_minus);

	cout << "check A: \n";
	A->check();
	cout << "check T: \n";
	T->check();
	cout << "check d_i: \n";
	d_i->check();

	
	cout << "check C_un_plus: \n";
	C_un_plus->check();
	cout << "check C_un_minus: \n";
	C_un_minus->check();
	cout << "check C_pro_plus: \n";
	C_pro_plus->check();
	cout << "check C_pro_minus: \n";
	C_pro_minus->check();
	

	delete A;
	delete T;
	delete C_pro_plus;
	delete C_pro_minus;
	delete C_un_plus;
	delete C_un_minus;

	delete d_i;
}

void test_update_eo_counter_tree_fairness(int party, int iters) {
	cout << "running test_update_counter_tree(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);

	ZKRAM<BoolIO<NetIO>> * C_un_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_un_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz); 
	ZKRAM<BoolIO<NetIO>> * C_pro_plus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
	ZKRAM<BoolIO<NetIO>> * C_pro_minus = new ZKRAM<BoolIO<NetIO>>(party, tree_index_sz, step_sz, val_sz);
 
	ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
		d_i->refresh();
	}

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	for (int i=0; i<iters; i++) {
		if ((i % (iters/10))==0) {cout << i << "/" << iters <<"\n";}
		Integer curr_index(tree_index_sz, 1, PUBLIC);
		for(int j=0; j<TREE_HT; j++) {
			Integer temp = update_counter_eo(curr_index, *d_i, *A, *T, *C_pro_plus, *C_un_plus, *C_pro_minus, *C_un_minus);
			curr_index = temp;
			//print_ct(dt_height, *Cpro, *Cun);
		}
	}

	// prove eo gain bound
	int max_interior_node = 1 << TREE_HT;
	Float EO_GAIN_BOUND(0.05, PUBLIC);
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
	
	print_ct(TREE_HT, *C_pro_plus, *C_un_plus);
	print_ct(TREE_HT, *C_pro_minus, *C_un_minus);

	cout << "check A: \n";
	A->check();
	cout << "check T: \n";
	T->check();
	cout << "check d_i: \n";
	d_i->check();

	
	cout << "check C_un_plus: \n";
	C_un_plus->check();
	cout << "check C_un_minus: \n";
	C_un_minus->check();
	cout << "check C_pro_plus: \n";
	C_pro_plus->check();
	cout << "check C_pro_minus: \n";
	C_pro_minus->check();
	

	delete A;
	delete T;
	delete C_pro_plus;
	delete C_pro_minus;
	delete C_un_plus;
	delete C_un_minus;

	delete d_i;
}





void test_read(int party, int iters) {
	//cout << "tree_index_sz: " << tree_index_sz << "\n";
	//cout << "val_sz: " << val_sz << "\n";
	cout << "test_read(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	vector<Integer> x;
	int entries = 1 << tree_index_sz;
	for (int i=0; i<entries; i++) {
		Integer data(val_sz, i, ALICE);
		x.push_back(data);
	}
	A->init(x);
	
	//ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	//generate_toy_tree(TREE_HT, *A, *T);
	//vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);
	//ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	/*
	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
	}
	*/

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	int sum=0;
	for (int i=0; i<iters; i++) {
		Integer attr = A->read(curr_index);
		sum += attr.reveal<int32_t>();
	}
	cout << "sum: " << sum << "\n";


	delete A;
	//delete T;
	//delete d_i;
}

void test_read_v2(int party, int iters) {
	//cout << "tree_index_sz: " << tree_index_sz << "\n";
	//cout << "val_sz: " << val_sz << "\n";
	cout << "test_read_v2(). iters: " << iters << "\n";
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	/*
	vector<Integer> x;
	int entries = 1 << tree_index_sz;
	for (int i=0; i<entries; i++) {
		Integer data(val_sz, i, ALICE);
		x.push_back(data);
	}
	A->init(x);
	*/
	
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);

	generate_toy_tree(TREE_HT, *A, *T);
	//vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES);
	//ZKRAM<BoolIO<NetIO>> * d_i = new ZKRAM<BoolIO<NetIO>>(party, ft_index_sz, step_sz, val_sz);

	/*
	for (int k=0; k<NUM_FEATURES; k++) {
		Integer ind(ft_index_sz, k, PUBLIC);
		Integer data(val_sz, D[0][k], ALICE);
		d_i->write(ind, data);
	}
	*/

	Integer curr_index(tree_index_sz, 1, PUBLIC);
	int sum=0;
	for (int i=0; i<iters; i++) {
		Integer attr = A->read(curr_index);
		sum += attr.reveal<int32_t>();
	}
	cout << "sum: " << sum << "\n";


	delete A;
	delete T;
	//delete d_i;
}


void test_rf_dp_help(int party) {
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
	generate_toy_tree(TREE_HT, *A, *T);
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES*2, NUM_FEATURES*2);
	vector<vector<Integer>> comm;
	commit_to_data(NUM_SAMPLES*2, NUM_FEATURES*2, D, comm);
	//cout << comm[NUM_SAMPLES*2-1][NUM_FEATURES*2-1].reveal<uint32_t>() << "\n";
	vector<int> attr_indices;
	vector<int> samp_indices;
	for(int i=0; i<NUM_SAMPLES; i++) {
		samp_indices.push_back(i*2);
	}
	for (int j=0; j<NUM_FEATURES; j++){
		attr_indices.push_back(j*2);
	}
	rf_dp_help(party, 0.005, TREE_HT, *A, *T, attr_indices, NUM_FEATURES, samp_indices, NUM_SAMPLES, comm);
	delete A;
	delete T;
}


void test_rf_dp(int party, int num_trees) {
	vector<ROZKRAM<BoolIO<NetIO>> *> As;
	vector<ROZKRAM<BoolIO<NetIO>> *> Ts;
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES*2, NUM_FEATURES*2);
	vector<vector<int>> attr_is;
	vector<vector<int>> samp_is;

	for (int i=0; i<num_trees; i++) {
		ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
		ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
		generate_toy_tree(TREE_HT, *A, *T);
		As.push_back(A);
		Ts.push_back(T);

		vector<int> attr_indices;
		vector<int> samp_indices;
		for(int i=0; i<NUM_SAMPLES; i++) {
			samp_indices.push_back(i);
		}
		for (int j=0; j<NUM_FEATURES; j++){
			attr_indices.push_back(j);
		}
		attr_is.push_back(attr_indices);
		samp_is.push_back(samp_indices);
	}
	rf_dp(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES*2, NUM_FEATURES*2, D);
	cout << "hello\n";
	for (int i=0; i<num_trees; i++) {
		delete As[i];
		delete Ts[i];
	}
}

void test_rf_eo(int party, int num_trees) {
	vector<ROZKRAM<BoolIO<NetIO>> *> As;
	vector<ROZKRAM<BoolIO<NetIO>> *> Ts;
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES*2, NUM_FEATURES*2);
	vector<vector<int>> attr_is;
	vector<vector<int>> samp_is;

	for (int i=0; i<num_trees; i++) {
		ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
		ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
		generate_toy_tree(TREE_HT, *A, *T);
		As.push_back(A);
		Ts.push_back(T);

		vector<int> attr_indices;
		vector<int> samp_indices;
		for(int i=0; i<NUM_SAMPLES; i++) {
			samp_indices.push_back(i);
		}
		for (int j=0; j<NUM_FEATURES; j++){
			attr_indices.push_back(j);
		}
		attr_is.push_back(attr_indices);
		samp_is.push_back(samp_indices);
	}
	rf_eo(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES*2, NUM_FEATURES*2, D);
	cout << "hello\n";
	for (int i=0; i<num_trees; i++) {
		cout << "deleting " << i << "\n";
		delete As[i];
		delete Ts[i];
	}
	cout << "done\n";
}


int main(int argc, char** argv) {

	using namespace std::chrono;
	//time_point<high_resolution_clock> dp_s, dp_e, opdp_s, opdp_e, eo_s, eo_e, opeo_s, opeo_e;
	
	//time_point<high_resolution_clock> clock_start, clock_end;
	//clock_start = high_resolution_clock::now();
	int party, port;
	int threads = 1;
	parse_party_and_port(argv, &party, &port);
	BoolIO<NetIO>* ios[threads];

	for(int i=0; i<threads; ++i)
		ios[i] = new BoolIO<NetIO>(new NetIO(party==ALICE?nullptr:"127.0.0.1", port), party==ALICE);

	setup_zk_bool<BoolIO<NetIO>>(ios, threads, party);

	cout << "TREE HT: " << TREE_HT << "\n";
	//test_write_di(party, 10000);

	//test_update_root(party, 10000);

	//test_update_counter_single(party, 100000);

	//test_update_counter_tree(party, 10000);

	//test_update_eo_counter_tree(party, 10000);

	//test_read(party, 1000000);

	//test_read_v2(party, 1000000);

	//test_rf_dp_help(party);

	//test_rf_dp(party, 10);

	test_rf_eo(party, 3);

	auto result = finalize_zk_bool<BoolIO<NetIO>>();
	cout << (result?"CHEAT!":"FINE") << "\n";

}
