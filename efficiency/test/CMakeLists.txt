#Testing macro
macro (add_test_executable_with_lib _name libs)
	add_executable(test_${_name} "${_name}.cpp")
	target_link_libraries(test_${_name} ${EMP-ZK_LIBRARIES})
endmacro()

macro (add_test_case _name)
	add_test_executable_with_lib(${_name} "")
	add_test(NAME ${_name} COMMAND "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/test_${_name}" WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/")
endmacro()

macro (add_test_case_with_run _name)
	add_test_executable_with_lib(${_name} "")
	add_test(NAME ${_name} COMMAND "./run" "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/test_${_name}" WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/")
endmacro()

# Test cases
add_test_case_with_run(hello)
add_test_case_with_run(ram_test)
add_test_case_with_run(ro_ram_test)
add_test_case_with_run(toy_example)
add_test_case_with_run(benchmark)
add_test_case_with_run(benchmark_ONLINE)
add_test_case_with_run(zk_dt_unit)
add_test_case_with_run(benchmark_RF_ONLINE)