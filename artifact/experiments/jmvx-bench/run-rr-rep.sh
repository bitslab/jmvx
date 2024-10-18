#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

TIME=`which time`

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-DcoreDumpAtEnd=$CORE_DUMP"
fi

export _RR_TRACE_DIR="rr-rec"
echo REPLAYING
CMD="$TIME -f \"Runtime: %E\" rr replay -a"
echo $CMD
eval "$CMD"
