package com.vecu.can

/**
 * Transport-agnostic CAN interface. The rest of the app never knows whether it
 * is talking to SocketCAN (Linux) or PCAN (Windows).
 */
interface CanDriver {
    val isOpen: Boolean

    /** Human-readable identity for logs/UI, e.g. "SocketCAN vcan0". */
    val name: String

    /** Opens the bus and starts delivering received frames to the listener. */
    fun open()

    fun close()

    /** Transmits one frame. No-op behaviour when not open is driver-specific. */
    fun send(frame: CanFrame)

    /** Frames arrive on a background thread; keep the callback cheap. */
    fun setListener(listener: (CanFrame) -> Unit)
}
