# Prebuilt dbcppp (vendored, multi-arch)

`libdbcppp` and headers, vendored so vecu-sim builds with no external prefix.
Headers are arch-neutral (shared); the `.so` is per-arch.
`native/CMakeLists.txt` picks the right one by target arch.

```
include/dbcppp/        headers (shared across arches)
linux-x86_64/          libdbcppp.so.3.8.0  (+ libdbcppp.so symlink)
linux-aarch64/         libdbcppp.so.3.8.0  (+ libdbcppp.so symlink)
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
