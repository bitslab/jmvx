#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"

INPUT_ROLE="-Djmvx.role=Leader"

CIRC=""
if [ "$CIRC_BUFFER" != "" ]; then
	CIRC="-DmappedMemorySize=$CIRC_BUFFER"
	echo "Set circ buffer to $CIRC_BUFFER"
fi

DUMP=""
if [ "$CORE_DUMP" != "" ]; then
	#DUMP="-DcoreDumpAtEnd=$CORE_DUMP"
	DUMP="-c CoreDumpCallback"
fi


pushd $DACAPO_CHOPIN_INSTRUMENTED
$JAVA_HOME/bin/java $MEM -Dlogfile="leader.log" $CIRC -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp . Harness $DUMP $@
popd
