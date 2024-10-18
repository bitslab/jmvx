#!/bin/bash
source scripts/machine-specific.sh > /dev/null

if [ -z "$ROOT" ]; then
	echo "Please set project ROOT on file env.sh";
	echo "Or set it up on file scripts/machine-specific.sh";
	exit 1;
fi

if (! docker stats --no-stream); then
	echo "No docker daemon found";
	echo "Please launch the docker daemon";
	exit 1;
fi

# Build container from image
docker build -t java-mvx $ROOT/..
# https://stackoverflow.com/questions/54607149/containers-not-running-in-detached-mode
# Ugly hacker needed to keep the container running in detached mode
docker run -v $ROOT/..:/java-mvx -v $JAVA_HOME:/jdk8 -d --name java-mvx-container java-mvx tail -f /dev/null
docker exec -it java-mvx-container python3.8 artifact/scripts/affinity.py export
docker exec -it java-mvx-container bash
