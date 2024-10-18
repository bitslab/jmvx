#!/bin/bash

#For use in the docker container to test stuff
N=${1:-1} #number of iters
THREADS=${THREADS:-9}
BENCH="avrora"

mkdir -p data/quick
# data/threads data/buffers

echo "==== Running Trace ===="
mkdir -p traces
#runs our tracing tool on avrora from DaCapo Chopin
scripts/tracer/trace.sh dacapo-chopin avrora -t $THREADS
#file produced by the previous command
TRACE="natives-avrora.log"
#Run the logfile through our analysis tool
#"table" mode prints out the native methods that result in system calls
#and the number of unique stack traces that led to the method call 
#for readibility, we sort this output
scripts/tracer/analyze2.sh --depth 8 table  $TRACE | sort > traces/table.txt
#Rerun the tool in "simple" mode, which outputs a suggested method to instrument all system calls
#This method will be the lowest non native, non lambda method before the native call
#Observe in the output that predictable classes appear here: FileOutputStream's open and write
scripts/tracer/analyze2.sh --depth 8 simple $TRACE | sort > traces/recommended.txt
#Finally, rerun in the "dump", which prints out the last 8 methods called in each stack trace
#This mode allows us to manually find a level of abstraction to instrument at
#This allows us to reduce the number of methods we instrument by choosing a method
#that could lead to multiple different system calls.
#For instance, FileSystemProvider.checkAccess can lead to different calls (lstat vs stat)
scripts/tracer/analyze2.sh --depth 8 dump   $TRACE > traces/dump.txt
mv $TRACE traces
echo "Trace results in traces directory"

echo "Testing core functionality, with $N iterations"
echo "==== Instrumentation Experiments ===="
#Run all instrumentation overhead experiments
experiments/chopin-instr.sh $BENCH "all" $N data/quick $THREADS

echo "==== MVX Experiments ===="
#Run all (sync vs nosync) MVX experiments 
experiments/chopin-mvx.sh $BENCH "all" $N data/quick $THREADS

echo "==== Recording Experiments ===="
#Run all recorder experiments
experiments/chopin-rec.sh $BENCH "all" $N data/quick $THREADS

echo "==== Replaying Experiments ===="
#Run all replayer experiments
experiments/chopin-rep.sh $BENCH "all" $N data/quick $THREADS

echo "==== Generating Tables ===="
mkdir -p tables/quick
#Use our scripts to graph the data. We omit data that didn't pass $N times
python3 experiments/table-instr.py data/quick txt $N > tables/quick/instr.txt
python3 experiments/table-mvx.py   data/quick txt $N > tables/quick/mvx.txt
python3 experiments/table-rr.py    data/quick txt $N > tables/quick/rec.txt
python3 experiments/table-rep.py   data/quick txt $N > tables/quick/rep.txt
echo "Tables available in tables/quick"
