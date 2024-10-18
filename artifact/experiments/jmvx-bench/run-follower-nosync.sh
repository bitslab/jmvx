#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5556,suspend=n"
INPUT_ROLE="-Djmvx.role=Follower"
#OPS="-DmappedMemoryLocation=/Volumes/RAMDisk/ -DreadsFromDisk=false -DavoidObjectSerialization=true" 
CIRC=""
if [ "$CIRC_BUFFER" != "" ]; then
	CIRC="-DmappedMemorySize=$CIRC_BUFFER"
	echo "Set circ buffer to $CIRC_BUFFER"
fi
DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	DUMP="-DcoreDumpAtEnd=$CORE_DUMP"
fi

$JAVA_HOME/bin/java -Dlogfile="follower.log" $MEM $CIRC $DUMP $OPS -Xbootclasspath/p:$INSTALL_DIR/nosync/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $JMVX_BENCH_INSTRUMENTED_NOSYNC/target/$JMVX_BENCH_JAR org.h2.tools.Server -tcp
#removed -web -trace
