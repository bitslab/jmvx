#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

mkdir -p $JMVX_BENCH_INSTRUMENTED_NOSYNC/target
cp $JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR $JMVX_BENCH_INSTRUMENTED_NOSYNC/target/$JMVX_BENCH_JAR

DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"

$JAVA_HOME/bin/java $DEBUG -Dsync=false -Dlogfile="instrumenter.log" -Djmvx.file=dacapo -cp $JMVX_JAR:$JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR edu.uic.cs.jmvx.Main -instrument $JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR $JMVX_BENCH_INSTRUMENTED_NOSYNC/target
