# syntax=docker/dockerfile:1
FROM ubuntu:22.04
SHELL ["/bin/bash", "-c"]

#LABEL author="ldesa3@uic.edu"
#LABEL maintainer="dschwa23@uic.edu / luispina@uic.edu"
LABEL version="1.0"

# Copying files
WORKDIR /java-mvx
COPY . /java-mvx
COPY jdk1.8.0_231 /jdk8

#util/os setup
#perl is in image, but missing deps that are in full installation
#lsof is used by monitorH2.pl
#python is used in scripts to make tables, graphs, compute checksums etc
#venv needed to get pip installed
#maven for builds
#screen for managing processes in experiment
#strace used for tracing system calls
RUN apt update -y && apt upgrade -y && apt install -y vim tmux htop strace perl lsof python3 python3-venv maven screen

#python setup
ENV python3=python3
ENV python=python3
RUN $python -m venv env && source env/bin/activate && \
pip install -r /java-mvx/artifact/requirements.txt

#JMVX env, build, and instrumentation
# NOTE: MAKE SURE ALL JAVA CODE IS NOT CONNECTING TO A DEBUG PORT
# This can cause the build to hang!
RUN cd /java-mvx/artifact/scripts && cp docker-specific.sh machine-specific.sh
# RUN cd /java-mvx/artifact && source env.sh && cd scripts && \
# ./build-jmvx.sh && ./instrument-jvm.sh && ./instrument-jvm-nosync.sh && \
# ./instrument-dacapo.sh && ./instrument-dacapo-nosync.sh && \
# ./instrument-jmvx-bench.sh && ./instrument-jmvx-bench-nosync.sh \
# ./instrument-dacapo-chopin.sh && ./instrument-dacapo-chopin-nosync.sh && \
# $python compute-checksum.py /java-mvx/artifact/software/dacapo-chopin-inst && \
# $python compute-checksum.py /java-mvx/artifact/software/dacapo-chopin-inst-nosync

#ENV MVX_CONTAINER=1
#call affinity now to make sure the tasksets are done with respect to the host's processor
#and not the person who builds the image
#the source flows into the bash login shell!
ENTRYPOINT source env/bin/activate && cd /java-mvx/artifact && $python scripts/affinity.py "export" && source env.sh && bash -l

