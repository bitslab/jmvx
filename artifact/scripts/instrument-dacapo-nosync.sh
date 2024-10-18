#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

mkdir -p $DACAPO_INSTALL
pushd $DACAPO_INSTALL
{
    cp $DOWNLOAD_DIR/$DACAPO_JAR .
    jar xf $DACAPO_JAR
    rm $DACAPO_JAR
}
popd

cp -r $DACAPO_INSTALL $DACAPO_INSTRUMENTED_NOSYNC

DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"
FILE_NAME="-Djmvx.file=dacapo"

# Instrument the whole DaCapo benchmark
echo "Instrumenting the whole DaCapo"
CP=$JMVX_JAR
CP=$CP:$DACAPO_INSTALL
CP=$CP:$DACAPO_INSTALL/harness
CP=$CP:$DACAPO_INSTALL/jar/antlr-3.1.3.jar
CP=$CP:$DACAPO_INSTALL/jar/xml-apis-1.3.04.jar
CP=$CP:$DACAPO_INSTALL/jar/xml-apis-ext-1.3.04.jar
CP=$CP:$DACAPO_INSTALL/jar/asm-3.1.jar
CP=$CP:$DACAPO_INSTALL/jar/junit-3.8.1.jar
$JAVA_HOME/bin/java -Dsync=false -Dlogfile="instrumenter.log" $DEBUG $FILE_NAME -cp $CP edu.uic.cs.jmvx.Main -instrument $DACAPO_INSTALL $DACAPO_INSTRUMENTED_NOSYNC
