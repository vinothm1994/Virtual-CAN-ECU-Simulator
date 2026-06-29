#!/usr/bin/env bash
# Reproducibly (cross-)builds libdbcppp.so for aarch64 and installs it into
# linux-aarch64/. Run from an x86_64 host; produces a Pi-compatible library.
#
#   ./build_dbcppp_aarch64.sh
#
# Uses the ARM GNU 12.3 toolchain (gcc 12.3 / glibc 2.36) to match Debian
# bookworm (Raspberry Pi OS) — the produced .so needs only GLIBCXX_3.4.29 /
# GLIBC_2.17, so it loads on the Pi. Set CROSS_TC to reuse an existing toolchain.
#
# Alternatively, just build dbcppp natively on the Pi and copy its
# libdbcppp.so.3.8.0 + the libdbcppp.so symlink into linux-aarch64/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HERE/linux-aarch64"
DBCPPP_COMMIT="b520607"   # v3.2.6-26-g b520607 (matches linux-x86_64 build)
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- toolchain ---
if [[ -z "${CROSS_TC:-}" ]]; then
    TCVER="arm-gnu-toolchain-12.3.rel1-x86_64-aarch64-none-linux-gnu"
    URL="https://developer.arm.com/-/media/Files/downloads/gnu/12.3.rel1/binrel/${TCVER}.tar.xz"
    echo "[a64] downloading $TCVER ..."
    curl -fsSL -o "$WORK/tc.tar.xz" "$URL"
    tar -xf "$WORK/tc.tar.xz" -C "$WORK"
    CROSS_TC="$WORK/$TCVER"
fi
GXX="$CROSS_TC/bin/aarch64-none-linux-gnu-g++"
GCC="$CROSS_TC/bin/aarch64-none-linux-gnu-gcc"
"$GXX" --version | head -1

# --- source ---
echo "[a64] cloning dbcppp @ $DBCPPP_COMMIT ..."
git clone -q https://github.com/xR3b0rn/dbcppp.git "$WORK/src"
git -C "$WORK/src" checkout -q "$DBCPPP_COMMIT"   # third-party/boost is vendored (not a submodule)

# --- cross build (core only: no KCD => no libxml; vendored boost headers) ---
cat > "$WORK/tc.cmake" <<EOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER   $GCC)
set(CMAKE_CXX_COMPILER $GXX)
EOF
cmake -S "$WORK/src" -B "$WORK/build" \
    -DCMAKE_TOOLCHAIN_FILE="$WORK/tc.cmake" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_DISABLE_FIND_PACKAGE_Boost=ON \
    -Dbuild_kcd=OFF -Dbuild_tools=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF
cmake --build "$WORK/build" -j"$(nproc)"

# --- install ---
mkdir -p "$DEST"
cp -f "$WORK/build/libdbcppp.so.3.8.0" "$DEST/"
ln -sf libdbcppp.so.3.8.0 "$DEST/libdbcppp.so"
echo "[a64] installed:"
ls -la "$DEST"
file "$DEST/libdbcppp.so.3.8.0" | cut -d, -f1-2
