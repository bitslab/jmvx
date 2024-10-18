#!/bin/bash

#For use in the docker container to test stuff
N=${1:-2} #number of iters
THREADS=${THREADS:-9}

mkdir -p data/results
# data/threads data/buffers

echo "Testing no sync functionality, with $N iterations"

echo "==== Instrumentation Experiments ===="
experiments/dacapo-instr.sh "all" "nosync" $N data/results $THREADS
experiments/chopin-instr.sh "all" "nosync" $N data/results $THREADS
experiments/jmvx-bench-instr.sh   "nosync" $N data/results $THREADS

echo "==== MVX Experiments ===="
experiments/dacapo-mvx.sh "all" "nosync" $N data/results $THREADS
experiments/chopin-mvx.sh "all" "nosync" $N data/results $THREADS
experiments/jmvx-bench-mvx.sh   "nosync" $N data/results $THREADS

echo "==== Recording Experiments ===="
experiments/dacapo-rec.sh "all" "nosync" $N data/results $THREADS
experiments/chopin-rec.sh "all" "nosync" $N data/results $THREADS
experiments/jmvx-bench-rec.sh   "nosync" $N data/results $THREADS

echo "==== Replaying Experiments ===="
experiments/dacapo-rep.sh "all" "nosync" $N data/results $THREADS
experiments/chopin-rep.sh "all" "nosync" $N data/results $THREADS
experiments/jmvx-bench-rep.sh   "nosync" $N data/results $THREADS

echo "==== Generating Tables ===="
mkdir -p tables/results
python3 experiments/table-instr.py data/results txt $N > tables/results/instr.txt
python3 experiments/table-mvx.py   data/results txt $N > tables/results/mvx.txt
python3 experiments/table-rr.py    data/results txt >    tables/results/rec.txt
python3 experiments/table-rep.py   data/results txt $N > tables/results/rep.txt
echo "Tables available in tables/results"
