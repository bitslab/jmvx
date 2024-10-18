$TASKSET_LEADER screen -S server -d -m strace -f -o /dev/null -e signal=SIGSEGV -e 'trace=!%signal' -e 'inject=!%signal,futex,write:signal=SIGUSR2:when=80+' $ROOT/experiments/jmvx-bench/run-tracer.sh $*
echo Giving time for server to start
sleep 30
$TASKSET_CLIENT $ROOT/experiments/jmvx-bench/run-bench.sh -s small
mv natives.log natives-h2-server.log
screen -X -S server kill
