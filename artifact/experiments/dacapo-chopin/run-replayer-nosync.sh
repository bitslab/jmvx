#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-c CoreDumpCallback"
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5556,suspend=n"
#OPS="-DmappedMemoryLocation=/Volumes/RAMDisk/ -DreadsFromDisk=false -DavoidObjectSerialization=true"

INPUT_ROLE="-Djmvx.role=Replayer"


$JAVA_HOME/bin/java $MEM -DrecordingDir="rec" $OPS -Dlogfile="follower.log" -Xbootclasspath/p:$INSTALL_DIR/nosync/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $DACAPO_CHOPIN_INSTRUMENTED_NOSYNC Harness $DUMP $@

#jython screws with the terminal properties
#the replay does not reset them because it happens during jvm shutdown
#which isn't always logged (race condition)
#so we manually reset the echo property here
if [ "$1" = "jython" ]; then
	stty echo
fi
	
