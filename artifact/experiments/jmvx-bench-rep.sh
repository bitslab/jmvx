#!/bin/bash

COLS=("nosync" "rep")
RESULTS_DIR="results"


if [ "$1" != "all" ]; then
    COLS=("$1")
fi

N=$2

if [ "$3" != "" ]; then
    RESULTS_DIR=$3
fi

THREADS=9
if [ "$4" != "" ]; then
    THREADS=$4
fi

DACAPO_ARGS="-t $THREADS -n 1 --preserve"
TIMEOUT="timeout -k 10 10m"
TIMEOUTLONG="timeout -k 10 20m"
TIME=`which time`

DELAY=30

mkdir $RESULTS_DIR &> /dev/null

exptemplate() {
    MODE=$1
    RECSCRIPT=$2
    REPSCRIPT=$3
    LOGPREFIX=$4

    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
        echo "Recording run h2 server"
        rm -rf rec &> /dev/null
        mkdir rec &> /dev/null
        CMD="$TASKSET_LEADER ./experiments/jmvx-bench/$RECSCRIPT"
        BENCH="$TASKSET_CLIENT $TIMEOUTLONG ./experiments/jmvx-bench/run-bench.sh $DACAPO_ARGS"
        echo "$CMD"
        echo "$BENCH"
        #bash -c "$CMD"
        screen -S recorder -d -m bash -c "$CMD"
        echo "Giving the server $DELAY seconds to start"
        sleep $DELAY
        #process to kill the servers when the work is done
        #screen -S monitor -d -m bash -c "sleep 5; perl experiments/monitorH2.pl"
        bash -c "$BENCH"
        #screen -X -S recorder kill
        #./scripts/killall.sh
        #perl scripts/killJMVX.pl
        ls -lah rec
        du -s rec
    
        LOG="$RESULTS_DIR/$LOGPREFIX-h2cs.txt"
        CMD="$TASKSET_LEADER $TIME $TIMEOUTLONG ./experiments/jmvx-bench/run-replayer.sh"
        echo "" > $LOG
        for I in $(seq $N); do
            echo "Executing replayer h2 server run $I"
            echo "$CMD" |& tee -a $LOG
            eval "{ $CMD; } |& tee -a $LOG"
        done
    fi
}

exptemplate "rep" "run-recorder.sh" "run-replayer.sh" "jmvx-rep"
exptemplate "nosync" "run-recorder-nosync.sh" "run-replayer-nosync.sh" "jmvx-rep-nosync"

