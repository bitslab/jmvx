#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the command: source env.sh";
    exit 1
fi

DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"
PACKAGE=edu/uic/cs/jmvx/divergence
SOURCE_DIR=$JMVX_DIR/target/test-classes/$PACKAGE
TARGET_DIR=$INSTALL_DIR/jmvx-test-inst/target/$PACKAGE
FILE_NAME="-Djmvx.file=test"

mkdir -p $TARGET_DIR

$JAVA_HOME/bin/java -Dlogfile="instrumenter.log" $DEBUG $FILE_NAME -cp $JMVX_JAR edu.uic.cs.jmvx.Main -instrument $SOURCE_DIR/DivergenceTest.class $TARGET_DIR
$JAVA_HOME/bin/java -Dlogfile="instrumenter.log" $DEBUG $FILE_NAME -cp $JMVX_JAR edu.uic.cs.jmvx.Main -instrument $SOURCE_DIR/DivergenceTest2.class $TARGET_DIR