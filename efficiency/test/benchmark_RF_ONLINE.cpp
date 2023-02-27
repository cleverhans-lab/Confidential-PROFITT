#include <chrono>
#include <ctime>
#include <iostream>
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
	for (int i=1; i<max_node; i++) {
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

int main(int argc, char** argv) {
	using namespace std::chrono;
	//time_point<high_resolution_clock> dp_s, dp_e, opdp_s, opdp_e, eo_s, eo_e, opeo_s, opeo_e;
	int num_trees = 10;

	time_t rawtime;
    tm* timeinfo;
    char buffer [80];

    time(&rawtime);
    timeinfo = localtime(&rawtime);

    strftime(buffer,80,"%Y-%m-%d-%H-%M-%S",timeinfo);
    puts(buffer);
	string logfile_str(buffer);

	logfile_str = "experiments/" + logfile_str + "_benchmarkRF_output.txt";

	int party, port;
	int threads = 4;
	parse_party_and_port(argv, &party, &port);
	if (party == ALICE) {
		freopen(logfile_str.c_str(), "w", stdout);
	}
	time_point<high_resolution_clock> clock_start, clock_end;
	clock_start = high_resolution_clock::now();


	BoolIO<NetIO>* ios[threads];

	for(int i=0; i<threads; ++i)
		//online:
		ios[i] = new BoolIO<NetIO>(new NetIO(party==ALICE?nullptr:argv[3], port), party==ALICE);
		
		//local:
		//ios[i] = new BoolIO<NetIO>(new NetIO(party==ALICE?nullptr:"127.0.0.1", port), party==ALICE);

	setup_zk_bool<BoolIO<NetIO>>(ios, threads, party);


	vector<ROZKRAM<BoolIO<NetIO>> *> As;
	vector<ROZKRAM<BoolIO<NetIO>> *> Ts;
	vector<vector<int32_t>> D = generate_toy_data(NUM_SAMPLES, NUM_FEATURES*NUM_FEATURES);
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



	
	cout << "cpof start\n";
	int and_start = CircuitExecution::circ_exec->num_and();
	//opdp_s = high_resolution_clock::now();
	if (WHICH_ALG==0) {
		cout << "running rf_dp\n";
		rf_dp(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES, NUM_FEATURES, D);
	} else if (WHICH_ALG==1) {
		cout << "running rf_dp\n";
		rf_dp(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES, NUM_FEATURES, D);
	} else if (WHICH_ALG==2) {
		cout << "running rf_eo\n";
		rf_eo(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES, NUM_FEATURES, D);
	} else if (WHICH_ALG==3) {
		cout << "running rf_eo\n";
		rf_eo(party, 0.005, TREE_HT, num_trees, NUM_FEATURES, NUM_SAMPLES, As, Ts, attr_is, samp_is, NUM_SAMPLES, NUM_FEATURES, D);
	} else {
		cout << "Incorrect value of WHICH_ALG for benchmark.cpp -- no cpof algorithm was run.\n";
	}

	//opdp_e = high_resolution_clock::now();
	int and_end = CircuitExecution::circ_exec->num_and();
	cout << "rf_cpof done\n";

	for (int i=0; i<num_trees; i++) {
		delete As[i];
		delete Ts[i];
	}

	auto result = finalize_zk_bool<BoolIO<NetIO>>();
	cout << "1\n";
	clock_end = high_resolution_clock::now();
	cout << "2\n";

	auto start = time_point_cast<microseconds>(clock_start).time_since_epoch().count();
	cout << "3\n";
	auto end = time_point_cast<microseconds>(clock_end).time_since_epoch().count();

	if (true /*party==ALICE*/) {
		cout << "4\n";
		cout << (result?"CHEAT!":"FINE") << "\n";
		cout << "TREE HT: " << TREE_HT << "    SAMPLES: " << NUM_SAMPLES << "    FEATURES: " << NUM_FEATURES << "\n"; 
		cout << "Time: " << microsec_to_sec(end - start) << " sec\n";
		cout << "      (" << (end-start) << " microsec)\n";
		cout << "AND gates: " << (and_end - and_start) << "\n";
		cout << "5\n";
	}



	for(int i=0; i<threads; ++i) {
		cout << "7 " << i << "\n";
		delete ios[i]->io;
		delete ios[i];
	}
	cout << "done.\n";

}
