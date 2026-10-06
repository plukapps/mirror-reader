package com.pluk.reader.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pluk.reader.R
import com.pluk.reader.theme.ReaderTheme
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity(), EpubNavigatorFragment.Listener {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // El factory debe estar instalado antes de super.onCreate para que Android
        // pueda restaurar el fragmento tras una recreación. Sin sesión no hay nada que restaurar.
        // Se registra antes de super.onCreate para cubrir también el fragmento restaurado.
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is EpubNavigatorFragment) setUpNavigator(fragment)
        }
        val existing = viewModel.session
        existing?.let(::installFragmentFactory)
        super.onCreate(if (existing != null) savedInstanceState else null)
        setContentView(R.layout.activity_reader)

        intent.data?.let(viewModel::open)

        findViewById<ComposeView>(R.id.controls).setContent {
            val settings by viewModel.settings.collectAsState()
            val visible by viewModel.controlsVisible.collectAsState()
            ReaderTheme {
                ReaderControls(
                    visible = visible,
                    settings = settings,
                    onToggleScroll = viewModel::toggleScroll,
                    onNextTheme = viewModel::nextTheme,
                    onSmallerFont = viewModel::smallerFont,
                    onBiggerFont = viewModel::biggerFont,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { navigator()?.submitPreferences(it.toEpubPreferences()) }
            }
        }
    }

    private fun navigator(): EpubNavigatorFragment? =
        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment

    private fun setUpNavigator(navigator: EpubNavigatorFragment) {
        // Toques en los bordes pasan de página. Un toque en el centro muestra u oculta los controles.
        navigator.addInputListener(DirectionalNavigationAdapter(navigator))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                viewModel.toggleControls()
                return true
            }
        })
    }

    private fun installFragmentFactory(session: ReaderSession) {
        supportFragmentManager.fragmentFactory = session.factory.createFragmentFactory(
            initialLocator = session.initialLocator,
            initialPreferences = viewModel.settings.value.toEpubPreferences(),
            listener = this,
        )
    }

    private fun render(state: ReaderState) {
        findViewById<ProgressBar>(R.id.loading).isVisible = state is ReaderState.Loading
        findViewById<TextView>(R.id.error).apply {
            isVisible = state is ReaderState.Failed
            if (state is ReaderState.Failed) text = state.message
        }
        if (state is ReaderState.Ready &&
            supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) == null
        ) {
            installFragmentFactory(state.session)
            supportFragmentManager.beginTransaction()
                .replace(R.id.reader_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                .commitNow()
        }
    }

    // Enlaces externos dentro del libro: solo web, en el navegador del sistema.
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        val uri = url.toUri()
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // Sin navegador disponible: no se hace nada.
        }
    }

    companion object {
        const val NAVIGATOR_TAG = "navigator"
    }
}
