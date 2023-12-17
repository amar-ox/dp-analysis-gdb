#!/usr/bin/env bash

set -e

# export IP
IP=127.0.0.1
export EXTERNAL_IP=$IP

# Get this script directory (to find yml from any directory)
export DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Stop containers
docker-compose -f $DIR/docker-compose.yml stop

# Start persistence containers
docker-compose -f $DIR/docker-compose.yml up -d neo4j
sleep 60

# Start services
docker-compose -f $DIR/docker-compose.yml up