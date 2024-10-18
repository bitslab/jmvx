#!/bin/bash

#runs an experiment while varying the number of threads
#example: ./run-with-threads ./run-table-mvx.sh "all" "all" 1
#will execute run-table-mvx while setting the dacapo threads to 1 - 9
#results_threads directory will contain a folder for each thread setting
#note: expects command to run-table script with first 3 args
#this script sets the rest of the params

RESULTS_DIR="results_threads"
#T=9 is regular setting
for T in {1..8}; 
do
	CMD="$* \"$RESULTS_DIR\" $T"
	echo "Running $CMD"
	ITER_DIR="$T" #in case we want dir to be "tX" or "threads-X"
	bash -c "$CMD"
	mkdir -p $RESULTS_DIR/$ITER_DIR &> /dev/null
	echo "Moving results to $RESULTS_DIR/$ITER_DIR"
	#this is not the way the move command works for all systems...
	#e.g., macbook may not like this command
	mv -t $RESULTS_DIR/$ITER_DIR/. $RESULTS_DIR/*.txt
done
