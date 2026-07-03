# Prebuilt dbcppp (vendored, multi-platform)

`libdbcppp` and headers, vendored so vecu-sim builds with no external prefix.
Headers are arch-neutral (shared); the library is per platform.
`native/CMakeLists.txt` picks the right one by target OS+arch.

```
include/dbcppp/        headers (shared across platforms)
linux-x86_64/          libdbcppp.so.3.8.0   (+ libdbcppp.so symlink)
linux-aarch64/         libdbcppp.so.3.8.0   (+ libdbcppp.so symlink)
windows-x86_64/        libdbcppp.dll (+ import lib) + the built vecunative.dll
                       and the llvm-mingw C++ runtime (libc++.dll, libunwind.dll)
```

- Source: https://github.com/xR3b0rn/dbcppp @ `b520607` (v3.2.6-26-g)
- Internal version 3.8.0 (SONAME `libdbcppp.so.3.8.0`)
- Built core-only: `build_kcd=OFF` ⇒ no libxml2/boost runtime dependency
  (only libstdc++/libm/libgcc_s/libc).

## linux-x86_64

Built natively on x86_64 Linux (same source/options as below).

## linux-aarch64  (Raspberry Pi / Pi 5)

Cross-compiled with the **ARM GNU 12.3 toolchain** (gcc 12.3 / glibc 2.36) to
match Debian bookworm (Pi OS). Verified:

- symbol requirements ≤ `GLIBCXX_3.4.29` / `GLIBC_2.17` (bookworm provides
  3.4.30 / 2.36), so it loads on the Pi;
- functionally exercised under `qemu-aarch64-static` (loaded `hvac.dbc`:
  4 messages, HvacStatus has 14 signals).

Reproduce / refresh with [`build_dbcppp_aarch64.sh`](build_dbcppp_aarch64.sh),
or build dbcppp natively on the Pi and copy `libdbcppp.so.3.8.0` (+ the
`libdbcppp.so` symlink) here.

## windows-x86_64

Cross-compiled from Linux with **llvm-mingw** (clang 18, UCRT). Unlike the Linux
builds, this folder ships the **whole native bridge** so a Windows user needs
only a JDK (no C++ toolchain):

- `libdbcppp.dll` (+ `libdbcppp.dll.a` import lib) — dbcppp, built with
  `-DDBCPPP_EXPORT`, KCD off, vendored Boost.
- `vecunative.dll` — the JNI bridge (dbc + PCAN-Basic), all 10 `Java_com_vecu_*`
  entrypoints exported.
- `libc++.dll`, `libunwind.dll` — the shared C++ runtime, so both DLLs use one
  runtime instance. `NativeLoader` loads them (then dbcppp, then the bridge) in
  order.

`PCANBasic.dll` is **not** a static dependency — it is loaded on demand at
Connect time (it comes with the PEAK driver install). The `api-ms-win-crt-*`
imports are the Windows 10/11 Universal CRT (OS-provided).

Reproduce with [`build_windows_x86_64.sh`](build_windows_x86_64.sh). Not yet
run-tested on Windows (built on a Linux CI host) — see the repo README's Windows
section for the on-device smoke test.
