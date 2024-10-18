#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=y"

INPUT_ROLE="-Djmvx.role=SingleLeaderWithoutCoordinator"

$JAVA_HOME/bin/java -Dlogfile="leader.log" -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $JMVX_BENCH_INSTRUMENTED_NOSYNC/target/$JMVX_BENCH_JAR org.h2.tools.Server -tcp

