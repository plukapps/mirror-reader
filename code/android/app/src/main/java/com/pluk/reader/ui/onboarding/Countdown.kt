package com.pluk.reader.ui.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuenta regresiva de reenvío (ONB-007, ONB-017): avisa [seconds], [seconds]-1, ..., 0, una vez por segundo. */
internal fun CoroutineScope.countdown(seconds: Int, onTick: (Int) -> Unit): Job = launch {
    for (left in seconds downTo 0) {
        onTick(left)
        if (left > 0) delay(1_000)
    }
}
