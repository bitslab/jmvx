#!/bin/bash

export ROOT=/java-mvx/artifact
export JAVA_HOME=/jdk8

#variables to control settings in the experiment
#100 mb cirb buffer (arg is read as # of bytes)
export CIRC_BUFFER=$(( 100 * 1024 * 1024 ))
#max heap memory for java
#export MEM="-Xmx12G"
export CORE_DUMP="true"
