package com.pluk.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.pluk.reader.reader.ReaderActivity
import com.pluk.reader.ui.navigation.AppNavHost
import com.pluk.reader.ui.theme.ReaderTheme
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity: el navegador de Readium es un Fragment (ADR 0005).
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost(onOpenBook = ::openBook)
                }
            }
        }
    }

    // Provisional: la ruta "reader" de Compose reemplaza a ReaderActivity en la Tarea 3 del plan.
    private fun openBook(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Algunos proveedores no permiten permiso persistente. Se abre igual esta vez.
        }
        startActivity(
            Intent(this, ReaderActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
