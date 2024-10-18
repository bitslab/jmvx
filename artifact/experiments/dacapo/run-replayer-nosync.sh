#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-DcoreDumpAtEnd=$CORE_DUMP"
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5556,suspend=y"

INPUT_ROLE="-Djmvx.role=Replayer"

$JAVA_HOME/bin/java $MEM -DjunkPort=10001 -DrecordingDir="rec" -Dlogfile="follower.log" $DUMP -Xbootclasspath/p:$INSTALL_DIR/nosync/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $DACAPO_INSTRUMENTED_NOSYNC Harness $@
