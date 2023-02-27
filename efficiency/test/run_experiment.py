import os
from os.path import exists
import sys
import subprocess
from experiment_config import *



def change_constants(new_tree_height, new_num_samples, new_num_features, new_which_alg, verbose=True):
    if verbose:
        print("##### change_constant_file() #####")
    if (not exists(CONSTANT_FILE_PATH)):
        print("Error in change_constant_file(): file " + CONSTANT_FILE_PATH + " does not exist.")
        quit()
    with open(CONSTANT_FILE_PATH, "r") as in_file:
        with open("temp_constants.h", "w") as temp_file:
            for line in in_file.readlines():
                if "const static int TREE_HT" in line:
                    temp_file.write("const static int TREE_HT = " + str(new_tree_height) + ";\n")
                    if verbose:
                        print( "in file: temp_constants.h wrote TREE_HT = " + str(new_tree_height))
                elif "const static int NUM_SAMPLES" in line:
                    temp_file.write("const static int NUM_SAMPLES = " + str(new_num_samples) + ";\n")
                    if verbose:
                        print( "in file: temp_constants.h wrote NUM_SAMPLES = " + str(new_num_samples))
                elif "const static int NUM_FEATURES" in line:
                    temp_file.write("const static int NUM_FEATURES = " + str(new_num_features) + ";\n")
                    if verbose:
                        print( "in file: temp_constants.h wrote NUM_FEATURES = " + str(new_num_features))
                elif "const static int WHICH_ALG" in line:
                    temp_file.write("const static int WHICH_ALG = " + str(new_which_alg) + ";\n")
                    if verbose:
                        print( "in file: temp_constants.h wrote WHICH_ALG = " + str(new_which_alg))
                else:
                    temp_file.write(line)

    with open(CONSTANT_FILE_PATH, "w") as f:
        with open("temp_constants.h", "r") as temp_file:
            for line in temp_file.readlines():
                f.write(line)
        if verbose:
            print("wrote new parameters to file: " + CONSTANT_FILE_PATH)
    os.remove("temp_constants.h")


def compile_cpof(verbose=True):
    if verbose:
        print("##### compile_cpof() #####")
    output = subprocess.check_output("cmake . && make", cwd=CPOF_PATH, shell=True, text=True)

def compile_and_run_cpof(tree_ht, num_samples, num_features, which_alg, verbose=True):
    change_constants(tree_ht, num_samples, num_features, which_alg, verbose)
    compile_cpof(verbose)
    #output = subprocess.check_output("./run " + EXPERIMENT_SCRIPT_NAME, cwd=CPOF_PATH, shell=True, text=True)

def compile_with_params(tree_ht, num_samples, num_features, which_alg, verbose=True):
    change_constants(tree_ht, num_samples, num_features, which_alg, verbose)
    compile_cpof(verbose)

def run_cpof_online(party, port, IP):
    output = subprocess.check_output(ONLINE_SCRIPT_NAME + " " + str(party) + " " + str(port) + " " + IP, cwd=CPOF_PATH, shell=True, text=True)
    return output

def get_ip_from_file(f):
    #need only ALICE's IP
    with open(f, 'r') as ip_file:
        ip = ip_file.readline().strip()
    return ip

def print_file(f):
    with open(f, 'r') as my_file:
        for line in my_file:
            print(line.strip())

def print_log(f):
    with open(f, 'r') as my_file:
        for line in my_file:
            if "connected" in line:
                pass
            elif "microsec" in line:
                pass
            elif ("start" in line) or ("done" in line):
                pass
            else:
                print(line.strip())

def print_most_recent_log():
    ls = os.listdir(EXP_LOG_DIR)
    ls.sort(reverse=True)
    latest = ls[0]
    #print(latest)
    print_log(EXP_LOG_DIR + latest)


if __name__ == "__main__":
    command = int(sys.argv[1])
    if command==0:
        print("LOCAL MODE")
        compile_and_run_cpof(8, 1000, 10, 0)
    elif command==3:
        print("DEBUG MODE")
        print_most_recent_log()
    else:
        print("NETWORK MODE")
        party = int(sys.argv[2])
        port = int(sys.argv[3])
        tree_ht = int(sys.argv[4])
        num_samples = int(sys.argv[5])
        num_features = int(sys.argv[6])
        which_alg = int(sys.argv[7])
        str_ip_file = "/home/ubuntu/cpof/temp/temp_pub_ips.txt"


        if command==1: #compile
            compile_with_params(tree_ht, num_samples, num_features, which_alg)
        elif command==2: #run
            ip = get_ip_from_file(str_ip_file)
            print(run_cpof_online(party, port, ip))
            if party==1:
                print_most_recent_log()





