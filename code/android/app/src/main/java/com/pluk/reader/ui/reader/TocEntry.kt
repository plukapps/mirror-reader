package com.pluk.reader.ui.reader

import org.readium.r2.shared.publication.Link

data class TocEntry(val title: String, val link: Link, val depth: Int)

/** Aplana la tabla de contenidos conservando el nivel de cada entrada (RDR-004). */
fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> =
    links.flatMap { link ->
        listOf(TocEntry(link.title ?: link.href.toString(), link, depth)) +
            flattenToc(link.children, depth + 1)
    }
