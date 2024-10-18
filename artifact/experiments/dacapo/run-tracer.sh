#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
    DUMP="-c CoreDumpCallback"
fi

INPUT_ROLE="-Djmvx.role=SingleLeaderWithoutCoordinator"

$JAVA_HOME/bin/java -Dlogfile="leader.log" -DlogNatives="true" -Xbootclasspath/p:$INSTALL_DIR/tracer/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $DACAPO_INSTRUMENTED Harness $DUMP $@
