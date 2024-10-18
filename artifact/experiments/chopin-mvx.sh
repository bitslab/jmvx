#!/bin/bash

BMS=("avrora" "batik" "fop" "jython" "jme" "pmd" "sunflow" "xalan")
SKIP=("h2" "luindex" "lusearch")
COLS=("jmvx" "nosync")
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

mkdir $RESULTS_DIR &> /dev/null

SCRIPT_DIR="./experiments/dacapo-chopin"

for C in ${COLS[@]}; do
    ROLE=""
    if [ "$C" = "nosync" ]; then
        ROLE="-$C"
    fi
    for B in ${BMS[@]}; do
        LEADER="$RESULTS_DIR/jmvx-leader$ROLE-$B.txt"
        FOLLOWER="$RESULTS_DIR/jmvx-follower$ROLE-$B.txt"
        COORD="$RESULTS_DIR/jmvx-coordinator$ROLE-$B.txt"
        
	echo "" > $LEADER
        echo "" > $FOLLOWER
        echo "" > $COORD

        rm leader.txt
        rm follower.txt
        rm coord.txt
        ln -s $LEADER leader.txt
        ln -s $FOLLOWER follower.txt
        ln -s $COORD coord.txt

        for I in $(seq $N); do
            echo "Executing JMVX $B run $I"
	    # Launch leader
	    CMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/run-leader$ROLE.sh $B $DACAPO_ARGS |& tee -a $LEADER"
	    echo "$CMD" |& tee -a $LEADER
	    screen -S leader -d -m bash -c "$CMD"

	    # Launch follower
	    CMD="$TASKSET_FOLLOWER $TIMEOUT $SCRIPT_DIR/run-follower$ROLE.sh $B $DACAPO_ARGS |& tee -a $FOLLOWER"
	    echo "$CMD" |& tee -a $FOLLOWER
	    screen -S follower -d -m bash -c "$CMD"

	    # Launch coordinator
	    #no timeout. Screen doesn't play well with timeout
	    #in order to screen -X -S coord kill, timeout needs --foreground flag
	    #however, timeout fails to kill the process when run with that flag from screen
	    #removing the timeout is a hacky fix. Timeouts in leader/follower will kill them
	    #script will fallthrough and kill coord with screen if need arises
	    CMD="$TASKSET_COORD ./experiments/run-coordinator.sh |& tee -a $COORD"
	    echo "$CMD" |& tee -a $COORD
	    screen -S coord -d -m bash -c "$CMD"

	    # Wait for leader
	    screen -r leader

	    # Wait for follower
	    screen -r follower


            # Kill coordinator
            screen -X -S coord kill
            screen -wipe

            rm -rf scratch

            sleep 5

        done
    done
done
