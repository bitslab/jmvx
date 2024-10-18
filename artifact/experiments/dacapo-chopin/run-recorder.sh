#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-c CoreDumpCallback"
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=y"
OPTS="-DavoidObjectSerialization=0"

INPUT_ROLE="-Djmvx.role=Recorder"

$JAVA_HOME/bin/java $MEM -DrecordingDir="rec" -Dlogfile="leader.log" -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $DACAPO_CHOPIN_INSTRUMENTED Harness $DUMP $@
