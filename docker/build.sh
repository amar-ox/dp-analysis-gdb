#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t "dpanalyzer" -f $DIR/../dpanalyzer/Dockerfile $DIR/../dpanalyzer