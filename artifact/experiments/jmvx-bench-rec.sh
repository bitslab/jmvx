#!/bin/bash

COLS=("vanilla" "nosync" "rec" "rep")
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

DACAPO_ARGS="-t $THREADS -n 1"
TIMEOUT="timeout -k 10 10m"
TIMEOUTLONG="timeout -k 10 20m"
TIME=`which time`

mkdir $RESULTS_DIR &> /dev/null

exptemplate() {
    MODE=$1
    SCRIPT=$2
    LOGPREFIX=$3
    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
        LOG="$RESULTS_DIR/$LOGPREFIX-h2cs.txt"
	CMD="$TASKSET_LEADER $TIME ./experiments/jmvx-bench/$SCRIPT"
	
	BENCHLOG="$RESULTS_DIR/$LOGPREFIX-h2cs-bench.txt"
    	BENCHCMD="$TASKSET_CLIENT $TIMEOUTLONG ./experiments/jmvx-bench/run-bench.sh $DACAPO_ARGS"
        
	echo "" > $LOG
	echo "" >$BENCHLOG
        for I in $(seq $N); do
            echo "Executing recorder h2cs run $I"
            
	    rm -rf rec &> /dev/null
            mkdir rec &> /dev/null
	    
	    echo $CMD |& tee -a $LOG
    	    screen -S recorder -d -m bash -c "{ $CMD; } |& tee -a $LOG"
    	    
	    echo "Giving the server 30 seconds to start"
    	    sleep 30
    	    #screen -S monitor -d -m bash -c "sleep 5; perl experiments/monitorH2.pl"
    	    #how do we want to taskset this...?
    	    
	    echo $BENCHCMD |& tee -a $BENCHLOG
    	    eval $BENCHCMD |& tee -a $BENCHLOG

            echo "Giving the server 15 seconds to shutdown"
            sleep 15
    
    	    #screen -X -S recorder kill
    	    #safety net
    	    #./scripts/killall.sh #this script uses debug sockets to find programs, but we are turning those off...
    	    #perl scripts/killJMVX.pl
            gzip rec/*.dat
            ls -lah rec |& tee -a $LOG
            du -s rec
        done
    fi
}

#note, this causes vanilla to make and delete a rec directory
#but it doesn't matter
exptemplate "vanilla" "run-vanilla.sh" "vanilla-rec"
exptemplate "rec" "run-recorder.sh" "jmvx-rec"
exptemplate "nosync" "run-recorder-nosync.sh" "jmvx-rec-nosync"

