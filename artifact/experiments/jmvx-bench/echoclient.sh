#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"

$JAVA_HOME/bin/java -Dlogfile="server.log" -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR -cp $JMVX_BENCH_INSTRUMENTED/target/$JMVX_BENCH_JAR demos.echoserver.EchoClient
