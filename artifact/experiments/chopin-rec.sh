#!/bin/bash

BMS=("avrora" "batik" "fop" "jython" "jme" "pmd" "sunflow" "xalan")
SKIP=("h2" "luindex" "lusearch")
COLS=("vanilla" "nosync" "rec" "rep")
RESULTS_DIR="results"

if [ "$1" != "all" ]; then
    BMS=("$1")
fi

if [ "$2" != "all" ]; then
    COLS=("$2")
fi

N=$3

if [ "$4" != "" ]; then
    RESULTS_DIR=$4
fi

THREADS=9
if [ "$5" != "" ]; then
    THREADS=$5
fi

DACAPO_ARGS="-t $THREADS -n 1"
TIMEOUT="timeout -k 10 10m"
TIMEOUTLONG="timeout -k 10 20m"
TIME=`which time`

SCRIPT_DIR="./experiments/dacapo-chopin"

mkdir $RESULTS_DIR &> /dev/null

exptemplate() {
    MODE=$1
    SCRIPT=$2
    LOGPREFIX=$3
    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
        for B in ${BMS[@]}; do
            LOG="$RESULTS_DIR/$LOGPREFIX-$B.txt"
            CMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/$SCRIPT $B $DACAPO_ARGS"
            echo "" > $LOG
            for I in $(seq $N); do
                echo "Executing $MODE $B run $I"
                
		rm -rf rec &> /dev/null
                mkdir rec &> /dev/null
                
		echo "$CMD" |& tee -a $LOG
		eval "$CMD" |& tee -a $LOG
                
		#compress everything
		gzip rec/*.dat
		ls -lah rec |& tee -a $LOG
                du -s rec
            done
        done
    fi
}

#note, this causes vanilla to make and delete a rec directory
#but it doesn't matter
exptemplate "vanilla" "run-vanilla.sh" "vanilla-rec"
exptemplate "rec" "run-recorder.sh" "jmvx-rec"
exptemplate "nosync" "run-recorder-nosync.sh" "jmvx-rec-nosync"

