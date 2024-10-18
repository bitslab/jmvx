#!/bin/bash

COLS=("jmvx" "nosync")
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

for C in ${COLS[@]}; do
    ROLE=""
    if [ "$C" = "nosync" ]; then
        ROLE="-$C"
    fi

    LEADER="$RESULTS_DIR/jmvx-leader$ROLE-h2cs.txt"
    FOLLOWER="$RESULTS_DIR/jmvx-follower$ROLE-h2cs.txt"
    COORD="$RESULTS_DIR/jmvx-coordinator$ROLE-h2cs.txt"
    BENCH="$RESULTS_DIR/bench$ROLE-h2cs.txt"
    
    echo "" > $LEADER
    echo "" > $FOLLOWER
    echo "" > $COORD
    echo "" > $BENCH

    rm leader.txt
    rm follower.txt
    rm coord.txt
    rm bench.txt
    ln -s $LEADER leader.txt
    ln -s $FOLLOWER follower.txt
    ln -s $COORD coord.txt
    ln -s $BENCH bench.txt

    for I in $(seq $N); do
        echo "Executing JMVX h2cs run $I"
        #h2 server benchmark is setup differently. Leader/follower are never ending processes
        #need a another process to drive them
        # Launch leader
        CMD="{ $TASKSET_LEADER $TIME ./experiments/jmvx-bench/run-leader$ROLE.sh; } |& tee -a $LEADER"
        echo "$CMD" |& tee -a $LEADER
        screen -S leader -d -m bash -c "$CMD"
        
        # Launch follower
        CMD="{ $TASKSET_FOLLOWER $TIME ./experiments/jmvx-bench/run-follower$ROLE.sh; } |& tee -a $FOLLOWER"
        echo "$CMD" |& tee -a $FOLLOWER
        screen -S follower -d -m bash -c "$CMD"
        
        # Launch coordinator
        CMD="$TASKSET_COORD ./experiments/run-coordinator.sh |& tee -a $COORD"
        echo "$CMD" |& tee -a $COORD
        screen -S coord -d -m bash -c "$CMD"
        
        # wait for the leader to start the server. If the bench tries to connect before the leader prints
        # "TCP server started at ...", it will fail
        # this will read the latest line appended to leader.txt and search for TCP
        # this breaks on old versions of tail, which don't detect the pipe is broken
        # until the NEXT write (which never occurs due to the nature of the program...)
        #tail -n 0 -F leader.txt | grep -q "TCP"
        echo "Giving the server 30 seconds to start"
        sleep 30 
        
        #process to kill the servers when the work is done
        #screen -S monitor -d -m bash -c "sleep 5; perl experiments/monitorH2.pl"
        # Launch client benchmark
        CMD="$TASKSET_CLIENT $TIMEOUTLONG ./experiments/jmvx-bench/run-bench.sh $DACAPO_ARGS |& tee -a $BENCH"
        echo "$CMD" |& tee -a $BENCH
        screen -S bench bash -c "$CMD"
        
        echo Giving OS time to reclaim socket
        sleep 5
        # Wait for benchmark
        #screen -r bench
        # Kill leader/follower (they never end)
        screen -X -S leader kill
        screen -X -S follower kill

        # Kill coordinator
        screen -X -S coord kill
        # Make sure the coordinator is really dead
        # fixes problems on Dave's machine
        #./scripts/killall.sh
        #perl scripts/killJMVX.pl

        screen -wipe

        rm -rf scratch

        sleep 5

    done
done
