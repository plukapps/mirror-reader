package com.pluk.reader.data.epub

import android.content.Context
import android.net.Uri
import com.pluk.reader.domain.model.BookOpenException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@Singleton
class PublicationLoader @Inject constructor(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    private val opener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            appContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    suspend fun load(uri: Uri): Result<Publication> {
        val url = uri.toAbsoluteUrl()
            ?: return Result.failure(BookOpenException("No se pudo acceder al archivo."))
        val asset = assetRetriever.retrieve(url).getOrElse {
            return Result.failure(BookOpenException("No se pudo leer el archivo.", Exception(it.message)))
        }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            return Result.failure(
                BookOpenException(
                    "No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM.",
                    Exception(it.message),
                ),
            )
        }
        return Result.success(publication)
    }
}
