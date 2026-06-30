package com.vecu.can

/**
 * A CAN frame in application terms. [id] is the raw arbitration id (11- or
 * 29-bit, without flag bits); [extended] distinguishes 29-bit frames.
 */
data class CanFrame(
    val id: Int,
    val extended: Boolean,
    val data: ByteArray,
    val dlc: Int,
) {
    /** e.g. "03 00 05 00 DC 05 00 00" over [dlc] bytes. */
    fun hex(): String =
        (0 until dlc.coerceAtMost(data.size)).joinToString(" ") { "%02X".format(data[it]) }

    /** e.g. "0x500" (11-bit) or "0x18FF50E5" (29-bit). */
    fun idHex(): String = if (extended) "0x%08X".format(id) else "0x%03X".format(id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        return id == other.id && extended == other.extended && dlc == other.dlc &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int =
        ((id * 31 + extended.hashCode()) * 31 + dlc) * 31 + data.contentHashCode()

    companion object {
        private const val EXTENDED_FLAG = -0x80000000 // 0x80000000 as Int

        /**
         * Unpacks the 13-byte layout produced by the native encode/recv calls:
         * `[0..3] canId big-endian (bit31=extended), [4] dlc, [5..12] data`.
         */
        fun unpack(packed: ByteArray): CanFrame {
            val rawId = ((packed[0].toInt() and 0xFF) shl 24) or
                ((packed[1].toInt() and 0xFF) shl 16) or
                ((packed[2].toInt() and 0xFF) shl 8) or
                (packed[3].toInt() and 0xFF)
            val extended = (rawId and EXTENDED_FLAG) != 0
            val id = if (extended) rawId and 0x1FFFFFFF else rawId and 0x7FF
            val dlc = (packed[4].toInt() and 0xFF).coerceAtMost(8)
            val data = packed.copyOfRange(5, 13)
            return CanFrame(id, extended, data, dlc)
        }
    }
}
