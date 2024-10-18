#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=y"
DUMP=""
if [ "$CORE_DUMP" != "" ]; then
    DUMP="-c CoreDumpCallback"
fi

echo "$TASKSET_LEADER $JAVA_HOME/bin/java $MEM $DEBUG -cp $DACAPO_INSTALL Harness $@"
pushd $DACAPO_CHOPIN_INSTALL
$TASKSET_LEADER $JAVA_HOME/bin/java $MEM $DEBUG -cp . Harness $DUMP $@
popd
