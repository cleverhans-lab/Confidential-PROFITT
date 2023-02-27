cmake_minimum_required (VERSION 3.0)
project (cpof)
set(NAME "cpof")

find_path(CMAKE_FOLDER NAMES cmake/emp-tool-config.cmake)
include(${CMAKE_FOLDER}/cmake/emp-base.cmake)

FIND_PACKAGE(emp-zk REQUIRED)
INCLUDE_DIRECTORIES(${EMP-ZK_INCLUDE_DIRS})

ENABLE_TESTING()
ADD_SUBDIRECTORY(test)
