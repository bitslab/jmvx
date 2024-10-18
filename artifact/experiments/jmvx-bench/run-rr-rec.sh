#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-DcoreDumpAtEnd=$CORE_DUMP"
fi

export _RR_TRACE_DIR="rr-rec"
mkdir -p $_RR_TRACE_DIR

TIME=`which time`
CMD="$TIME -f \"Runtime: %E\" rr record $JAVA_HOME/bin/java -cp $JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR org.h2.tools.Server -tcp"
echo RECORDING
echo $CMD
eval $CMD 
#eval $CMD &

#echo "Giving the server 30 seconds to start"
#sleep 30
#
#$TASKSET_CLIENT $ROOT/experiments/jmvx-bench/run-bench.sh $DUMP $@
