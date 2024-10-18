#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=y"

echo "$TASKSET_LEADER $JAVA_HOME/bin/java $DEBUG -cp $DACAPO_INSTALL Harness $@"
$TASKSET_LEADER $JAVA_HOME/bin/java $DEBUG -cp $DACAPO_INSTALL Harness $@
