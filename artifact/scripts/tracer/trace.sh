DIR=$1
shift
BENCH=$1
strace -f -o /dev/null -e signal=SIGSEGV -e 'trace=!%signal' -e 'inject=!%signal,futex,write:signal=SIGUSR2:when=70+' $ROOT/experiments/$DIR/run-tracer.sh $*
mv natives.log natives-$BENCH.log
