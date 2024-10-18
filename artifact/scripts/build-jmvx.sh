#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

pushd $JMVX_DIR
{
    mvn clean
    mvn package
}
popd
