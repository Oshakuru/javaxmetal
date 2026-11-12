#!/bin/sh
set -eu
WORKSPACE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sh "$WORKSPACE/build-metallum.sh" --offline
git -C "$WORKSPACE/metallum" diff --check
JOML_JAR=$(find "$WORKSPACE/.gradle-local/caches/modules-2/files-2.1/org.joml/joml/1.10.9" -name 'joml-1.10.9.jar' -print -quit)
if [ -z "$JOML_JAR" ]; then
    echo 'Missing cached JOML dependency' >&2
    exit 1
fi
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/java" --class-path "$JOML_JAR" "$WORKSPACE/validation/MetallumProjectionCheck.java"
CHECK_CLASSES="$WORKSPACE/metallum/build/validation"
mkdir -p "$CHECK_CLASSES"
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/javac" -d "$CHECK_CLASSES" \
    "$WORKSPACE/metallum/src/main/java/com/metallum/benchmark/FrameTimeStats.java" \
    "$WORKSPACE/validation/FrameTimeStatsCheck.java"
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/java" -cp "$CHECK_CLASSES" FrameTimeStatsCheck
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/javac" -cp "$JOML_JAR" -d "$CHECK_CLASSES" \
    "$WORKSPACE/metallum/src/main/java/com/metallum/diagnostic/CameraSnapshot.java" \
    "$WORKSPACE/validation/CameraSnapshotCheck.java"
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/java" -cp "$CHECK_CLASSES:$JOML_JAR" CameraSnapshotCheck
GSON_JAR=$(find "$WORKSPACE/.gradle-local/caches/modules-2/files-2.1/com.google.code.gson/gson/2.14.0" -name 'gson-2.14.0.jar' -print -quit)
if [ -z "$GSON_JAR" ]; then
    echo 'Missing cached Gson dependency' >&2
    exit 1
fi
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/javac" -cp "$GSON_JAR" -d "$CHECK_CLASSES" \
    "$WORKSPACE/metallum/src/main/java/com/metallum/material/MaterialCatalog.java" \
    "$WORKSPACE/validation/MaterialCatalogCheck.java"
"$WORKSPACE/.tooling/jdk25/Contents/Home/bin/java" -cp "$CHECK_CLASSES:$GSON_JAR" MaterialCatalogCheck \
    "$WORKSPACE/metallum/src/main/resources/assets/metallum/materials/default.json"
echo 'PASS: Java build, diff whitespace, and numerical reconstruction checks'
echo 'NOT CONFIGURED: standalone linter and coverage'
echo 'PENDING: runtime Metal shader and target gameplay checks for the latest JAR'
