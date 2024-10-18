#!/bin/bash

# Kills Coordinator, Leader and Follower processes

fuser -k 5555/tcp; #Coordinator
fuser -k 5556/tcp; #Leader
fuser -k 5557/tcp; #Follower
