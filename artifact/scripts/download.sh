#!/bin/bash

if [ -z "$JMVX_SOURCED" ]; then
    echo "Environment not set, please run the comment: source env.sh";
    exit 1
fi

mkdir $DOWNLOAD_DIR
pushd $DOWNLOAD_DIR
{
    $WGET $DACAPO_URL
}
popd

