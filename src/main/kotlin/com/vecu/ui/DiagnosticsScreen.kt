package com.vecu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vecu.viewmodel.CanLogEntry
import com.vecu.viewmodel.Direction
import com.vecu.viewmodel.EcuInfo
import com.vecu.viewmodel.SimStatus
import kotlinx.coroutines.delay

/** Rolling window the rates are measured over. */
private const val WINDOW_MS = 5_000L

/** How many periods of a message must fit in its own window before its period
 *  can be measured at all. A 5 s message cannot show two samples in a 5 s
 *  window, so measuring every message against one fixed window reports the
 *  slowest ones as silent when they are transmitting perfectly. */
private const val PERIODS_NEEDED = 3

/** Hard bound on the scan, so a fast bus cannot walk the whole log. */
private const val WINDOW_ROWS = 20_000

/**
 * Bus health: what is actually on the wire, against what the YAML said would be.
 *
 * Everything here is derived from the CAN log the app already keeps — no extra
 * capture path, so what you see is exactly what the monitor saw.
 */
@Composable
fun DiagnosticsScreen(entries: List<CanLogEntry>, status: SimStatus, ecus: List<EcuInfo>) {
    // Rates are a moving window, so this recomputes on a clock rather than on
    // every frame: at bus speed that would be hundreds of passes a second.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val health = remember(tick, entries.size, ecus) { analyse(entries, ecus) }

    Column(Modifier.fillMaxSize()) {
        PanelHeader("Diagnostics · bus health")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenCard("BUS") {
                KeyValueRow(
                    "State",
                    if (status.ecuRunning) "running" else if (status.connected) "connected, ECUs stopped" else "disconnected",
                    if (status.ecuRunning) VecuColors.ok else if (status.connected) VecuColors.rx else VecuColors.idle,
                )
                KeyValueRow("Driver", status.driverName.ifBlank { "—" })
                KeyValueRow("Frames in last ${WINDOW_MS / 1000} s", health.total.toString())
                KeyValueRow("RX rate", "%.1f /s".format(health.rxPerSec))
                KeyValueRow("TX rate", "%.1f /s".format(health.txPerSec))
                status.lastError?.let {
                    Spacer(Modifier.height(6.dp))
                    Note("Last error: $it", color = VecuColors.error)
                }
            }

            ScreenCard("ECU ACTIVITY") {
                TableRow {
                    Cell("ECU", 1.2f, ScreenLabel, mono = false, bold = true)
                    Cell("TX /s", 0.6f, ScreenLabel, mono = false, bold = true)
                    Cell("RX /s", 0.6f, ScreenLabel, mono = false, bold = true)
                    Cell("LAST SEEN", 0.9f, ScreenLabel, mono = false, bold = true)
                }
                ScreenDivider()
                if (health.perEcu.isEmpty()) {
                    Note("No frames in the window. Connect and start the ECUs to see traffic.")
                }
                health.perEcu.forEach { r ->
                    TableRow {
                        Cell(r.ecu, 1.2f, VecuColors.rx)
                        Cell("%.1f".format(r.txPerSec), 0.6f)
                        Cell("%.1f".format(r.rxPerSec), 0.6f)
                        Cell("${r.lastSeenMsAgo} ms ago", 0.9f)
                    }
                }
                // An event-shaped ECU is silent by design; say so rather than
                // leaving a reader to wonder why SWC never appears above.
                val eventOnly = ecus.filter { it.tx.isEmpty() && it.eventWidgets > 0 }
                if (eventOnly.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Note("${eventOnly.joinToString { it.name }}: event-shaped, transmits only on a gesture.")
                }
            }

            ScreenCard("CYCLIC TIMING") {
                TableRow {
                    Cell("ECU", 1.0f, ScreenLabel, mono = false, bold = true)
                    Cell("MESSAGE", 1.6f, ScreenLabel, mono = false, bold = true)
                    Cell("MODE", 0.9f, ScreenLabel, mono = false, bold = true)
                    Cell("EXPECTED", 0.8f, ScreenLabel, mono = false, bold = true)
                    Cell("MEASURED", 0.8f, ScreenLabel, mono = false, bold = true)
                    Cell("DRIFT", 0.7f, ScreenLabel, mono = false, bold = true)
                }
                ScreenDivider()
                if (health.cyclic.isEmpty()) {
                    Note("No cyclic messages declared.")
                }
                health.cyclic.forEach { r ->
                    TableRow {
                        Cell(r.ecu, 1.0f)
                        Cell(r.message, 1.6f)
                        Cell(if (r.onChange) "cyclic+chg" else "cyclic", 0.9f, ScreenLabel)
                        Cell("${r.expectedMs} ms", 0.8f)
                        Cell(r.measuredMs?.let { "%.0f ms".format(it) } ?: r.absence, 0.8f, driftColor(r))
                        Cell(r.driftPct?.let { "%+.0f%%".format(it) } ?: "—", 0.7f, driftColor(r))
                    }
                }
                if (health.cyclic.any { it.onChange }) {
                    Spacer(Modifier.height(8.dp))
                    Note(
                        "cyclic+chg also transmits on content change, so it legitimately beats " +
                            "its period. Measured shows its longest gap — the cyclic floor — not " +
                            "the mean, which those extra frames would drag down.",
                    )
                }
            }

            ScreenCard("UNDECODED IDS") {
                if (health.unknown.isEmpty()) {
                    Note("Every frame in the window was decoded by a profile's DBC.")
                } else {
                    TableRow {
                        Cell("ID", 0.8f, ScreenLabel, mono = false, bold = true)
                        Cell("FRAMES", 0.6f, ScreenLabel, mono = false, bold = true)
                        Cell("LAST SEEN", 0.9f, ScreenLabel, mono = false, bold = true)
                    }
                    ScreenDivider()
                    health.unknown.forEach { u ->
                        TableRow {
                            Cell(u.idHex, 0.8f, VecuColors.error)
                            Cell(u.count.toString(), 0.6f)
                            Cell("${u.lastSeenMsAgo} ms ago", 0.9f)
                        }
                    }
                }
            }
        }
    }
}

private fun driftColor(r: PeriodRow): Color = when {
    r.measuredMs == null -> if (r.samples == 0) VecuColors.error else VecuColors.warn
    r.driftPct == null -> ScreenValue
    kotlin.math.abs(r.driftPct) < 5 -> VecuColors.ok
    kotlin.math.abs(r.driftPct) < 15 -> VecuColors.warn
    else -> VecuColors.error
}

private class BusHealth(
    val total: Int,
    val rxPerSec: Double,
    val txPerSec: Double,
    val perEcu: List<EcuRate>,
    val cyclic: List<PeriodRow>,
    val unknown: List<UnknownRow>,
)

private class EcuRate(val ecu: String, val txPerSec: Double, val rxPerSec: Double, val lastSeenMsAgo: Long)
private class PeriodRow(
    val ecu: String,
    val message: String,
    val expectedMs: Long,
    /** null when fewer than two frames were seen: one frame is a sample, not
     *  an interval. */
    val measuredMs: Double?,
    val driftPct: Double?,
    val samples: Int,
    val onChange: Boolean,
) {
    /** What to print instead of a period, when there isn't one. */
    val absence: String get() = if (samples == 0) "silent" else "1 frame"
}

private class UnknownRow(val idHex: String, val count: Int, val lastSeenMsAgo: Long)

/** One pass over the window: rates, per-message periods and undecoded ids. */
private fun analyse(entries: List<CanLogEntry>, ecus: List<EcuInfo>): BusHealth {
    val now = System.currentTimeMillis()
    // Scan back far enough for the slowest declared message to show several
    // periods; rates still only count the last WINDOW_MS of it.
    val slowestMs = ecus.flatMap { it.tx }.mapNotNull { it.periodMs }.maxOrNull() ?: 0L
    val spanMs = maxOf(WINDOW_MS, slowestMs * PERIODS_NEEDED)
    val rateCutoff = now - WINDOW_MS
    val window = entries.asReversed().asSequence()
        .take(WINDOW_ROWS)
        .takeWhile { it.atMs >= now - spanMs }
        .toList()

    var rx = 0
    var tx = 0
    val txByEcu = HashMap<String, Int>()
    val rxByEcu = HashMap<String, Int>()
    val lastByEcu = HashMap<String, Long>()
    // Timestamps per transmitted message, for the measured period.
    val txTimes = HashMap<String, MutableList<Long>>()
    val unknownCount = HashMap<String, Int>()
    val unknownLast = HashMap<String, Long>()

    var inRateWindow = 0
    for (e in window) {
        val ecu = e.ecu ?: "(unknown)"
        val counts = e.atMs >= rateCutoff
        if (counts) inRateWindow++
        if (e.direction == Direction.TX) {
            // Periods are measured over the whole span; rates are not.
            txTimes.getOrPut("$ecu·${e.message}") { ArrayList() }.add(e.atMs)
            if (counts) {
                tx++
                txByEcu[ecu] = (txByEcu[ecu] ?: 0) + 1
            }
        } else if (counts) {
            rx++
            rxByEcu[ecu] = (rxByEcu[ecu] ?: 0) + 1
        }
        lastByEcu[ecu] = maxOf(lastByEcu[ecu] ?: 0L, e.atMs)
        if (e.unknown && counts) {
            unknownCount[e.idHex] = (unknownCount[e.idHex] ?: 0) + 1
            unknownLast[e.idHex] = maxOf(unknownLast[e.idHex] ?: 0L, e.atMs)
        }
    }

    val secs = WINDOW_MS / 1000.0
    val perEcu = lastByEcu.keys.sorted().map { name ->
        EcuRate(
            ecu = name,
            txPerSec = (txByEcu[name] ?: 0) / secs,
            rxPerSec = (rxByEcu[name] ?: 0) / secs,
            lastSeenMsAgo = now - (lastByEcu[name] ?: now),
        )
    }

    val cyclic = ecus.flatMap { ecu ->
        ecu.tx.filter { it.periodMs != null }.map { spec ->
            val expected = spec.periodMs!!
            // Each message gets a window sized to its own period, so a slow
            // message is judged over enough of its own cycles.
            val ownCutoff = now - maxOf(WINDOW_MS, expected * PERIODS_NEEDED)
            val stamps = txTimes["${ecu.name}·${spec.message}"].orEmpty()
                .filter { it >= ownCutoff }
                .sorted()
            val measured = when {
                stamps.size < 2 -> null
                // An on-change message sends extra frames between its cyclic
                // ones, so the mean interval measures the traffic, not the
                // period. The longest gap is the period showing through.
                spec.onChange -> stamps.zipWithNext { a, b -> (b - a).toDouble() }.max()
                else -> (stamps.last() - stamps.first()).toDouble() / (stamps.size - 1)
            }
            PeriodRow(
                ecu = ecu.name,
                message = spec.message,
                expectedMs = expected,
                measuredMs = measured,
                driftPct = measured?.let { (it - expected) / expected * 100.0 },
                samples = stamps.size,
                onChange = spec.onChange,
            )
        }
    }

    val unknown = unknownCount.entries
        .sortedByDescending { it.value }
        .map { (id, count) -> UnknownRow(id, count, now - (unknownLast[id] ?: now)) }

    return BusHealth(inRateWindow, rx / secs, tx / secs, perEcu, cyclic, unknown)
}
