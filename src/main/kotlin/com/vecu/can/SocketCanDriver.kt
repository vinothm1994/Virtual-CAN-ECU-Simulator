package com.vecu.can

import com.vecu.config.NativeLoader
import kotlin.concurrent.thread

/**
 * Linux SocketCAN driver. Works with `vcan0` (virtual) and `can0` (real)
 * interfaces alike. A dedicated thread blocks in the native receive call and
 * dispatches frames to the listener.
 */
class SocketCanDriver(private val iface: String) : CanDriver {
    @Volatile
    private var fd: Long = -1
    @Volatile
    private var rxThread: Thread? = null
    private var listener: ((CanFrame) -> Unit)? = null

    override val isOpen: Boolean get() = fd >= 0
    override val name: String get() = "SocketCAN $iface"

    override fun setListener(listener: (CanFrame) -> Unit) {
        this.listener = listener
    }

    override fun open() {
        if (isOpen) return
        NativeLoader.ensureLoaded()
        val result = SocketCanNative.openIface(iface)
        if (result < 0) {
            throw IllegalStateException(
                "cannot open CAN interface '$iface': ${errno(result)}. " +
                    "Is it up? Try: sudo ip link set $iface up (or scripts/setup_vcan.sh)",
            )
        }
        fd = result
        rxThread = thread(name = "can-rx-$iface", isDaemon = true) { receiveLoop() }
    }

    override fun close() {
        val f = fd
        fd = -1
        rxThread = null
        if (f >= 0) SocketCanNative.closeFd(f)
    }

    override fun send(frame: CanFrame) {
        val f = fd
        if (f < 0) return
        SocketCanNative.sendFrame(f, frame.id, frame.extended, frame.data, frame.dlc)
    }

    private fun receiveLoop() {
        val myFd = fd
        while (fd == myFd) {
            val packed = try {
                SocketCanNative.recvFrame(myFd, 200)
            } catch (_: Throwable) {
                null
            } ?: continue
            listener?.invoke(CanFrame.unpack(packed))
        }
    }

    private fun errno(negErrno: Long): String {
        val e = (-negErrno).toInt()
        val name = when (e) {
            1 -> "EPERM (need CAP_NET_RAW / root)"
            2 -> "ENOENT"
            19 -> "ENODEV (interface does not exist)"
            else -> "errno $e"
        }
        return name
    }
}
