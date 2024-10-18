#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#use the external time so we can format the output
TIME=`which time`

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
    DUMP="-c CoreDumpCallback"
fi

export _RR_TRACE_DIR="rr-rec"
mkdir -p $_RR_TRACE_DIR

CMD="rr record $TASKSET_LEADER $JAVA_HOME/bin/java -cp $DACAPO_CHOPIN_INSTALL Harness $DUMP $@"
echo RECORDING
echo $CMD
eval $CMD

echo REPLAYING
CMD="$TIME -f \"Runtime: %E\" rr replay -a"
echo $CMD
eval "$CMD"

#BENCH=$1
#see where the latest symlink points to
#there is a standard format for rr's names which helps
#LATEST=`ls -l "$_RR_TRACE_DIR/latest-trace" | grep -E -o '[-[:alnum:]]*$'`
#mv $_RR_TRACE_DIR/$LATEST $_RR_TRACE_DIR/$BENCH
