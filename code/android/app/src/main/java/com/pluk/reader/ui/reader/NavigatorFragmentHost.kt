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
    @Volatile
    private var factory: FragmentFactory? = null

    /** Hay un libro abierto cuyo fragmento Android puede restaurar. */
    val isInstalled: Boolean get() = factory != null

    /** Fábrica estable para el `FragmentManager`: delega en la del libro actual. */
    val fragmentFactory: FragmentFactory = object : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            val delegate = factory
            return if (delegate != null && className == EpubNavigatorFragment::class.java.name) {
                delegate.instantiate(classLoader, className)
            } else {
                super.instantiate(classLoader, className)
            }
        }
    }

    override fun install(book: OpenedBook, settings: ReaderSettings, onExternalLink: (String) -> Unit) {
        // Una posición guardada dañada no debe impedir abrir el libro (RDR-006).
        val initialLocator = book.resumeLocatorJson
            ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        factory = EpubNavigatorFactory(book.publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = settings.toEpubPreferences(),
            // Los insets de sistema (barra de estado) los reserva ReaderScreen, no Readium.
            configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) = onExternalLink(url.toString())
            },
        )
    }

    override fun clear() {
        factory = null
    }
}
