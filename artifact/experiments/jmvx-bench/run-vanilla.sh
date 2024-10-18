#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"

$JAVA_HOME/bin/java $DEBUG -cp $JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR org.h2.tools.Server -tcp #-web -trace
