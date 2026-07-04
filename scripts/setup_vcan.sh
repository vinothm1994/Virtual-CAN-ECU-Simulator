#!/usr/bin/env bash
# Brings up a virtual CAN interface (vcan0) for local testing without hardware.
# Needs root (the ip link operations require CAP_NET_ADMIN), so run with sudo:
#
#     sudo scripts/setup_vcan.sh          # create + bring up vcan0
#     sudo scripts/setup_vcan.sh can1     # or a different name
#
set -euo pipefail
IFACE="${1:-vcan0}"

modprobe vcan 2>/dev/null || true

if ip link show "$IFACE" >/dev/null 2>&1; then
    echo "[setup_vcan] $IFACE already exists"
else
    ip link add dev "$IFACE" type vcan
    echo "[setup_vcan] created $IFACE"
fi

ip link set up "$IFACE"
echo "[setup_vcan] $IFACE is up:"
ip -details link show "$IFACE" | sed 's/^/    /'
