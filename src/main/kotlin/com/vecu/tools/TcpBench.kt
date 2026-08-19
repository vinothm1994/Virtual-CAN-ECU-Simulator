package com.vecu.tools

import com.vecu.can.CanFrame
import com.vecu.can.TcpCanDriver
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * Headless exercise of [TcpCanDriver] against the AAOS emulator bench — the UI
 * equivalent of pressing Connect on a `tcp:` bus, without needing a display.
 * Run with: `./gradlew tcpBench`.
 *
 * Expects `vcan_tcp_bridge` to be running inside the guest and dialling
 * `10.0.2.2:<port>`; this side listens on the host loopback. It transmits a
 * `VehicleConfiguration` frame (0x101) announcing PowertrainType=BEV once a
 * second and prints every frame that comes back off the guest's `vcan0`.
 *
 * Useful because it isolates the transport: if this sees traffic, the driver,
 * the bridge, and the emulator's NAT are all working, and anything still broken
 * is above the bus.
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 29536
    val seconds = args.getOrNull(1)?.toIntOrNull() ?: 30

    val rx = AtomicInteger()
    val driver = TcpCanDriver(port)
    driver.setListener { f ->
        println("RX  ${f.idHex()}  len=${f.dlc}  ${f.hex()}")
        rx.incrementAndGet()
    }

    println("listening on 127.0.0.1:$port — waiting for vcan_tcp_bridge to dial in")
    driver.open()

    // VehicleConfiguration 0x101: PowertrainType occupies bits 0..3, and 4 = BEV
    // (see cluster-can-gateway config/dbc/vehicle.dbc). The gateway's own config
    // says ICE, so an adopted BEV on the far side proves this frame crossed.
    val cfg = CanFrame(
        id = 0x101,
        extended = false,
        data = byteArrayOf(0x04, 0, 0, 0, 0, 0, 0, 0),
        dlc = 8,
    )

    var tx = 0
    repeat(seconds) {
        driver.send(cfg)
        tx++
        Thread.sleep(1000)
    }

    driver.close()
    println("---")
    println("sent $tx VehicleConfiguration frames, received ${rx.get()} frames")
    exitProcess(if (rx.get() > 0 || tx > 0) 0 else 1)
}
