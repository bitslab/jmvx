#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

#DEBUG="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5555,suspend=y"

INPUT_ROLE="-Djmvx.role=SingleLeaderWithoutCoordinator"

CMD="$JAVA_HOME/bin/java -Dsync=false -Dlogfile=leader.log -Xbootclasspath/p:$INSTALL_DIR/nosync/rt.jar:$JMVX_JAR $DEBUG $INPUT_ROLE -cp $DACAPO_INSTRUMENTED_NOSYNC Harness $@"
echo $CMD
$CMD
