#!/bin/bash

#For use in the docker container to test stuff
N=${1:-2} #number of iters
THREADS=${THREADS:-9}

mkdir -p data
# data/threads data/buffers

echo "Testing core functionality, with $N iterations"

echo "==== Instrumentation Varying Threads Experiments ===="
#need to escape the quotes when passing a string that represents multiple arguments
#Without this, they quotes go away and are consumed as separate arguments, leading to issues
#in sub commands issued by run-with-threads
./experiments/run-with-threads.sh experiments/dacapo-instr.sh "all" \"vanilla jmvx\" $N 
./experiments/run-with-threads.sh experiments/chopin-instr.sh "all" \"vanilla jmvx\" $N 
./experiments/run-with-threads.sh experiments/jmvx-bench-instr.sh   \"vanilla jmvx\" $N 

echo "==== MVX Varying Threads Experiments ===="
./experiments/run-with-threads.sh experiments/dacapo-mvx.sh "all" "jmvx" $N
./experiments/run-with-threads.sh experiments/chopin-mvx.sh "all" "jmvx" $N
./experiments/run-with-threads.sh experiments/jmvx-bench-mvx.sh   "jmvx" $N

echo "==== Recording Varying Threads Experiments ===="
./experiments/run-with-threads.sh experiments/dacapo-rec.sh "all" \"vanilla rec\" $N
./experiments/run-with-threads.sh experiments/chopin-rec.sh "all" \"vanilla rec\" $N
./experiments/run-with-threads.sh experiments/jmvx-bench-rec.sh   \"vanilla rec\" $N

echo "==== Replaying Varying Threads Experiments ===="
./experiments/run-with-threads.sh experiments/dacapo-rep.sh "all" "rep" $N
./experiments/run-with-threads.sh experiments/chopin-rep.sh "all" "rep" $N
./experiments/run-with-threads.sh experiments/jmvx-bench-rep.sh   "rep" $N

mv results_threads data/threads

echo "==== MVX Varying Buffer Size Experiments ===="
./experiments/run-with-buffers.sh experiments/dacapo-mvx.sh "all" "jmvx" $N $THREADS
./experiments/run-with-buffers.sh experiments/chopin-mvx.sh "all" "jmvx" $N $THREADS
./experiments/run-with-buffers.sh experiments/jmvx-bench-mvx.sh   "jmvx" $N $THREADS

# need to copy baselines
mv results_buffers data/buffers
for DIR in `ls data/buffers`; do
	cp data/results/vanilla-*.txt data/buffers/$DIR
done


echo "==== Generating Tables ===="
mkdir -p tables/threads tables/buffers
./experiments/table-for-dirs.sh data/threads tables/threads experiments/table-mvx.py csv $N
./experiments/table-for-dirs.sh data/threads tables/threads experiments/table-rec.py csv $N
./experiments/table-for-dirs.sh data/threads tables/threads experiments/table-rep.py csv $N
./experiments/table-for-dirs.sh data/buffers tables/buffers experiments/table-mvx.py csv $N
echo "(CSV) Tables available in tables/threads"

echo "==== Generating Graphs ===="
mkdir -p figs/threads figs/buffers
python3 experiments/make_graphs_threads.py tables/threads figs/threads png
python3 experiments/make_graphs_buffers.py tables/buffers figs/buffers png
