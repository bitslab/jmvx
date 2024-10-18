#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5556,suspend=n"
FILE_NAME="-Djmvx.file=rt.jar"

$JAVA_HOME/bin/java $DEBUG $FILE_NAME -Dlogfile="instrumenter.log" -cp $JMVX_JAR edu.uic.cs.jmvx.Main -instrument $JAVA_HOME/jre/lib/rt.jar $INSTALL_DIR
