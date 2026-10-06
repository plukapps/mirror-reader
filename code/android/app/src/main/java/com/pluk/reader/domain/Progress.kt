package com.pluk.reader.domain

import kotlin.math.roundToInt

/** Porcentaje 0..100 del libro leído, o null si todavía no hay dato (RDR-005). */
fun progressPercent(totalProgression: Double?): Int? =
    totalProgression?.let { (it * 100).roundToInt().coerceIn(0, 100) }
