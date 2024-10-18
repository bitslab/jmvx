#!/bin/bash

COLS=("vanilla" "nosync" "jmvx")
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

DACAPO_ARGS="-t $THREADS -n 3"
TIMEOUT="timeout -k 10 10m"
TIMEOUTLONG="timeout -k 10 20m"
TIME=`which time`

mkdir $RESULTS_DIR &> /dev/null


exptemplate() {
    MODE=$1
    SCRIPT=$2
    LOGPREFIX=$3

    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
    	BENCH="$RESULTS_DIR/$LOGPREFIX-h2cs-bench.txt"
    	SERVER="$RESULTS_DIR/$LOGPREFIX-h2cs.txt"
        echo "" > $BENCH
	echo "" > $SERVER
        for I in $(seq $N); do
            echo "Executing H2 server $MODE run $I"
    	screen -S vanilla -d -m bash -c "{ $TASKSET_LEADER $TIME ./experiments/jmvx-bench/$SCRIPT; } |& tee -a $SERVER"
    	echo "Giving the server 30 seconds to start"
    	sleep 30
    	#this program watches over h2 and interrupts the server when it is done
    	#screen -S monitor -d -m bash -c "sleep 5; perl experiments/monitorH2.pl"
    	CMD="$TIMEOUTLONG $TASKSET_CLIENT ./experiments/jmvx-bench/run-bench.sh $DACAPO_ARGS"
    	echo "$CMD" >> "$BENCH"
    	env H2SIG="QUIT" bash -c "$CMD |& tee -a $BENCH"
    
    	echo Giving OS time to reclaim sockets
    	sleep 5
    
    	#screen -X -S vanilla kill
        done
    fi
}

exptemplate "vanilla" "run-vanilla.sh" "vanilla"
exptemplate "jmvx" "run-single.sh" "jmvx-sync"
exptemplate "nosync" "run-single-nosync.sh" "jmvx-nosync"

