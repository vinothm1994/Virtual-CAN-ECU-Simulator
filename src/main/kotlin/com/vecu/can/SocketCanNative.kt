package com.vecu.can

/** `external` binding to the SocketCAN JNI bridge. Use [SocketCanDriver]. */
internal object SocketCanNative {
    /** Opens a raw CAN socket bound to [iface]; returns fd (>=0) or -errno. */
    external fun openIface(iface: String): Long

    external fun closeFd(fd: Long)

    /** Sends a frame; returns 0 or -errno. */
    external fun sendFrame(fd: Long, canId: Int, eff: Boolean, data: ByteArray, dlc: Int): Int

    /**
     * Blocks up to [timeoutMs] for one frame. Returns the 13-byte packed layout
     * ([CanFrame.unpack]) or null on timeout/error.
     */
    external fun recvFrame(fd: Long, timeoutMs: Int): ByteArray?
}
