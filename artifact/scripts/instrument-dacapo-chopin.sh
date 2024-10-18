#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"
FILE_NAME="-Djmvx.file=dacapo"

echo "Instrumenting DaCapo Chopin"
CP=$JMVX_JAR
$JAVA_HOME/bin/java -Xmx8G -Dlogfile="instrumenter.log" $DEBUG $FILE_NAME -cp $CP:$DACAPO_CHOPIN_INSTALL edu.uic.cs.jmvx.Main -instrument $DACAPO_CHOPIN_INSTALL $DACAPO_CHOPIN_INSTRUMENTED
