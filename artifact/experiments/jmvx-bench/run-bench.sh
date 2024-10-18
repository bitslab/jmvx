#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5558,suspend=n"
$JAVA_HOME/bin/java $DEBUG -cp $JMVX_BENCH_INSTALL/target/$JMVX_BENCH_JAR org.dacapo.harness.TestHarness h2 $*

perl $ROOT/experiments/monitorH2.pl
