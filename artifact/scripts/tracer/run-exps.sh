#!/bin/bash

BACH=("h2" "luindex" "lusearch")
CHOPIN=("avrora" "batik" "fop" "jython" "pmd" "sunflow" "xalan" "jme")
for B in ${BACH[@]}; do
	CMD="$TASKSET_LEADER timeout -k 30 30m ./trace.sh dacapo $B -s small"
	echo $CMD
	eval $CMD
	if [ $? -ne 0 ]; then
		echo "command failed"
	fi
done

for B in ${CHOPIN[@]}; do
	CMD="$TASKSET_LEADER timeout -k 30 30m ./trace.sh dacapo-chopin $B -s small"
	echo $CMD
	eval $CMD
	if [ $? -ne 0 ]; then
		echo "command failed"
	fi
done
