package com.pluk.reader.data.store

import com.pluk.reader.domain.store.CatalogResult
import com.pluk.reader.domain.store.CatalogWork
import com.pluk.reader.domain.store.StoreCatalog
import com.pluk.reader.domain.store.toStoreBook
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Catálogo de la tienda sobre Open Library (prueba de concepto, plan `2026-10-10-android-store-poc.md`). */
class OpenLibraryCatalog @Inject constructor() : StoreCatalog {
    override suspend fun search(query: String, byAuthor: Boolean): CatalogResult = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(openLibrarySearchUrl(query, byAuthor))
            CatalogResult.Found(parseOpenLibrarySearch(body).map { it.toStoreBook() })
        } catch (_: IOException) {
            CatalogResult.Unavailable
        } catch (_: JSONException) {
            CatalogResult.Unavailable
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Open Library pide identificar la app en el `User-Agent`. */
internal const val USER_AGENT = "PlukReader/0.1 (Android; +https://github.com/plukapps)"

private const val FIELDS = "key,title,author_name,cover_i,ebook_access,id_standard_ebooks,id_project_gutenberg"

/** Búsqueda por título y autor (`q`) o solo por autor (`author`), STO-001 y STO-006. */
fun openLibrarySearchUrl(query: String, byAuthor: Boolean): String {
    val param = if (byAuthor) "author" else "q"
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    return "https://openlibrary.org/search.json?$param=$encoded&fields=$FIELDS&limit=20"
}

/**
 * Obras de la respuesta de `search.json`. Las que no tienen título se descartan. Los ids de
 * Standard Ebooks y Gutenberg solo se toman si la obra es de dominio público (`ebook_access` =
 * `public`): una de préstamo lleva DRM (STO-005).
 */
fun parseOpenLibrarySearch(json: String): List<CatalogWork> {
    val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
    return (0 until docs.length()).mapNotNull { index ->
        val doc = docs.optJSONObject(index) ?: return@mapNotNull null
        val key = doc.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = doc.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val public = doc.optString("ebook_access") == "public"
        CatalogWork(
            key = key,
            title = title,
            authors = doc.optJSONArray("author_name").strings(),
            coverId = doc.optLong("cover_i", 0L).takeIf { it > 0 },
            standardEbooksIds = if (public) doc.optJSONArray("id_standard_ebooks").strings() else emptyList(),
            gutenbergIds = if (public) doc.optJSONArray("id_project_gutenberg").strings() else emptyList(),
        )
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
