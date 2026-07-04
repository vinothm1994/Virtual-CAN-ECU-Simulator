#!/usr/bin/env bash
# Pretends to be the IVI head unit: sends HvacControl / HvacTempControl request
# frames onto vcan0 so you can watch the running simulator react (the widgets
# move and it starts transmitting HvacStatus / HvacTemperatures back).
#
# Usage:
#   1. sudo scripts/setup_vcan.sh          # once, to create vcan0
#   2. ./gradlew run                        # start the simulator, click Connect + Start ECU
#   3. candump vcan0                        # (optional, another terminal) watch the bus
#   4. scripts/ivi_demo.sh                  # run this to drive it
#
# CAN ids (from hvac.dbc): HvacControl=0x510, HvacTempControl=0x511.
set -euo pipefail
IFACE="${1:-vcan0}"
send() { echo "  -> $1#$2"; cansend "$IFACE" "$1#$2"; sleep 1; }

echo "[ivi] Power ON + A/C ON + fan speed 5   (HvacControl 0x510)"
#      byte0 bit0=PowerOnReq, bit1=AcOnReq -> 0x03 ; byte2 = FanSpeedReq = 5
send 510 0300050000000000

echo "[ivi] Driver setpoint 24.0 C           (HvacTempControl 0x511)"
#      16-bit LE, scale 0.1 -> 240 = 0x00F0 -> bytes F0 00
send 511 F000000000000000

echo "[ivi] Dual zone ON + fan speed 7"
#      byte0 bit6=DualOnReq(0x40)+Power(0x01)+Ac(0x02)=0x43 ; fan=7
send 510 4300070000000000

echo "[ivi] Everything OFF (power off gates the rest)"
send 510 0000000000000000

echo "[ivi] done. If the simulator was Connected + ECU running, its widgets"
echo "      tracked each command and it transmitted status back on 0x500/0x501."
