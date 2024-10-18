#!/bin/bash

#For use in the docker container to test stuff
N=${1:-2} #number of iters
THREADS=${THREADS:-9}

mkdir -p data/results
# data/threads data/buffers

echo "Testing core functionality, with $N iterations"

echo "==== Instrumentation Experiments ===="
#jmvx tests passthrough with synchronization
experiments/dacapo-instr.sh "all" "vanilla jmvx" $N data/results $THREADS
experiments/chopin-instr.sh "all" "vanilla jmvx" $N data/results $THREADS
experiments/jmvx-bench-instr.sh   "vanilla jmvx" $N data/results $THREADS

echo "==== MVX Experiments ===="
#jmvx in this context tests mvx mode
experiments/dacapo-mvx.sh "all" "jmvx" $N data/results $THREADS
experiments/chopin-mvx.sh "all" "jmvx" $N data/results $THREADS
experiments/jmvx-bench-mvx.sh   "jmvx" $N data/results $THREADS

echo "==== Recording Experiments ===="
#recapture vanilla b/c prior script uses warmup runs
#these scripts do not
experiments/dacapo-rec.sh "all" "vanilla rec" $N data/results $THREADS
experiments/chopin-rec.sh "all" "vanilla rec" $N data/results $THREADS
experiments/jmvx-bench-rec.sh   "vanilla rec" $N data/results $THREADS

echo "==== Replaying Experiments ===="
experiments/dacapo-rep.sh "all" "rep" $N data/results $THREADS
experiments/chopin-rep.sh "all" "rep" $N data/results $THREADS
experiments/jmvx-bench-rep.sh   "rep" $N data/results $THREADS

echo "==== Generating Tables ===="
mkdir -p tables/results
python3 experiments/table-instr.py data/results txt $N > tables/results/instr.txt
python3 experiments/table-mvx.py   data/results txt $N > tables/results/mvx.txt
#don't pass $N, use all available data
python3 experiments/table-rr.py    data/results txt > tables/results/rec.txt
python3 experiments/table-rep.py   data/results txt $N > tables/results/rep.txt
echo "Tables available in tables/results"
