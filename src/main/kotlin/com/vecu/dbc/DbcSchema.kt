package com.vecu.dbc

import org.yaml.snakeyaml.Yaml

/** One signal's static description, from the DBC. */
data class SignalInfo(
    val name: String,
    val start: Int,
    val length: Int,
    val unit: String,
    val min: Double,
    val max: Double,
    val factor: Double,
    val signed: Boolean,
    /** DBC value descriptions (VAL_), raw value -> label; empty if not enumerated. */
    val values: Map<Long, String>,
) {
    val isEnum: Boolean get() = values.isNotEmpty()
}

/** One message: id + ordered signals. */
data class MessageInfo(
    val name: String,
    val id: Int,
    val extended: Boolean,
    val dlc: Int,
    val signals: List<SignalInfo>,
) {
    val signalNames: List<String> = signals.map { it.name }
}

/**
 * Whole-database index, built once from [DbcNative.schemaJson]. Provides the
 * name/id lookups the rest of the app needs; nobody else parses DBC structure.
 */
class DbcSchema(val messages: List<MessageInfo>) {
    val messageByName: Map<String, MessageInfo> = messages.associateBy { it.name }
    private val messageByKey: Map<Long, MessageInfo> = messages.associateBy { keyOf(it.id, it.extended) }

    val signalInfo: Map<String, SignalInfo> =
        messages.flatMap { it.signals }.associateBy { it.name }
    val messageOfSignal: Map<String, MessageInfo> =
        messages.flatMap { m -> m.signals.map { it.name to m } }.toMap()

    fun messageForFrame(id: Int, extended: Boolean): MessageInfo? = messageByKey[keyOf(id, extended)]

    private fun keyOf(id: Int, extended: Boolean): Long =
        (if (extended) 1L shl 32 else 0L) or (id.toLong() and 0x1FFFFFFF)

    companion object {
        /** Parses the JSON emitted by the native bridge (JSON is valid YAML). */
        @Suppress("UNCHECKED_CAST")
        fun parse(json: String): DbcSchema {
            val root = Yaml().load<Map<String, Any?>>(json)
            val msgs = (root["messages"] as? List<Map<String, Any?>> ?: emptyList()).map { m ->
                val signals = (m["signals"] as? List<Map<String, Any?>> ?: emptyList()).map { s ->
                    val rawValues = s["values"] as? Map<Any?, Any?> ?: emptyMap()
                    SignalInfo(
                        name = s["name"].asString(),
                        start = s["start"].asInt(),
                        length = s["length"].asInt(),
                        unit = s["unit"].asString(),
                        min = s["min"].asDouble(),
                        max = s["max"].asDouble(),
                        factor = s["factor"].asDouble(1.0),
                        signed = s["signed"] as? Boolean ?: false,
                        values = rawValues.entries.associate {
                            it.key.toString().toLong() to it.value.toString()
                        },
                    )
                }
                MessageInfo(
                    name = m["name"].asString(),
                    id = m["id"].asInt(),
                    extended = m["eff"] as? Boolean ?: false,
                    dlc = m["dlc"].asInt(),
                    signals = signals,
                )
            }
            return DbcSchema(msgs)
        }

        private fun Any?.asString(): String = this?.toString() ?: ""
        private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: 0
        private fun Any?.asDouble(default: Double = 0.0): Double =
            (this as? Number)?.toDouble() ?: default
    }
}
