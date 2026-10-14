package com.pluk.reader.domain.profile

import com.pluk.reader.domain.account.StorageQuota
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Unidad en la que se muestran los tamaños del espacio (PRF-006). */
enum class StorageUnit(val bytes: Long, val label: String) {
    MB(1024L * 1024, "MB"),
    GB(1024L * 1024 * 1024, "GB"),
}

/** PRF-006: los tamaños usan la unidad del total; GB si el total es de 1 GB o más. */
fun storageUnitFor(totalBytes: Long): StorageUnit = if (totalBytes >= StorageUnit.GB.bytes) StorageUnit.GB else StorageUnit.MB

/** "1,2 GB", "2 GB", "3,4 MB": un decimal como máximo y coma decimal, sin depender del idioma del sistema. */
fun formatStorage(bytes: Long, unit: StorageUnit): String {
    val tenths = (bytes.coerceAtLeast(0) * 10.0 / unit.bytes).roundToLong()
    val whole = tenths / 10
    val decimal = tenths % 10
    val number = if (decimal == 0L) "$whole" else "$whole,$decimal"
    return "$number ${unit.label}"
}

/** Uso del espacio listo para mostrar (PRF-006, PLN-003): tamaños en la unidad del total y proporción usada. */
data class StorageUsage(
    val used: String,
    val total: String,
    val free: String,
    /** Proporción usada entre 0 y 1. */
    val fraction: Float,
    /** La misma proporción en por ciento, de 0 a 100. */
    val percent: Int,
)

fun storageUsage(quota: StorageQuota): StorageUsage {
    val unit = storageUnitFor(quota.quotaBytes)
    val fraction = if (quota.quotaBytes <= 0) 0f else (quota.usedBytes.toFloat() / quota.quotaBytes).coerceIn(0f, 1f)
    return StorageUsage(
        used = formatStorage(quota.usedBytes, unit),
        total = formatStorage(quota.quotaBytes, unit),
        free = formatStorage(quota.freeBytes, unit),
        fraction = fraction,
        percent = (fraction * 100).roundToInt(),
    )
}
