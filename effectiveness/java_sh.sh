#!/bin/bash

mkdir -p $1
for i in {0..1000}
do
	touch $1$i.txt
done
chmod -R 777 $1

