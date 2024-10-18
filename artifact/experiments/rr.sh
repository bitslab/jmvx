#!/usr/bin/bash

BACH=("h2" "luindex" "lusearch")
CHOPIN=("avrora" "batik" "fop" "jython" "jme" "pmd" "sunflow" "xalan")
COLS=("bach", "chopin", "jmvx-bench")

if [ "$1" != "all" ]; then
    COLS=("$1")
fi

N=$2
RESULTS_DIR=${3:-results-rr}
THREADS=${4:-9}

DACAPO_ARGS="-t $THREADS -n 1"
TIMEOUT="timeout -k 10 10m"
SCRIPT_DIR="$ROOT/experiments"
TIME=`which time`

mkdir -p $RESULTS_DIR

exptemplate() {
    MODE=$1
    SCRIPT=$2
    shift
    shift
    BMS=("$@")
    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
        for B in ${BMS[@]}; do
             RECLOG="$RESULTS_DIR/rr-rec-$B.txt"
             REPLOG="$RESULTS_DIR/rr-rep-$B.txt"
             RECCMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/$SCRIPT/run-rr-rec.sh $B $DACAPO_ARGS"
             REPCMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/$SCRIPT/run-rr-rep.sh $B $DACAPO_ARGS"
             echo "" > $RECLOG
             echo "" > $REPLOG
             for I in $(seq $N); do
                 echo "Executing $B run $I"

                 echo "$RECCMD" |& tee -a $RECLOG
                 echo "$RECCMD" |& tee -a $RECLOG
                 
                 #rr-rec is log dir set by run-rr.sh script we call
                 #need the slash at the end to resolve symlink
                 ls -lah rr-rec/latest-trace/ |& tee -a $RECLOG
                 du -s rr-rec/latest-trace/   |& tee -a $RECLOG

                 eval "$REPCMD" |& tee -a $REPLOG
                 eval "$REPCMD" |& tee -a $REPLOG
                   
             done
        done
    fi
}

jmvxbench() {
    MODE=$1
    if [[ " ${COLS[*]} " =~ "$MODE" ]]; then
        RECLOG="$RESULTS_DIR/rr-rec-h2-server.txt"
        REPLOG="$RESULTS_DIR/rr-rep-h2-server.txt"
        BENCHLOG="$RESULTS_DIR/rr-h2-server-bench.txt"
        RECCMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/jmvx-bench/run-rr-rec.sh"
        REPCMD="$TASKSET_LEADER $TIMEOUT $SCRIPT_DIR/jmvx-bench/run-rr-rep.sh"
        BENCHCMD="$TASKSET_CLIENT $SCRIPT_DIR/jmvx-bench/run-bench.sh $DACAPO_ARGS"
        echo "" > $RECLOG
        echo "" > $REPLOG
        echo "" > $BENCHLOG
        for I in $(seq $N); do
            echo "Executing $B run $I"

            echo "$RECCMD" |& tee -a $RECLOG
            screen -S recorder -d -m bash -c "eval $RECCMD |& tee -a $RECLOG"

            echo "Giving server 30 seconds to start"
            sleep 30
            echo "$BENCHCMD" |& tee -a $BENCHLOG
            eval "$BENCHCMD" |& tee -a $BENCHLOG

            echo "Giving the server 30 seconds to shutdown"
            sleep 30
            
            #rr-rec is log dir set by run-rr.sh script we call
            #need the slash at the end to resolve symlink
            ls -lah rr-rec/latest-trace/ |& tee -a $RECLOG
            du -s rr-rec/latest-trace/   |& tee -a $RECLOG

            echo "$REPCMD" |& tee -a $REPLOG
            eval "$REPCMD" |& tee -a $REPLOG
              
        done
    fi
}

#note, this causes vanilla to make and delete a rec directory
#but it doesn't matter
exptemplate "bach" "dacapo" "${BACH[@]}"
exptemplate "chopin" "dacapo-chopin" "${CHOPIN[@]}"
jmvxbench "jmvx-bench"
