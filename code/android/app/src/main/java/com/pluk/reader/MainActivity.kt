package com.pluk.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.pluk.reader.di.MainEntryPoint
import com.pluk.reader.ui.MainViewModel
import com.pluk.reader.ui.navigation.AppNavHost
import com.pluk.reader.ui.navigation.Routes
import com.pluk.reader.ui.theme.ReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

// FragmentActivity: el navegador de Readium es un Fragment (ADR 0005).
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // EPUB recibido con "Abrir con" y todavía sin mostrar.
    private val incomingBook = mutableStateOf<Uri?>(null)
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android debe poder recrear el fragmento del navegador, y eso exige instalar la fábrica
        // antes de super.onCreate. Si el proceso se perdió no hay libro abierto que restaurar:
        // se descarta el estado guardado y se vuelve al inicio (la posición ya está en Room).
        val host = EntryPointAccessors.fromApplication(applicationContext, MainEntryPoint::class.java)
            .navigatorHost()
        supportFragmentManager.fragmentFactory = host.fragmentFactory
        super.onCreate(if (savedInstanceState != null && !host.isInstalled) null else savedInstanceState)
        enableEdgeToEdge()
        viewModel.startLibrarySync()

        if (savedInstanceState == null) incomingBook.value = intent?.data
        setContent {
            ReaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    LaunchedEffect(incomingBook.value) {
                        incomingBook.value?.let { uri ->
                            incomingBook.value = null
                            when (val incoming = viewModel.importIncoming(uri.toString())) {
                                is MainViewModel.Incoming.Open -> navController.navigate(Routes.reader(incoming.bookId))
                                is MainViewModel.Incoming.Failed ->
                                    Toast.makeText(this@MainActivity, incoming.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    AppNavHost(navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { incomingBook.value = it }
    }
}
