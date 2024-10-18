#!/bin/bash
#runs run-table-mvx.sh with a variable number of circular buffer sizes
#args are subset of run-table-mvx
#expects: [script] [benchmarks] [roles] [N]
#script sets [result dir], dacapo args are left as default

#bytes * 1024 * 1024 = MB
#start with 500 mb
INIT_MB=500
#buffer size is rounded to multiples of 4096
#so we can't really go any lower
MIN_SIZE=4096
RESULTS_DIR="results_buffers"

BUFFERS=(250 100 10 1)
SCRIPT=$1
shift

#for each iter, cut length in half
for SIZE in ${BUFFERS[@]}; do
	echo "Setting circular buffer to $SIZE bytes"
	#use env so we don't have to modify shell's state permanently
	#this way, if someone has CIRC_BUFFER already set, we won't interfere
	#with that once the script is finished
	#-S argument allows us to pass args to the command
	BYTES=$(( $SIZE * 1024 * 1024 ))
	CMD="env -S CIRC_BUFFER=$BYTES $ROOT/$SCRIPT $* $RESULTS_DIR"
	echo "Running $CMD"
	ITER_DIR="$SIZE" #in case we want to change name
	bash -c "$CMD"
	mkdir $RESULTS_DIR/$ITER_DIR &> /dev/null
	echo "Moving results to $RESULTS_DIR/$ITER_DIR"
	#this is not the way the move command works for all systems...
	#e.g., macbook may not like this command
	mv -t $RESULTS_DIR/$ITER_DIR/. $RESULTS_DIR/*.txt
done
