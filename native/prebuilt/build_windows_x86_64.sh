#!/usr/bin/env bash
# Reproducibly cross-builds the Windows x86_64 native bundle from a Linux host
# and installs it into windows-x86_64/:
#   libdbcppp.dll (+ import lib), vecunative.dll, libc++.dll, libunwind.dll
#
#   WIN_JDK=/path/to/windows-jdk ./build_windows_x86_64.sh
#
# Requires a Windows JDK's JNI headers (include/jni.h + include/win32/jni_md.h)
# via WIN_JDK. Uses llvm-mingw (downloaded to scratch unless CROSS_TC is set).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HERE/windows-x86_64"
NATIVE_DIR="$(cd "$HERE/.." && pwd)"          # the native/ dir (has CMakeLists.txt)
DBCPPP_COMMIT="b520607"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

: "${WIN_JDK:?set WIN_JDK to a Windows JDK dir (with include/jni.h + include/win32/jni_md.h)}"
test -f "$WIN_JDK/include/jni.h" || { echo "no jni.h under $WIN_JDK/include"; exit 1; }

# --- toolchain (llvm-mingw, Linux-hosted, targets Windows) ---
if [[ -z "${CROSS_TC:-}" ]]; then
    TCV="llvm-mingw-20240619-ucrt-ubuntu-20.04-x86_64"
    echo "[win] downloading $TCV ..."
    curl -fsSL -o "$WORK/tc.tar.xz" \
        "https://github.com/mstorsjo/llvm-mingw/releases/download/20240619/${TCV}.tar.xz"
    tar -xf "$WORK/tc.tar.xz" -C "$WORK"
    CROSS_TC="$WORK/$TCV"
fi
RTDIR="$CROSS_TC/x86_64-w64-mingw32/bin"

cat > "$WORK/win.cmake" <<EOF
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)
set(CMAKE_C_COMPILER   $CROSS_TC/bin/x86_64-w64-mingw32-gcc)
set(CMAKE_CXX_COMPILER $CROSS_TC/bin/x86_64-w64-mingw32-g++)
set(CMAKE_RC_COMPILER  $CROSS_TC/bin/x86_64-w64-mingw32-windres)
set(CMAKE_FIND_ROOT_PATH $CROSS_TC)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
EOF

# byteswap.h shim (dbcppp includes <byteswap.h>, a glibc-only header, on MinGW).
mkdir -p "$WORK/shim"
printf '#pragma once\n#include <cstdint>\n#define bswap_16(x) __builtin_bswap16(x)\n#define bswap_32(x) __builtin_bswap32(x)\n#define bswap_64(x) __builtin_bswap64(x)\n' \
    > "$WORK/shim/byteswap.h"

mkdir -p "$DEST"

# --- 1) libdbcppp.dll ---
echo "[win] building libdbcppp.dll ..."
git clone -q https://github.com/xR3b0rn/dbcppp.git "$WORK/dbc"
git -C "$WORK/dbc" checkout -q "$DBCPPP_COMMIT"
cmake -S "$WORK/dbc" -B "$WORK/dbc-b" \
    -DCMAKE_TOOLCHAIN_FILE="$WORK/win.cmake" -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_DISABLE_FIND_PACKAGE_Boost=ON \
    -DCMAKE_CXX_FLAGS="-DDBCPPP_EXPORT -I$WORK/shim" \
    -Dbuild_kcd=OFF -Dbuild_tools=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF
cmake --build "$WORK/dbc-b" -j"$(nproc)"
cp -f "$WORK/dbc-b/libdbcppp.dll" "$WORK/dbc-b/libdbcppp.dll.a" "$DEST/"

# --- 2) vecunative.dll (dbc + pcan JNI) ---
echo "[win] building vecunative.dll ..."
cmake -S "$NATIVE_DIR" -B "$WORK/vecu-b" \
    -DCMAKE_TOOLCHAIN_FILE="$WORK/win.cmake" -DCMAKE_BUILD_TYPE=Release \
    -DVECU_JNI_INCLUDE="$WIN_JDK/include;$WIN_JDK/include/win32" \
    -DDBCPPP_PREFIX="$HERE"
cmake --build "$WORK/vecu-b"
cp -f "$WORK/vecu-b/vecunative.dll" "$DEST/"

# --- 3) shared C++ runtime ---
cp -f "$RTDIR/libc++.dll" "$RTDIR/libunwind.dll" "$DEST/"

echo "[win] installed:"
ls -la "$DEST"
