package com.pluk.reader.domain

import java.text.Normalizer

/** Entrada de primer nivel de la tabla de contenidos: título y recurso al que apunta (puede traer fragmento). */
data class BodyEntry(val title: String, val href: String)

// Palabras que, en una entrada final de la tabla de contenidos, marcan páginas que no son parte de la lectura.
// Se busca la palabra dentro del título (ya normalizado), no el título exacto: "Illustration Credits", "About the Authors".
private val backMatterWords = Regex(
    "\\b(indice|index|notas?|notes?|bibliografia|bibliography|glosario|glossary|agradecimientos?|acknowledg\\w*|" +
        "creditos?|credits?|colofon|colophon|copyright|laminas|sobre (el|la|los|las) autor\\w*|acerca (del|de la|de los) autor\\w*|" +
        "about the authors?|proxima lectura|next read|also by|otros libros)\\b",
)

private fun normalizedTitle(title: String): String =
    Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Índice, dentro de [readingOrder], del primer recurso de las páginas finales del libro (RDR-012), o null.
 *
 * Son páginas finales las entradas de primer nivel al final de [toc] cuyo título contiene palabras como "notas", "índice" o "créditos".
 * Si todo el contenido es de ese tipo, o el recurso no está en el orden de lectura, no hay fin del cuerpo.
 */
fun backMatterStart(toc: List<BodyEntry>, readingOrder: List<String>): Int? {
    val trailing = toc.takeLastWhile { backMatterWords.containsMatchIn(normalizedTitle(it.title)) }
    if (trailing.isEmpty() || trailing.size == toc.size) return null
    val index = readingOrder.indexOf(trailing.first().href.substringBefore('#'))
    return index.takeIf { it > 0 }
}

/**
 * Decide cuándo avisar que el cuerpo del libro terminó (RDR-012).
 *
 * Avisa una vez, al pasar de un recurso del cuerpo a uno de las páginas finales. Si la primera posición
 * ya está dentro de las páginas finales (libro reabierto ahí), no avisa.
 */
class BodyEndDetector(private val readingOrder: List<String>, private val backMatterStart: Int?) {
    private var seenBody = false
    private var notified = false

    /** True si esta posición es el momento de avisar. */
    fun onResource(href: String): Boolean {
        val start = backMatterStart ?: return false
        val index = readingOrder.indexOf(href.substringBefore('#'))
        if (index < 0) return false
        if (index < start) {
            seenBody = true
            return false
        }
        if (!seenBody || notified) return false
        notified = true
        return true
    }
}
