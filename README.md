# Maintainers:

1. David Schwartz (dschwa23@uic.edu)
2. Luís Pina (luispina@uic.edu)

# Introduction
Java Multi-Version Execution (JMVX) is a tool for performing Multi-Version Execution (MVX) and Record Replay (RR) in Java.
Most tools for MVX and RR observe the behavior of a program at a low level, e.g., by looking at system calls.
Unfortunately, this approach fails for high level language virtual machines due to benign divergences (differences in behavior that accomplish that same result) introduced by the virtual machine -- particularly by garbage collection and just-in-time compilation.
In other words, the management of the virtual machines creates differing sequences of system calls that lead existing tools to believe a program has diverged, when in practice, the application running on top of the VM has not.
JMVX takes a different approach, opting instead to add MVX and RR logic into the bytecode of compiled programs running in the VM to avoid benign divergences related to VM management.

This artifact is a docker image that will create a container holding our source code, compiled system, and experiments with JMVX.
The image allows you to run the experiments we used to address the research questions from the paper (from Section 4).
This artifact is desiged to show:

1. [**Supported**] JMVX performs MVX for Java
2. [**Supported**] JMVX performs RR for Java
3. [**Supported**] JMVX is performant 


In the "Step by Step" section, we will point out how to run experiments to generate data supporting these claims.
The 3rd claim is supported, however, it may not be easily reproducible. 
For the paper we measured performance on bare metal rather than in a docker container.
When testing the containerized artifact on a Macbook (Sonoma v14.5), JMVX ran slower than expected.
Similarly, see the section on "Differences From Experiment" to see properties of the artifact that were altered (and could affect runtime results).
Thanks for taking the time to explore our artifact.

# Hardware Requirements

* x86 machine running Linux, preferably Ubuntu 22.04 (Jammy)
* 120 Gb of storage
* About 10 Gb of RAM to spare
* 2+ cores

# Getting Started Guide
Section is broken into 2 parts, setting up the docker container and running a quick experiment to test if everything is working.

## Container Setup

1. Download the container [image](https://zenodo.org/records/12637140) (DOI 10.5281/zenodo.12637140).
2. If using docker desktop, increase the size of the virtual disk to 120 gb.
    - In the GUI goto Settings > Resources > Virtual Disk (should be a slider). 
    - From the terminal, modify `diskSizeMiB` field in docker's `settings.json` and restart docker.
        * Linux location: `~/.docker/desktop/settings.json`.
        * Mac location  : `~/Library/Group Containers/group.com.docker/settings.json`.
3. Install with `docker load -i java-mvx-image.tar.gz`
    - This process takes can take 30 minutes to 1 hour.
* Start the container via: `docker run --name jmvx -it --shm-size="10g" java-mvx`
    - The `--shm-size` parameter is important as JMVX will crash the JVM if not enough space is available (detected via a SIGBUS error). 

## Quick Start

The container starts you off in an environment with JMVX already prepared, e.g., JMVX has been built and the instrumentation is done.
The script `test-quick.sh` will test all of JMVX's features for DaCapo's `avrora` benchmark.
The script has comments explaining each command.
It should take about 10 minutes to run.

The script starts by running our system call tracer tool.
This phase of the script will create the directory `/java-mvx/artifact/trace`, which will contain:

1. `natives-avrora.log` -- (serialized) map of methods, that resulted in system calls, to the stack trace that generated the call.
    - `/java-mvx/artifact/scripts/tracer/analyze2.sh` is used to analyze this log and generate other files in this directory.
2. `table.txt` - a table showing how many unique stack traces led to the invocation of a native method that called a system call.  
3. `recommended.txt` - A list of methods JMVX recommends to instrument for the benchmark.
4. `dump.txt` - A textual dump of the last 8 methods from every stack trace logged.
    - This allows us to reduce the number of methods we need to instrument by choosing a wrapper that can handle multiple system calls.
    - `FileSystemProvider.checkAccess` is an example of this.

JMVX will recommend functions to instrument, these are included in `recommended.txt`. 
If you inspect the file, you'll see some simple candidates for instrumentation, e.g., `available`, `open`, and `read`, from `FileInputStream`.
The instrumentation code for `FileInputInputStream` can be found in `/java-mvx/src/main/java/edu/uic/cs/jmvx/bytecode/FileInputStreamClassVisitor.java`.
The recommendations work in many cases, but for some, e.g. `FileDescriptor.closeAll`, we chose a different method (e.g., `FileInputStream.close`) by manually inspecting `dump.txt`.

After tracing, runtime data is gathered, starting with measuring the overhead caused by instrumentation.
Next it will move onto getting data on MVX, and finally RR.
The raw output of the benchmark runs for these phases is saved in `/java-mvx/artifact/data/quick`.
Tables showing the benchmark's runtime performance will be placed in `/java-mvx/artifact/tables/quick`.
That directory will contain:

1. `instr.txt` -- Measures the overhead of instrumentation.
1. `mvx.txt`   -- Performance for multi-version execution mode.
2. `rec.txt`   -- Performance for recording.
3. `rep.txt`   -- Performance for replaying.

*This script captures data for research claims 1-3 albeit for a single benchmark and with a single iteration.*
Note, data is captured for the benchmark's memory usage, but the txt tables only display runtime data.
The next section will describe how to run data collection over the entire set of benchmarks.

# Step by Step Instructions
Running these three scripts will test JMVX: 

1. `./test-core.sh   [N]` -- Gathers baseline data and tests core MVX and RR functionality across all benchmarks
    - This script will take about an hour or two per iteration (N).
    - *This provides results for research claims 1-3.*
    - This provides half of the results for research questions 1, 2, 3, and 6 in the paper (see Section 4).
2. `./test-nosync.sh [N]` -- Gathers data without forcing synchronization in all modes and all benchmarks
    - This script will take about an hour or two per iteration (N).
    - This provides the other half of the results for research questions 1, 2, 3, and 6 in the paper.
3. `./test-rest.sh   [N]` -- Run experiments varying the number of threads and buffers over all modes and benchmarks
    - **This script can take on the order of days to run!**
    - This provides results for research questions 4 and 5 in the paper.

N is an optional parameter that sets the number of iterations to run per benchmark (not withstanding warmup runs).
By default, it is set at 2 for a functionality test, but we used 10 for the paper.
You have to run `test-core.sh` first as both `test-nosync.sh` and `test-rest.sh` reference data collected from `test-core.sh`.

Of the scripts listed above, the first two will create/update tables in `/java-mvx/artifact/tables/results`.
The tables are saved as a human readable text format, so you can `cat` the file to inspect them.
This folder will contain these files:

1. `instr.txt` -- Measures the overhead of instrumentation.
    - Data for research question 1 from the paper (see Section 4).
1. `mvx.txt`   -- Performance for multi-version execution mode.
    - Data for research question 2 from the paper.
2. `rec.txt`   -- Performance for recording.
    - Half of the data for research question 3 from the paper.
3. `rep.txt`   -- Performance for replaying.
    - Other half of the data for research question 3 from the paper.

The last script will generate figures in `/java-mvx/artifact/figs`.
You can get the directory from the docker container by running `docker cp jmvx:/java-mvx/artifact/figs .`
The graphs provide data to answers research questions 4 and 5 from the paper.

## Differences From Experiment
In the experiment, we had access to a NUMA machine with multiple sockets.
Each socket had 18 physical cores.
We mapped 9 unique cores to the leader and follower respectively, and distributed the load of the coordinator across them.
The docker container will perform this mapping, however, it will divide up the number of logical (not necessarily physical) cores on your machine.
As a result, the benchmarks may run with less threads.
For example, a machine with 8 cores will map 4 cores to leader and follower respectively, and each benchmark will be run with 4 threads (versus 9), to ensure proper thread pinning.
In addition, this may cause JMVX to run with hyperthreaded cores, which we did not test in our experiments.

For the H2 server, the benchmark was run on a separate socket from the server.
If the container is run on a machine without a separate socket, the threads for the client process will be assigned equally to CPUs mapped to the leader/follower processes.
Performance data for the H2 server may differ for this reason.

Another difference is the max heap size of the JMVX.
On our machine we set this to 12 Gb (via the flag `-Xmx12g`).
To reduce the memory consumption of the container, we omitted the flag.
This may alter the data reported for memory consumption by the JVM.
Note, the flag can be added easily by setting an environment variable called `MEM` like so `export MEM='-Xmx12g`. 

## Rerunning Particular Experiments
This section documents our experiment, tabling, and graphing scripts so they can be rerun for smaller, fine grained inputs.
Before discussing how to use them, it's important to know which benchmarks are available to run.

### Benchmarks
Two versions of DaCapo are supplied, along with a customized H2 server benchmark.
The Bach version of DaCapo (referred to as 'dacapo' by the scripts) is used to test `luindex`, `lusearch`, and `h2`.
The Chopin version of those benchmarks does not work for Java 8.
DaCapo Chopin (referred to as 'chopin' by the scripts) is used to test `avrora`, `batik`, `fop`, `jython`, `jme`, `pmd`, `sunflow`, and `xalan`.

The version of H2 in Bach runs in process; e.g., it doesn't start a server and communicate over a socket.
To test JMVX's capability to work with servers, we included an H2 server benchmark (referred to as 'jmvx-bench' in scripts).
It runs the same test bench as Bach's H2, but between two separate processes connected by a socket.

### Experiments
The larger test scripts from the "Step By Step" section make use of may smaller scripts located in `/java-mvx/artifact/experiments`.
The following subsections document their usage.

#### Baseline and Instrumentation Overhead

* `dacapo-instr.sh [benchmarks] [modes] [N] [output dir] [threads]` -- gathers uninstrumented data and measures instrumentation overhead for DaCapo Bach
    - `benchmarks` -- a string of benchmarks to run (e.g., "batik avrora") or "all"
    - `modes` -- modes to run, options: "vanilla", "sync", "nosync", and/or "all"
    - `N` -- number of iterations to run per benchmark per mode
    - `output dir` -- *optional, default: results*, name of the output directory to place logs into
    - `threads` -- *optional, default: 9*, number of threads to limit the benchmark to.
* `chopin-instr.sh [benchmarks] [modes] [N] [output dir] [threads]` -- see above, but for DaCapo Chopin
* `jmvx-bench-instr.sh [modes] [N] [output dir] [threads]` -- see above, but for just the H2 client server benchmark

Here is an example usage of one of the scripts:
```
chopin-instr.sh "avrora" "all" 2 data/results 9
```
That command will run `avrora` from DaCapo Chopin on all modes (e.g., unistrumented, instrumented, and instrumented without synchronization).
It will run `avrora` twice (excluding warmups), and save a log of the output to data/results.
Finally, the benchmark will be set to use 9 threads.
Note, the `env.sh` script sets an environment variable called `THREADS` which contains the number of cpu cores JMVX allows the JVM to use in the scripts.
You can use that to prevent the benchmarks from using too many threads.

#### MVX

* `dacapo-mvx.sh [benchmarks] [modes] [N] [output dir] [threads]` -- gathers MVX data for DaCapo Bach
    - `benchmarks` -- a string of benchmarks to run (e.g., "batik avrora") or "all"
    - `modes` -- modes to run, options: "jmvx" and/or "nosync"
    - `N` -- number of iterations to run per benchmark per mode
    - `output dir` -- *optional, default: results*, name of the output directory to place logs into
    - `threads` -- *optional, default: 9*, number of threads to limit the benchmark to **PER ROLE**; setting to 2 uses 4 threads total (2 for leader and 2 for follower).
* `chopin-mvx.sh [benchmarks] [modes] [N] [output dir] [threads]` -- see above, but for DaCapo Chopin
* `jmvx-bench-mvx.sh [modes] [N] [output dir] [threads]` -- see above, but for just the H2 client server benchmark

Script example usage:
```
dacapo-mvx.sh "luindex" "all" 2 data/results 9
```
This will run `luindex` from DaCapo Bach in MVX (with and without syncrhonization).

#### Recording

* `dacapo-rec.sh [benchmarks] [modes] [N] [output dir] [threads]` -- gathers data for Recorder performance in DaCapo Bach
    - `benchmarks` -- a string of benchmarks to run (e.g., "batik avrora") or "all"
    - `modes` -- modes to run, options: "vanilla", "rec", and/or "nosync"
    - `N` -- number of iterations to run per benchmark per mode
    - `output dir` -- *optional, default: results*, name of the output directory to place logs into
    - `threads` -- *optional, default: 9*, number of threads to limit the benchmark to. 
* `chopin-rec.sh [benchmarks] [modes] [N] [output dir] [threads]` -- see above, but for DaCapo Chopin
* `jmvx-bench-rec.sh [modes] [N] [output dir] [threads]` -- see above, but for just the H2 client server benchmark

Script example usage:
```
chopin-rec.sh "avrora" "all" 2 data/results 9
```

#### Replaying

* `dacapo-rep.sh [benchmarks] [modes] [N] [output dir] [threads]` -- gathers data for Replayer performance in DaCapo Bach
    - `benchmarks` -- a string of benchmarks to run (e.g., "batik avrora") or "all"
    - `modes` -- modes to run, options: "vanilla", "rep", and/or "nosync"
    - `N` -- number of iterations to run per benchmark per mode
    - `output dir` -- *optional, default: results*, name of the output directory to place logs into
    - `threads` -- *optional, default: 9*, number of threads to limit the benchmark to. 
* `chopin-rep.sh [benchmarks] [modes] [N] [output dir] [threads]` -- see above, but for DaCapo Chopin
* `jmvx-bench-rep.sh [modes] [N] [output dir] [threads]` -- see above, but for just the H2 client server benchmark

Script example usage:
```
chopin-rep.sh "avrora" "all" 2 data/results 9
```

### Generating Tables
In `java-mvx/artifact/experiments`, several scripts exist to create tables.
Table scripts usually take three arguments:

* `[directory]` -- containing input file
* `[format]` -- output format of the table (txt, latex, or csv) 
* `[N]` -- number of successful runs required to be included in the chart.

Not all of the modes output the same graph.
Scripts makes tables for measuring: 

* `table-instr.py [directory] [mode]` --  instrumentation overhead
* `table-mvx.py [directory] [format] [N]` -- performance of MVX mode.  Latex mode produces the table in the paper.
* `table-rr.py [directory] [format] [N]`  -- perofrmance recording and record reply (see sub bullets below)
    - txt mode -- recording
    - latex mode -- record replay (this is the table from the paper)
    - csv mode -- used for some of the graphing scripts.
* `table-rec.py [directory] [format] [N]` -- outdated, use `table-rr.py` instead.
* `table-rep.py [directory] [format] [N]` -- performance of the replayer.
* `table-mem.py [directory] [mode] [format]` -- memory overhead of JMVX.
    - Mode is the JMVX mode to make a table on (e.g., "mvx", "rec", or "rep")
    - Parts of this script are used by others to compute memory overhead, the formatting of the table from the script may be out of date.

Some of the formatting between the txt and latex outputs differ as the txt formatter was used for quick feedback.

### Graphing
Scripts to graph data are available in `/java-mvx/artifact/experiments`.
These scripts rely on csv files created by the table generation scripts.
The commands are documented below.

* `make_graphs.py [input directory] [output directory] [format]` -- makes graphs for instrumentation, MVX, and RR.
* `make_graphs_threads.py [input directory] [output directory] [format]` -- makes graphs for varying threads experiment.
* `make_graphs_buffers.py [input directory] [output directory] [format]` -- makes graphs for varying buffer size experiment.
* `graph_utils.py` -- contains utility functions used by other graphing scripts.

All commands use the same arguments, described below:

* `[input directory]` -- directory of input csvs to use to generate the graph
* `[output directory]` -- directory to save graphs to
* `[format]` -- Format to save the graph as (any format supported by matplotlib works, we tend to use png or pdf)

# Reusability Guide
To be reusable JMVX is:

1. Open source -- source code is included in the artifact (and will be made public on git once the paper is accepted).
2. Rebuildable -- build script is included.
3. Can trace other applications.
4. Can instrument other applications.
5. Can run an instrumented application.

The following sections will describe each of these points respectively.

## Source Code
All of the source is supplied in the docker container.
Upon entering the container, you will be placed at `/java-mvx/artifact/`.
If you go back a directory, you will see a `src` directory containing all of our source code.
The core parts of the code are the `bytecode` and `runtime` packages, which manage instrumentation and handling intercepted methods respectively.

## Building JMVX

* Go to `/java-mvx/artifact/scripts` and run `./build-jmvx.sh`.

Maven is pre-installed in the container and has already downloaded the necessary dependencies.

## Tracing New Applications
To trace an application, the following command can be used:
```
strace -f -o /dev/null -e signal=SIGSEGV -e 'trace=!%signal' \
	-e 'inject=!%signal,futex,write:signal=SIGUSR2:when=70+' \ 
	java -DlogNatives="true" -Xbootclasspath/p:$INSTALL_DIR/tracer/rt.jar:$JMVX_JAR \
	"-Djmvx.role=SingleLeaderWithoutCoordinator" <program> <args>
```

For the DaCapo benchmarks, we split this command up between two scripts:

1. strace part -- `/java-mvx/artifact/scripts/tracer/run-tracer.sh`
2. java (with JMVX args) part -- `/java-mvx/artifact/experiment/dacapo/tracer.sh` (there is another in `dacapo-chopin`)

Whenever a system call (except futex and write) occurs, strace will send a signal (`SIGUSR2`) to JMVX.
The `java` command has additional arguments to tell JMVX to trace natives (`-DlogNatives=true`).
In addition, the jdk we instrumented for tracing is used instead of the default one (`-Xbootclasspath` part of the command).
JMVX is also told to run in `SingleLeaderWithoutCoordinator` mode.
Other than that, `<program>` and `<args>` are for the java program you are running.
For more details on how JMVX handles the signal and determines what method made the system call, refer to Section 3.2 of the paper.

This command should produce a logfile called `natives.log`.
This log file can be analyzed with the script `/java-mvx/artifact/scripts/tracer/analyze2.sh`.
For instance running: `analyze2.sh simple natives.log` prints potential methods to instrument to support the benchmark.
We would inspect this file to determine if a benchmark needs additional support, which would require adding code to the `bytecode` and `runtime` packages of JMVX.

## Instrumentation
To run JMVX, the target program must be instrumented. 
This can be done with the command:

```
java -Xmx8G -Dlogfile="instrumenter.log" -Djmvx.file="<program name>" \
	-cp $JMVX_JAR:<program class path> \
	edu.uic.cs.jmvx.Main -instrument <program install dir> <instrumentated dir>
```

JMVX needs to know the name of the program being instrumented (via the `-Djmvx.file` property).
This property is used to differentiate a target application from the jdk itself, so we can turn off jdk specific instrumentation, speeding up the process.
The program must also be on the class path when instrumenting.
The latter two arguments are: the directory containing the programs bytecode/jar(s) and a directory to place the instrumented code into.

Examples of this command can be found in many files in `/java-mvx/artifact/scripts`.
Instrumentation scripts there are tailored for the JVM, DaCapo, and the H2 server (referred to as JMVX Bench in the code).
Below is a brief descriptions of the (important) instrumentation scripts:

* `instrument-jvm.sh` - Instruments the JVM pointed to by `$JAVA_HOME`, defaults to the jdk supplied in the container
* `instrument-jvm-nosync.sh` - Same as above, but does not instrument monitors and synchronized methods. 
* `instrument-jvm-tracer.sh` - same as `instrument-jvm.sh`, but instruments for the purpose of tracing system calls.
* `instrument-dacapo.sh` - Instruments the Bach version of DaCapo.
* `instrument-dacapo-nosync.sh` - Same as above, but turns off synchronization instrumentation.
* `instrument-dacapo-chopin.sh` - Instruments the Chopin version of DaCapo. This takes a while, around 30-40 minutes.
* `instrument-dacapo-chopin-nosync.sh` - Same as above, but turns off synchronization instrumentation.

Note: after instrumenting the Chopin version of DaCapo, you need to recompte the checksums on the jars!
If you don't the benchmarks will error out.
This can be done with the commands:

* `compute_md5_chopin.py $DACAPO_CHOPIN_INSTRUMENTED`
* `compute_md5_chopin.py $DACAPO_CHOPIN_INSTRUMENTED_NOSYNC`

The variables `$DACAPO_CHOPIN_INSTRUMENTED` and `$DACAPO_CHOPIN_INSTRUMENTED_NOSYNC` are defined in `env.sh`, which is sourced on container start up.

## Running JMVX on a New Program
Once the program has been instrumented, you can run it via the command:

```
java -Dlogfile=<logfile name> -Xbootclasspath/p:$INSTALL_DIR/rt.jar:$JMVX_JAR \
	-Djmvx.role=<input role> -cp <instrumented program dir> <main class> <args>
```

This command uses the instrumented jdk adds JMVX to the bootstrap classes (with the `-Xbootclasspath`).
You also need to add some information to the command:

* Logfile name, usually mimics the role name.
* Role, which tells JMVX what to do. Options are:
    - Leader/Follower for MVX
    - Recorder/Replayer for RR
    - SingleLeaderWithoutCoordinator for passthrough
* Instrumented program directory used from the instrumentation step

Examples of this command can be seen in the directory `/java-mvx/artifact/experiment/dacapo` in files such as:

* `run-leader.sh`
* `run-follower.sh`
* `run-recorder.sh`
* `run-replayer.sh`

As these run DaCapo with each of those roles respectively.

