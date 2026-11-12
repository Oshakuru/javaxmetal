#!/bin/sh
set -eu
WORKSPACE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export JAVA_HOME="$WORKSPACE/.tooling/jdk25/Contents/Home"
export GRADLE_USER_HOME="$WORKSPACE/.gradle-local"
cd "$WORKSPACE/metallum"
exec ./gradlew build --no-daemon "$@"
