package com.pluk.reader.domain.onboarding

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Próximo momento (en milisegundos desde 1970) en que toca el recordatorio de las [hour]:[minute] en [zone],
 * estrictamente después de [nowMillis] (ONB-012).
 */
fun nextReminderAt(nowMillis: Long, zone: ZoneId, hour: Int, minute: Int): Long {
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
    var next = now.with(LocalTime.of(hour, minute))
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}
