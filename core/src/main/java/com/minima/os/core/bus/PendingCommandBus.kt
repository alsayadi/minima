package com.minima.os.core.bus

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-lifetime bus for handing a command from a one-shot Activity
 * (e.g. ShareReceiverActivity) to whichever ViewModel owns the command bar.
 *
 * Why a bus and not just an intent extra on LauncherActivity? Because the
 * ViewModel may not exist yet when the intent is delivered (cold process
 * start after a share). `replay = 1` keeps the last emission around long
 * enough for the ViewModel's init block to collect it.
 *
 * After the consumer handles the command it should call [consumed] so a
 * later configuration change doesn't re-submit the same command.
 */
object PendingCommandBus {
    val flow = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 4,
    )

    /**
     * Non-suspending emit. Safe to call from Activity lifecycle callbacks.
     * Returns true if the value was buffered.
     */
    fun post(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return false
        return flow.tryEmit(trimmed)
    }

    /** Clears the replay cache so this command won't be replayed on reattach. */
    fun consumed() {
        flow.resetReplayCache()
    }
}
