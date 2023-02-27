# Confidential PROFITT - anonymized README for replication of efficiency benchmarks
(formerly titled cpof)

Zero-knowledge proof of fair training for decision trees.

### Replication Quick Start 
Benchmarks in the paper were run using separate Amazon EC2 instances for the prover and verifier. They can also be run locally, but better performance is achieved with two instances (since the computational cost of initializing the ZK proof framework is distributed among two machines).

#### Setup
1. Install emp-tool with the following command:
```
wget https://raw.githubusercontent.com/emp-toolkit/emp-readme/master/scripts/install.py && python3 install.py --install --tool --ot --zk --deps 
```

2. Inside the working directory for this code (here on referred to as `cpof-main`) make auxilliary directories and compile:
```
cd cpof-main; mkdir data && mkdir experiments && mkdir temp && cmake . && make
```

3. Configure `test/experiment_config.py` by replacing `/PATH/TO/THISDIR/...` with the path to this directory on your machine.

4. If desired, use this script to throttle the connection to LAN parameters
```
wget https://raw.githubusercontent.com/emp-toolkit/emp-readme/master/scripts/throttle.py

python3 throttle.py -i ens5 -b 1000 -l 1
```

#### Running experiments
5. Set parameters in `cpof/constant.py` and compile using `cmake . && make`. For the Demographic Parity version of the algorithm, set `WHICH_ALG=1` and for Equalized Odds set `WHICH_ALG=3`.

6. Run benchmark for zero-knowledge proof of fair decision tree training. To run it locally, use the following command:
```
./run bin/test_benchmark
```
To run it on separate machines, use these respective commands on 2 machines
```
./bin/test_benchmark_ONLINE 1 12345 <IP of machine 2>
```
```
./bin/test_benchmark_ONLINE 2 12345 <IP of machine 1>
```
To obtain the figures in the paper, we perform 5 runs for each parameter setting and report the median.


7. Run benchmark for  zero-knowledge proof of fair random forest training. On two separate machines, use the following commands:
```
./bin/test_benchmark_RF_ONLINE 1 12345 <IP of machine 2>
```
```
./bin/test_benchmark_RF_ONLINE 2 12345 <IP of machine 1>
```
To obtain the figures in the paper, we perform 5 runs for each parameter setting and report the median.
