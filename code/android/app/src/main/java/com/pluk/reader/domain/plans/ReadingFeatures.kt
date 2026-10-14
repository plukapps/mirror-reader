package com.pluk.reader.domain.plans

import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme

/** PLN-015: temas y fuentes son de Lector y Biblioteca. */
val PlanId.hasThemesAndFonts: Boolean get() = this != PlanId.FREE

/** PLN-015: Sepia y Noche son de un plan pago; Clásico es de todos. */
val ReadingTheme.isPaid: Boolean get() = this != ReadingTheme.LIGHT

/** PLN-015: sin serifa y monoespaciada son de un plan pago; la letra con serifa es de todos. */
val ReaderFont.isPaid: Boolean get() = this != ReaderFont.SERIF

/**
 * PLN-015: ajustes con los que se lee. En Gratis, Clásico y serifa; tamaño, interlineado y animación quedan como
 * están. Lo guardado no se toca: vuelve si la cuenta pasa a un plan pago.
 */
fun effectiveReaderSettings(saved: ReaderSettings, plan: PlanId): ReaderSettings =
    if (plan.hasThemesAndFonts) saved else saved.copy(theme = ReadingTheme.LIGHT, font = ReaderFont.SERIF)

/**
 * PLN-015: elegir una opción de lectura. Si el plan no la incluye, muestra la hoja de función paga y devuelve false;
 * si la incluye, devuelve true y quien llama la guarda.
 */
fun PlanPrompts.allowReadingOption(plan: PlanId, paidOption: Boolean): Boolean {
    if (!paidOption || plan.hasThemesAndFonts) return true
    show(PlanPrompt.Paid(PaidFeature.THEMES_FONTS))
    return false
}
