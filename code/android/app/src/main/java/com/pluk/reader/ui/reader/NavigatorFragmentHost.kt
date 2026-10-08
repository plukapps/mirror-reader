package com.pluk.reader.ui.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entrega a Android la fábrica con la que se instancia el `EpubNavigatorFragment`.
 *
 * Android debe poder recrear el fragmento al restaurar la actividad (rotación), y Readium no permite
 * un constructor vacío. Por eso la fábrica vive en un singleton que `MainActivity` instala
 * en su `FragmentManager` antes de `super.onCreate` (ADR 0005).
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class NavigatorFragmentHost @Inject constructor() : NavigatorHost {
    private class Installed(
        val book: OpenedBook,
        val settings: ReaderSettings,
        val onExternalLink: (String) -> Unit,
    )

    @Volatile
    private var installed: Installed? = null

    /** Última posición conocida (RDR-016): al recrearse el navegador (girar) se vuelve ahí, no a la de apertura. */
    @Volatile
    private var latestLocatorJson: String? = null

    /** Hay un libro abierto cuyo fragmento Android puede restaurar. */
    val isInstalled: Boolean get() = installed != null

    /** Fábrica estable para el `FragmentManager`: delega en la del libro actual. */
    val fragmentFactory: FragmentFactory = object : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            val delegate = createFactory()
            return if (delegate != null && className == EpubNavigatorFragment::class.java.name) {
                delegate.instantiate(classLoader, className)
            } else {
                super.instantiate(classLoader, className)
            }
        }
    }

    override fun install(book: OpenedBook, settings: ReaderSettings, onExternalLink: (String) -> Unit) {
        latestLocatorJson = book.resumeLocatorJson
        installed = Installed(book, settings, onExternalLink)
    }

    override fun onLocatorChanged(locatorJson: String) {
        latestLocatorJson = locatorJson
    }

    override fun clear() {
        installed = null
        latestLocatorJson = null
    }

    /** Se arma en cada instanciación del fragmento, para que use la posición de ese momento. */
    private fun createFactory(): FragmentFactory? {
        val current = installed ?: return null
        // Una posición guardada dañada no debe impedir abrir el libro (RDR-006).
        val initialLocator = latestLocatorJson
            ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        return EpubNavigatorFactory(current.book.publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = current.settings.toEpubPreferences(),
            configuration = readerNavigatorConfiguration(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) = current.onExternalLink(url.toString())
            },
        )
    }
}
