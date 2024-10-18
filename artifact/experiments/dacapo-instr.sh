#!/bin/bash

#BMS=("avrora" "batik" "fop" "h2" "jython" "luindex" "lusearch" "pmd" "sunflow" "xalan")
BMS=("h2" "luindex" "lusearch")
COLS=("vanilla" "nosync" "jmvx")
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

DACAPO_ARGS="-t $THREADS -n 3"
TIMEOUT="timeout -k 10 10m"
TIMEOUTLONG="timeout -k 10 20m"
TIME=`which time`

SCRIPT_DIR="./experiments/dacapo"

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
                echo $CMD >> $LOG
                eval $CMD |& tee -a $LOG
            done
        done
    fi
}

exptemplate "vanilla" "run-vanilla.sh" "vanilla"
exptemplate "jmvx" "run-single.sh" "jmvx-sync" 
exptemplate "nosync" "run-single-nosync.sh" "jmvx-nosync" 

