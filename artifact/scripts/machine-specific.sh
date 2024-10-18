#!/bin/bash


# Root dir, REPO/artifact on your machine
export ROOT=/home/lganchin/repos/java-mvx/artifact

# Where do you have JDK11 installed
export JAVA_HOME=/home/lganchin/jvm/jdk1.8.0_231

if which python > /dev/null; then
	export python=python
elif which python3 > /dev/null; then
	export python=python3
else
	echo "Neither 'python' or 'python3' found"
fi

$python scripts/affinity.py

export TASKSET_LEADER=""
export TASKSET_FOLLOWER=""
export TASKSET_COORD=""

