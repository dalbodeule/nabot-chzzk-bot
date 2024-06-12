#!/bin/bash

current_time=$(date +"%Y%m%d%H%M")
docker build -t dalbodeule/chzzkbot:latest -t dalbodeule/chzzkbot:$current_time  --push .