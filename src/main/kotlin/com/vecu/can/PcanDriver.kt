package com.vecu.can

import com.vecu.config.NativeLoader
import kotlin.concurrent.thread

/**
 * Windows PEAK PCAN-USB driver over PCAN-Basic. Mirrors [SocketCanDriver]: a
 * dedicated thread blocks in the native receive call and dispatches to the
 * listener. `PCANBasic.dll` (from the PEAK driver install) is loaded on demand
 * by the native bridge, so the app runs without it until you actually Connect.
 */
class PcanDriver(
    private val channel: Int,
    private val baudrate: Int,
    private val channelName: String,
) : CanDriver {
    @Volatile
    private var open = false
    @Volatile
    private var rxThread: Thread? = null
    private var listener: ((CanFrame) -> Unit)? = null

    override val isOpen: Boolean get() = open
    override val name: String get() = "PCAN $channelName"

    override fun setListener(listener: (CanFrame) -> Unit) {
        this.listener = listener
    }

    override fun open() {
        if (open) return
        NativeLoader.ensureLoaded()
        val status = PcanNative.openChannel(channel, baudrate)
        if (status != 0) {
            throw IllegalStateException(
                "cannot open $channelName: ${PcanNative.statusText(status)}. " +
                    "Is the PEAK driver installed and the PCAN-USB plugged in?",
            )
        }
        open = true
        rxThread = thread(name = "can-rx-$channelName", isDaemon = true) { receiveLoop() }
    }

    override fun close() {
        if (!open) return
        open = false
        rxThread = null
        PcanNative.closeChannel(channel)
    }

    override fun send(frame: CanFrame) {
        if (!open) return
        PcanNative.sendFrame(channel, frame.id, frame.extended, frame.data, frame.dlc)
    }

    private fun receiveLoop() {
        while (open) {
            val packed = try {
                PcanNative.recvFrame(channel, 200)
            } catch (_: Throwable) {
                null
            } ?: continue
            listener?.invoke(CanFrame.unpack(packed))
        }
    }
}

/**
 * PCAN-Basic channel and bitrate constants (from PCANBasic.h). Maps the
 * human-readable names used in the YAML `can:` block to the numeric values the
 * native API expects.
 */
object Pcan {
    private val channels = mapOf(
        "PCAN_USBBUS1" to 0x51, "PCAN_USBBUS2" to 0x52, "PCAN_USBBUS3" to 0x53,
        "PCAN_USBBUS4" to 0x54, "PCAN_USBBUS5" to 0x55, "PCAN_USBBUS6" to 0x56,
        "PCAN_USBBUS7" to 0x57, "PCAN_USBBUS8" to 0x58,
    )
    private val baudrates = mapOf(
        "1M" to 0x0014, "800K" to 0x0016, "500K" to 0x001C, "250K" to 0x011C,
        "125K" to 0x031C, "100K" to 0x432F, "50K" to 0x472F, "20K" to 0x532F,
        "10K" to 0x672F,
    )

    /** Channel handle for a name like "PCAN_USBBUS1"; defaults to bus 1. */
    fun channel(name: String?): Int = channels[name?.uppercase()] ?: 0x51

    /** Bitrate code for a name like "500K"; defaults to 500 kbit/s. */
    fun baudrate(name: String?): Int = baudrates[name?.uppercase()] ?: 0x001C
}
