package com.pluk.reader.domain.profile

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
