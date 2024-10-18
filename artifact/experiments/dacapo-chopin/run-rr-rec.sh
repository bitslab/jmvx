#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
    DUMP="-c CoreDumpCallback"
fi

export _RR_TRACE_DIR="rr-rec"
mkdir -p $_RR_TRACE_DIR

CMD="rr record $JAVA_HOME/bin/java -cp $DACAPO_CHOPIN_INSTALL Harness $DUMP $@"
echo RECORDING
echo $CMD
eval $CMD

