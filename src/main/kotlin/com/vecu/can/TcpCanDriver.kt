package com.vecu.can

import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * CAN over TCP — the transport that lets this app drive an ECU bus inside the
 * AAOS emulator while running on the host.
 *
 * The emulator's user-mode (SLIRP) networking cannot carry a CAN bus, IP
 * multicast, or inbound connections, so `cluster-can-gateway` runs *inside* the
 * guest against a real `vcan0`, and a small bridge there
 * (`vcan_tcp_bridge`, in the gateway repo) forwards frames over one TCP
 * connection. This driver is the other end of that connection.
 *
 * **This side is the server.** The emulator's NAT only forwards outbound
 * connections, so the guest must dial us; `10.0.2.2` inside the guest is the
 * host's loopback. Binding loopback-only is deliberate: it keeps Windows
 * Defender Firewall out of the picture entirely (no prompt on first run, no
 * exception to deploy), and costs nothing because `10.0.2.2` reaches it.
 *
 * Wire format — one fixed 16-byte record per frame, little-endian, matching
 * `struct can_frame` on a little-endian target so the bridge needs no
 * conversion:
 * ```
 *   offset  size  field
 *        0     4  can_id, including the EFF/RTR/ERR flag bits
 *        4     1  len (0..8)
 *        5     3  padding, zero
 *        8     8  data
 * ```
 * Classic CAN only; CAN-FD would need a different, length-prefixed format.
 *
 * There is no bitrate here. A TCP link has no wire speed, so [CanDriver.name]
 * reports the endpoint instead — showing "500K" would invent a number that
 * describes nothing. Frame timing comes entirely from the ECU TX schedulers.
 */
class TcpCanDriver(private val port: Int) : CanDriver {

    private companion object {
        const val WIRE_SIZE = 16
        const val EFF_FLAG = 0x8000_0000.toInt() // CAN_EFF_FLAG
        const val EFF_MASK = 0x1FFF_FFFF         // 29-bit id
        const val SFF_MASK = 0x0000_07FF         // 11-bit id
    }

    @Volatile private var server: ServerSocket? = null
    @Volatile private var peer: Socket? = null
    @Volatile private var out: DataOutputStream? = null
    @Volatile private var running = false

    private var listener: ((CanFrame) -> Unit)? = null
    private val sendLock = Any()

    override val isOpen: Boolean get() = running

    override val name: String get() =
        if (peer != null) "TCP 127.0.0.1:$port (connected)" else "TCP 127.0.0.1:$port (waiting)"

    override fun setListener(listener: (CanFrame) -> Unit) {
        this.listener = listener
    }

    override fun open() {
        if (running) return
        val s = try {
            ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            throw IllegalStateException(
                "cannot listen on 127.0.0.1:$port: ${e.message}. " +
                    "Is another vecu-sim already running?",
                e,
            )
        }
        server = s
        running = true
        // Accepting on a background thread keeps open() non-blocking: the
        // emulator usually boots after this app, and the UI must stay live
        // while nothing is connected yet.
        thread(name = "can-tcp-accept-$port", isDaemon = true) { acceptLoop(s) }
    }

    override fun close() {
        running = false
        // Close the server first so acceptLoop falls out instead of re-arming.
        runCatching { server?.close() }
        runCatching { peer?.close() }
        server = null
        peer = null
        out = null
    }

    override fun send(frame: CanFrame) {
        val stream = out ?: return // not connected yet; frames are dropped, as on an unplugged bus
        val buf = ByteArray(WIRE_SIZE)
        val id = if (frame.extended) (frame.id and EFF_MASK) or EFF_FLAG else frame.id and SFF_MASK
        buf[0] = (id and 0xFF).toByte()
        buf[1] = ((id ushr 8) and 0xFF).toByte()
        buf[2] = ((id ushr 16) and 0xFF).toByte()
        buf[3] = ((id ushr 24) and 0xFF).toByte()
        val len = frame.dlc.coerceIn(0, 8)
        buf[4] = len.toByte()
        // bytes 5..7 stay zero (padding)
        for (i in 0 until len) buf[8 + i] = frame.data.getOrElse(i) { 0 }
        try {
            // One writer at a time: several ECU TX schedulers share this driver.
            synchronized(sendLock) {
                stream.write(buf)
                stream.flush()
            }
        } catch (_: Exception) {
            dropPeer()
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val c = try {
                s.accept()
            } catch (_: Exception) {
                return // server closed by close()
            }
            // CAN frames are 16 bytes. With Nagle on, the stack coalesces them
            // into ~40 ms clumps and the cluster visibly stutters under load —
            // the single most likely "why is it laggy" bug in this path.
            runCatching { c.tcpNoDelay = true }
            peer = c
            out = DataOutputStream(c.getOutputStream())
            try {
                receiveLoop(c.getInputStream())
            } catch (_: Exception) {
                // fall through to cleanup; the bridge reconnects on its own
            }
            dropPeer()
        }
    }

    private fun receiveLoop(input: InputStream) {
        val buf = ByteArray(WIRE_SIZE)
        while (running) {
            readFully(input, buf)
            val id = (buf[0].toInt() and 0xFF) or
                ((buf[1].toInt() and 0xFF) shl 8) or
                ((buf[2].toInt() and 0xFF) shl 16) or
                ((buf[3].toInt() and 0xFF) shl 24)
            val extended = (id and EFF_FLAG) != 0
            val len = (buf[4].toInt() and 0xFF).coerceAtMost(8)
            val data = ByteArray(8)
            System.arraycopy(buf, 8, data, 0, len)
            listener?.invoke(
                CanFrame(
                    id = if (extended) id and EFF_MASK else id and SFF_MASK,
                    extended = extended,
                    data = data,
                    dlc = len,
                ),
            )
        }
    }

    /** TCP is a stream: a 16-byte record can arrive split across reads. */
    private fun readFully(input: InputStream, buf: ByteArray) {
        var got = 0
        while (got < buf.size) {
            val r = input.read(buf, got, buf.size - got)
            if (r < 0) throw EOFException("peer closed")
            got += r
        }
    }

    private fun dropPeer() {
        runCatching { peer?.close() }
        peer = null
        out = null
    }
}
