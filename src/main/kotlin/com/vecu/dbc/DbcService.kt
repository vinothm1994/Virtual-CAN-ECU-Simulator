package com.vecu.dbc

import com.vecu.can.CanFrame
import com.vecu.config.NativeLoader

/** A decoded message: which message, and physical value per signal name. */
data class DecodedMessage(
    val message: MessageInfo,
    val values: Map<String, Double>,
)

/**
 * The only Kotlin class that talks to dbcppp (via [DbcNative]). Deals purely in
 * message/signal names and physical (engineering-unit) values.
 */
class DbcService {
    private var handle: Long = 0
    lateinit var schema: DbcSchema
        private set

    /** Loads and indexes the DBC. Throws on failure. */
    fun load(path: String) {
        NativeLoader.ensureLoaded()
        handle = DbcNative.loadDbc(path)
        check(handle != 0L) { "failed to load DBC: $path" }
        schema = DbcSchema.parse(DbcNative.schemaJson(handle))
    }

    /** Decodes a frame, or null if no message in the DBC matches its id. */
    fun decode(frame: CanFrame): DecodedMessage? {
        val message = schema.messageForFrame(frame.id, frame.extended) ?: return null
        val values = DbcNative.decode(handle, frame.id, frame.extended, frame.data) ?: return null
        val names = message.signalNames
        val map = LinkedHashMap<String, Double>(names.size)
        for (i in names.indices) {
            map[names[i]] = if (i < values.size) values[i] else 0.0
        }
        return DecodedMessage(message, map)
    }

    /** Encodes named physical values into a frame for [message]. Null if unknown. */
    fun encode(message: String, values: Map<String, Double>): CanFrame? {
        if (!schema.messageByName.containsKey(message)) return null
        val names = values.keys.toTypedArray()
        val vals = DoubleArray(names.size) { values.getValue(names[it]) }
        val packed = DbcNative.encode(handle, message, names, vals) ?: return null
        return CanFrame.unpack(packed)
    }

    fun close() {
        if (handle != 0L) {
            DbcNative.release(handle)
            handle = 0
        }
    }
}
