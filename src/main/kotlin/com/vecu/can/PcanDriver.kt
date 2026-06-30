package com.vecu.can

/**
 * Windows PEAK PCAN-USB driver. Placeholder for the MVP — the abstraction is in
 * place so a PCAN-Basic (JNA/JNI) implementation can drop in without touching
 * anything above [CanDriver]. On Linux the app always selects [SocketCanDriver].
 */
class PcanDriver(private val channel: String = "PCAN_USBBUS1") : CanDriver {
    override val isOpen: Boolean get() = false
    override val name: String get() = "PCAN $channel"

    override fun open() =
        throw UnsupportedOperationException(
            "PCAN driver not implemented in the MVP; run on Linux with SocketCAN.",
        )

    override fun close() {}
    override fun send(frame: CanFrame) {}
    override fun setListener(listener: (CanFrame) -> Unit) {}
}
