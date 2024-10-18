#!/bin/bash
# If you plan on using Docker, uncomment line 4, and comment out line 5.
# If don't plan on using Docker, leave as is.
# source scripts/docker-specific.sh
source scripts/machine-specific.sh

setenv() {
    # wget command to download programs/data
    export WGET="$(which wget) -nc"

    if [ -z "$ROOT" ]; then
        echo "Please set project ROOT on file env.sh";
        echo "Or set it up on file scripts/machine-specific.sh";
        return 1
    fi

    # Where everything is downloaded
    export DOWNLOAD_DIR=$ROOT/downloads

    # Where everything is installed
    export INSTALL_DIR=$HOME/software

    #export JAVA_HOME=$INSTALL_DIR/jvm

    if [ -z "$JAVA_HOME" ]; then
        echo "Please export JAVA_HOME (or set it on file scripts/env.sh)";
        return 1
    fi

    export PATH=$JAVA_HOME/bin:$PATH

    export INSTALL_DIR=$ROOT/software

    export JMVX_DIR=$ROOT/..
    export JMVX_JAR=$JMVX_DIR/target/JavaMVX-0.01-SNAPSHOT.jar

    export DACAPO_URL=https://sourceforge.net/projects/dacapobench/files/9.12-bach-MR1/dacapo-9.12-MR1-bach.jar
    export DACAPO_JAR=dacapo-9.12-MR1-bach.jar
    export DACAPO_INSTALL=$INSTALL_DIR/dacapo
    export DACAPO_INSTRUMENTED=$INSTALL_DIR/dacapo-inst
    export DACAPO_INSTRUMENTED_NOSYNC=$INSTALL_DIR/dacapo-inst-nosync

    export DACAPO_CHOPIN_INSTALL=$INSTALL_DIR/dacapo-chopin
    export DACAPO_CHOPIN_INSTRUMENTED=$INSTALL_DIR/dacapo-chopin-inst
    export DACAPO_CHOPIN_INSTRUMENTED_NOSYNC=$INSTALL_DIR/dacapo-chopin-inst-nosync

    export JMVX_BENCH_URL=git@github.com:bitslab/java-mvx-bench.git
    export JMVX_BENCH_INSTALL=$INSTALL_DIR/jmvx-bench
    export JMVX_BENCH_INSTRUMENTED=$INSTALL_DIR/jmvx-bench-inst
    export JMVX_BENCH_INSTRUMENTED_NOSYNC=$INSTALL_DIR/jmvx-bench-inst-nosync
    export JMVX_BENCH_JAR=jmvxBench-jar-with-dependencies.jar

    export JMVX_SOURCED=1
}

setenv
