#!/bin/bash

#script generates the tables for the thread experiments
#usage:   ./table_for_dirs.sh [input dir] [output dir] [table-script] [format] [rm under]
#example: ./table-for-dirs.sh data/threads tables/threads experiments/table-mvx.py txt 10
#that command will run "python3 table-mvx.py txt 10 for each dir in data/threads
#lace tables into tables/threads

INPUT_DIR=${1:-.}
OUTPUT_DIR=${2:-.}
SCRIPT=$3
FORMAT=${4:-txt}
RM_UNDER=$5
SCRIPT_NAME=`basename $SCRIPT`
#drop table- from script name
WO_TABLE=${SCRIPT_NAME#table-}
#drops .py from script name
OUT=${WO_TABLE%.*}
#OUT is the name to save the data under
#running table-mvx.py for csv's will make save files mvx1.csv, mvx2.csv etc

for THREAD_DIR in $INPUT_DIR/*; 
do
	echo $THREAD_DIR
	if [ -d $THREAD_DIR ]; then
		echo "$THREAD_DIR results:"
		CMD="python3 $SCRIPT $THREAD_DIR $FORMAT $RM_UNDER"
		#$2 $THREAD_DIR $3"
		B=`basename $THREAD_DIR`
		CMD="$CMD > $OUTPUT_DIR/$OUT$B.$FORMAT"
		echo "Running $CMD"
		bash -c "$CMD"
	fi
done
