#!/bin/bash

export RUN_AGENT=true
./gradlew -Pagent run
./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image