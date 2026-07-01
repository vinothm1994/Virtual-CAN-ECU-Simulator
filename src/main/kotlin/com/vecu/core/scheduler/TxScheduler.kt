package com.vecu.core.scheduler

import com.vecu.core.config.TxSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodic transmitter. Each configured status message gets its own coroutine
 * ticking at its period; [onTx] is invoked with the message name each time and
 * is responsible for encoding + sending.
 */
class TxScheduler(
    private val scope: CoroutineScope,
    private val specs: List<TxSpec>,
    private val onTx: (message: String) -> Unit,
) {
    private val jobs = mutableListOf<Job>()

    val isRunning: Boolean @Synchronized get() = jobs.isNotEmpty()

    @Synchronized
    fun start() {
        if (jobs.isNotEmpty()) return
        // Only cyclic messages get a timer here; on-change messages are driven
        // by the ECU state changing (see SimulatorViewModel.evaluateOnChange).
        for (spec in specs) {
            val period = spec.periodMs ?: continue
            jobs += scope.launch {
                while (isActive) {
                    onTx(spec.message)
                    delay(period)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }
}
