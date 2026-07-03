package com.vecu.can

/** `external` binding to the PCAN-Basic JNI bridge (Windows). Use [PcanDriver]. */
internal object PcanNative {
    /** Initializes a channel at a bitrate; returns 0 (PCAN_ERROR_OK) or a status. */
    external fun openChannel(channel: Int, baudrate: Int): Int

    external fun closeChannel(channel: Int)

    /** Sends a frame; returns 0 or a PCAN status. */
    external fun sendFrame(channel: Int, canId: Int, extended: Boolean, data: ByteArray, dlc: Int): Int

    /**
     * Blocks up to [timeoutMs] for one frame. Returns the 13-byte packed layout
     * ([CanFrame.unpack]) or null on timeout/error.
     */
    external fun recvFrame(channel: Int, timeoutMs: Int): ByteArray?

    /** Human-readable text for a PCAN status code. */
    external fun statusText(status: Int): String
}
