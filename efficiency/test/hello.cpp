#include <iostream>
#include <fstream>
#include <vector>
#include <emp-tool/emp-tool.h>
#include <thread>
#include <chrono>

using namespace std;
using namespace emp;

int main(int argc, char** argv) {
	cout << "hello world.\n";

	auto start = clock_start();
	std::this_thread::sleep_for(std::chrono::milliseconds(1000));
	double a = time_from(start);
	double b = 0.0;
	double x = 0.0;
	for (int i=0; i<10; i++) {
		auto t2 = clock_start();

		for (int j=0; j<5; j++) {
			std::this_thread::sleep_for(std::chrono::milliseconds(200));
		}
		b = time_from(t2);
		x = x + b;

		std::this_thread::sleep_for(std::chrono::milliseconds(1000));
	}
	double c = time_from(start);

	cout << "a: " << a * 1e-6 << endl;
	cout << "b: " << b << endl;
	cout << "c: " << c << endl;
	cout << "x: " << x << endl;

	return 0;
}
