#!/bin/bash
# Below commands will recurisvely find all the files owned by root and delete them
docker exec -it java-mvx-container find /java-mvx/artifact/ -user root -exec rm -rf {} +
docker exec -it java-mvx-container find /java-mvx/target/ -user root -exec rm -rf {} +
docker container stop java-mvx-container
docker container rm java-mvx-container
docker image rm java-mvx
