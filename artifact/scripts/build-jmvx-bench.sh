#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

git clone $JMVX_BENCH_URL $JMVX_BENCH_INSTALL

pushd $JMVX_BENCH_INSTALL
{
    mvn clean
    mvn package
}
popd
