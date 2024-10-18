mkdir rec

DIR=$1
shift
BENCH=$1
$ROOT/experiments/$DIR/run-recorder.sh $*
strace -f -o /dev/null -e signal=SIGSEGV -e 'trace=!%signal' -e 'inject=!%signal,futex,write:signal=SIGUSR2:when=270+' $ROOT/experiments/$DIR/run-replayer-tracer.sh $*
mv natives.log natives-replay-$BENCH.log
