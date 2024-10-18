#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

if [ -z "$1" ]; then
    printf "No role specified. Usage: run-switch-roles.sh <role>\n Where role can be Leader or Follower.\n";
    exit 1
fi

if [ "$1" == "Leader" ]; then
    INPUT_ROLE="-Djmvx.role=Leader"
    ADDRESS=5555
    LOGFILE="leader.log"
elif [ "$1" == "Follower" ]; then
    INPUT_ROLE="-Djmvx.role=Follower"
    ADDRESS=5556
    LOGFILE="follower.log"
else
    printf "Invalid role: %s\n" "$1"
    printf "Usage: run-switch-roles.sh <role>\n Where role can be Leader or Follower.\n"
    exit 1
fi

DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=$ADDRESS,suspend=n"

TEST_INSTRUMENTED=$ROOT/software/jmvx-test-inst

$JAVA_HOME/bin/java -Dlogfile=$LOGFILE -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $TEST_INSTRUMENTED/target edu.uic.cs.jmvx.divergence.DivergenceTest2 $INPUT_ROLE