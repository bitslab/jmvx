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

cp -r $DACAPO_INSTALL $DACAPO_INSTRUMENTED

FILE=""
case $1 in
    "avrora") #Working and passes validation
        FILE=(avrora-cvs-20091224.jar)
        ;;
    "batik") #Working but fails validation
        FILE=(batik-all.jar xml-apis-ext.jar crimson-1.1.3.jar xerces_2_5_0.jar xalan-2.6.0.jar)
        ;;
    "eclipse") #Divergence on Follower and NullPointerException on Leader / BIGLOCK allows Leader to make more progress but still have divergence and Leader seems to get stuck
        FILE=(eclipse.jar)
        ;;
    "fop") #Working and passes validation
        FILE=(fop.jar avalon-framework-4.2.0.jar batik-all-1.7.jar commons-io-1.3.1.jar commons-logging-1.0.4.jar serializer-2.7.0.jar servlet-2.2.jar xalan-2.7.0.jar xercesImpl-2.7.1.jar xml-apis-ext-1.3.04.jar xmlgraphics-commons-1.3.1.jar)
        ;;
    "h2") #Working and passes validation
        FILE=(dacapo-h2.jar derbyTesting.jar junit-3.8.1.jar h2-1.2.121.jar)
        ;;
    "jython") #Divergence on Follower / BIGLOCK allows Leader to pass validation, but still diverges on Follower
        FILE=(jython.jar antlr-3.1.3.jar asm-3.1.jar asm-commons-3.1.jar constantine.jar guava-r07.jar jaffl.jar jline-0.9.95-SNAPSHOT.jar jnr-posix.jar)
        ;;
    "luindex") #Working and passes validation
        FILE=(dacapo-luindex.jar lucene-core-2.4.jar lucene-demos-2.4.jar)
        ;;
    "lusearch-fix") #Working but fails validation
        FILE=(dacapo-lusearch-fix.jar lucene-core-2.4.jar lucene-demos-2.4.jar)
        ;;
    "lusearch") #Working but fails validation (StreamCorruptedException on Follower once but could not replicate it)
        FILE=(dacapo-lusearch.jar lucene-core-2.4.jar lucene-demos-2.4.jar)
        ;;
    "pmd") #Working sometimes but can get different exceptions on different runs / BIGLOCK causes a deadlock
        FILE=(pmd-4.2.5.jar jaxen-1.1.1.jar asm-3.1.jar junit-3.8.1.jar xercesImpl.jar)
        ;;
    "sunflow") #Working and passes validation
        FILE=(sunflow-0.07.2.jar janino-2.5.15.jar)
        ;;
    "tomcat") #Divergence on Follower
        FILE=(dacapo-tomcat.jar dacapo-digest.jar bootstrap.jar tomcat-juli.jar commons-daemon.jar commons-httpclient.jar commons-logging.jar commons-codec.jar)
        ;;
    "tradebeans") #ClassNotFoundException on Leader and Follower, ClassCastException on Coordinator
        FILE=(daytrader.jar)
        ;;
    "tradesoap") #Same as tradebeans
        FILE=(daytrader.jar)
        ;;
    "xalan") #Failed validation on Leader and Divergence on Follower
        FILE=(dacapo-xalan.jar xalan.jar xercesImpl.jar serializer.jar)
        ;;
esac

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=n"
FILE_NAME="-Djmvx.file=dacapo"
if [ -z "$FILE" ]; then

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
    $JAVA_HOME/bin/java -Dlogfile="instrumenter.log" $DEBUG $FILE_NAME -cp $CP edu.uic.cs.jmvx.Main -instrument $DACAPO_INSTALL $DACAPO_INSTRUMENTED

else

    echo "Instrumenting file $DACAPO_INSTALL/Harness"

    $JAVA_HOME/bin/java -DinstrumentSync=y $DEBUG $FILE_NAME -cp $JMVX_JAR edu.uic.cs.jmvx.Main -instrument $DACAPO_INSTALL/Harness.class $DACAPO_INSTRUMENTED

    # Instrument the jar with the target code
    for jfile in "${FILE[@]}";
    do
        echo "Instrumenting file $DACAPO_INSTALL/jar/$jfile"
        $JAVA_HOME/bin/java $DEBUG $FILE_NAME -DinstrumentSync="y" -cp $JMVX_JAR:$DACAPO_INSTALL/jar/xml-apis-ext.jar edu.uic.cs.jmvx.Main -instrument $DACAPO_INSTALL/jar/$jfile $DACAPO_INSTRUMENTED/jar
    done
fi
