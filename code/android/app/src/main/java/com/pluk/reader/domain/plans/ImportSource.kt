package com.pluk.reader.domain.plans

/** Orígenes de la hoja "Importar libros" (PLN-010), en el orden del diseño. */
enum class ImportSource(
    /** Función paga que anuncia, o null si importa de verdad. */
    val paidFeature: PaidFeature?,
) {
    /** Desde este dispositivo: el selector de archivos de hoy (LIB-001). */
    DEVICE(null),
    DRIVE(PaidFeature.DRIVE),
    CALIBRE(PaidFeature.CALIBRE),
}

/**
 * PLN-010: elegir un origen. Devuelve true si hay que abrir el selector de archivos; los orígenes pagos muestran su
 * hoja de función paga.
 */
fun PlanPrompts.onImportSource(source: ImportSource): Boolean {
    val feature = source.paidFeature ?: return true
    show(PlanPrompt.Paid(feature))
    return false
}
