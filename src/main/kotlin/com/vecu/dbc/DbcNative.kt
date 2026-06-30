package com.vecu.dbc

/**
 * Thin `external` binding to the dbcppp JNI bridge. No dbcppp type ever crosses
 * this boundary — callers get a schema JSON string, physical values, or a packed
 * CAN frame. Use [DbcService] rather than calling these directly.
 */
internal object DbcNative {
    /** Loads a .dbc file; returns an opaque handle, or 0 on failure. */
    external fun loadDbc(path: String): Long

    /** Frees a handle from [loadDbc]. */
    external fun release(handle: Long)

    /** Full database as JSON (messages, signals, layout, scaling, enums). */
    external fun schemaJson(handle: Long): String

    /**
     * Decodes a frame into physical values, in signal-declaration order for the
     * matched message. Returns null if no message has this id.
     */
    external fun decode(handle: Long, canId: Int, eff: Boolean, data: ByteArray): DoubleArray?

    /**
     * Encodes named physical values into a packed frame:
     * `[0..3] canId big-endian (bit31=extended), [4] dlc, [5..12] data`.
     * Unknown signal names are ignored. Returns null if the message is unknown.
     */
    external fun encode(
        handle: Long,
        message: String,
        signals: Array<String>,
        values: DoubleArray,
    ): ByteArray?
}
