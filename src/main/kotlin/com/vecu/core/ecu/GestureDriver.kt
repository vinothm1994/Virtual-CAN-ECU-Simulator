package com.vecu.core.ecu

import com.vecu.core.config.GestureSpec
import com.vecu.core.property.GesturePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Turns "the user is holding this key down" into the four-step gesture an
 * event-shaped ECU puts on the bus:
 *
 *     PRESSED -> (long_press_ms) LONG_PRESSED -> (repeat_ms) REPEAT ... -> RELEASED
 *
 * The point of emitting LONG_PRESSED as its own step is that the consumer never
 * has to infer a long press from arrival times — and so a long press does not
 * also run the short-click action. A consumer binds the short action to
 * RELEASED with no LONG_PRESSED seen since that key's PRESSED; the sequence
 * carries the distinction, and nothing has to be timed on the receiving end.
 *
 * It knows nothing about buttons, CAN or SWC: it drives phases for an opaque
 * key id and hands each one to the caller. Timing runs on [scope] (the
 * ViewModel's own `Dispatchers.Default`), never the Compose UI scope — a
 * minimized window must not stall auto-repeat, the same rule the TX scheduler
 * follows.
 */
class GestureDriver(
    private val scope: CoroutineScope,
    private val spec: GestureSpec,
) {
    private val lock = Any()
    private val held = HashMap<String, Held>()

    private class Held(val job: Job, val emit: (GesturePhase) -> Unit)

    /**
     * The key went down. Emits PRESSED synchronously — an event must be sent
     * when it happens, not noticed on a later tick — then schedules the
     * long-press and auto-repeat steps.
     *
     * A second press with no release in between is ignored: real hardware
     * cannot produce one, and honouring it would leak the timer job.
     */
    fun press(id: String, emit: (GesturePhase) -> Unit) {
        synchronized(lock) {
            if (held.containsKey(id)) return
            emit(GesturePhase.PRESSED)
            val job = scope.launch(Dispatchers.Default) {
                delay(spec.longPressMs)
                emit(GesturePhase.LONG_PRESSED)
                while (isActive) {
                    delay(spec.repeatMs)
                    emit(GesturePhase.REPEAT)
                }
            }
            held[id] = Held(job, emit)
        }
    }

    /**
     * The key came up. Cancels any pending long-press/repeat and emits
     * RELEASED — always, and always last: a consumer that never sees it holds
     * the key down forever.
     */
    fun release(id: String) {
        val h = synchronized(lock) { held.remove(id) } ?: return
        h.job.cancel()
        h.emit(GesturePhase.RELEASED)
    }

    /** Release every held key. For teardown and for switching bus/profile, so a
     *  key cannot stay logically down across the change. */
    fun releaseAll() {
        val ids = synchronized(lock) { held.keys.toList() }
        ids.forEach { release(it) }
    }

    /** Keys currently held. For tests and diagnostics. */
    fun heldCount(): Int = synchronized(lock) { held.size }
}
