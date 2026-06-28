#!/usr/bin/env bash
# Builds libvecunative.so (dbcppp + SocketCAN JNI bridge) into native/build/.
# Invoked by the Gradle `buildNative` task; safe to run by hand.
set -euo pipefail
cd "$(dirname "$0")"

# JNI headers come from the JDK that runs the build. Resolve JAVA_HOME from the
# javac/java on PATH if it is not already set.
if [[ -z "${JAVA_HOME:-}" ]]; then
    bin="$(command -v javac || command -v java)"
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$bin")")")"
fi
export JAVA_HOME
echo "[buildNative] JAVA_HOME=$JAVA_HOME"

if [[ ! -f "$JAVA_HOME/include/jni.h" ]]; then
    echo "[buildNative] ERROR: jni.h not found under \$JAVA_HOME/include." >&2
    echo "[buildNative] Install a JDK (e.g. 'sudo apt install openjdk-11-jdk')." >&2
    exit 1
fi

cmake -S . -B build -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build build --parallel

echo "[buildNative] built: $(pwd)/build/libvecunative.so"
