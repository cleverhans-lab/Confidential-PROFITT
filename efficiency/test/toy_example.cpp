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



int main(int argc, char** argv) {
	using namespace std::chrono;
	time_point<high_resolution_clock> dp_s, dp_e, opdp_s, opdp_e, eo_s, eo_e, opeo_s, opeo_e;

	int party, port;
	int threads = 1;
	parse_party_and_port(argv, &party, &port);
	BoolIO<NetIO>* ios[threads];


	for(int i=0; i<threads; ++i)
		ios[i] = new BoolIO<NetIO>(new NetIO(party==ALICE?nullptr:"127.0.0.1", port), party==ALICE);

	setup_zk_bool<BoolIO<NetIO>>(ios, threads, party);

	//vector<Integer> data = init_example_A();

	//ROZKRAM<BoolIO<NetIO>>* A = init_example_A(party); 
	//ROZKRAM<BoolIO<NetIO>>* T = init_example_T(party);
	
	ROZKRAM<BoolIO<NetIO>>* A = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz); 
	ROZKRAM<BoolIO<NetIO>>* T = new ROZKRAM<BoolIO<NetIO>>(party, tree_index_sz, val_sz);
	//initialize_tree_from_file("toy_tree.txt", *A, *T);
	initialize_tree_from_file("toy_tree_h5.txt", *A, *T);
	
	vector<vector<int32_t>> D = initialize_dataset_from_file("toy_data.txt");

	
	//Integer res = A->read(Integer(5, 3, PUBLIC));
	//Bit eq = res == Integer(32, 4, ALICE);
	//cout << "equal? " << eq.reveal<bool>(PUBLIC) << "\n";
	
	/*
	cout << "hi\n";
	int dp_and_start = CircuitExecution::circ_exec->num_and();
	dp_s = high_resolution_clock::now();
	cpof_dp(party, 0.005, 5, *A, *T, 8, 6, D);
	dp_e = high_resolution_clock::now();
	int dp_and_end = CircuitExecution::circ_exec->num_and();
	cout <<"yay\n";
	
	//cout << "start\n";
	//cpof_eo(party, 0.005, 0.005, 3, *A, *T, 8, 6, D);
	//cout << "done\n";

	cout << "start op\n";
	int opdp_and_start = CircuitExecution::circ_exec->num_and();
	opdp_s = high_resolution_clock::now();
	op_cpof_dp(party, 0.005, 5, *A, *T, 8, 6, D);
	opdp_e = high_resolution_clock::now();
	int opdp_and_end = CircuitExecution::circ_exec->num_and();
	cout << "done\n";
	*/

	int eo_and_start = CircuitExecution::circ_exec->num_and();
	eo_s = high_resolution_clock::now();
	cpof_eo(party, 0.005, 5, *A, *T, 8, 6, D);
	eo_e = high_resolution_clock::now();
	int eo_and_end = CircuitExecution::circ_exec->num_and();

	int opeo_and_start = CircuitExecution::circ_exec->num_and();
	opeo_s = high_resolution_clock::now();
	op_cpof_eo(party, 0.005, 5, *A, *T, 8, 6, D);
	opeo_e = high_resolution_clock::now();
	int opeo_and_end = CircuitExecution::circ_exec->num_and();
	

	auto result = finalize_zk_bool<BoolIO<NetIO>>();
	delete A;
	delete T;
	for(int i=0; i<threads; ++i) {
		delete ios[i]->io;
		delete ios[i];
	}

	/*
	auto dp_start = time_point_cast<microseconds>(dp_s).time_since_epoch().count();
	auto dp_end = time_point_cast<microseconds>(dp_e).time_since_epoch().count();
	auto opdp_start = time_point_cast<microseconds>(opdp_s).time_since_epoch().count();
	auto opdp_end = time_point_cast<microseconds>(opdp_e).time_since_epoch().count();
	*/

	auto eo_start = time_point_cast<microseconds>(eo_s).time_since_epoch().count();
	auto eo_end = time_point_cast<microseconds>(eo_e).time_since_epoch().count();
	auto opeo_start = time_point_cast<microseconds>(opeo_s).time_since_epoch().count();
	auto opeo_end = time_point_cast<microseconds>(opeo_e).time_since_epoch().count();

	if (party==ALICE) {
		cout << (result?"CHEAT!":"FINE") << "\n";
		/*
		cout << "Time (dp): " << (end - start) << " microsec\n";
		cout << "AND gates (dp): " << (dp_and_end - dp_and_start) << "\n";
		cout << "Time (opdp): " << (opend - opstart) << " microsec\n";
		cout << "AND gates (op): " << (opdp_and_end - opdp_and_start) << "\n";
		*/

		cout << "Time (eo): " << (eo_end - eo_start) << " microsec\n";
		cout << "AND gates (eo): " << (eo_and_end - eo_and_start) << "\n";
		cout << "Time (opeo): " << (opeo_end - opeo_start) << " microsec\n";
		cout << "AND gates (op): " << (opeo_and_end - opeo_and_start) << "\n";
	}
	
}
