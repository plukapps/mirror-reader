package com.pluk.reader.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.pluk.reader.R
import com.pluk.reader.ui.home.HomeScreen
import com.pluk.reader.ui.library.LibraryScreen
import com.pluk.reader.ui.reader.ReaderScreen
import com.pluk.reader.ui.reader.ReaderViewModel

object Routes {
    /** Inicio es la pantalla de arranque (HOM-005). */
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READER = "reader/{${ReaderViewModel.ARG_BOOK_ID}}"

    fun reader(bookId: String): String = "reader/$bookId"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val route by navController.currentBackStackEntryAsState()
    val selected = when (route?.destination?.route) {
        Routes.HOME -> MainDestination.Home
        Routes.LIBRARY -> MainDestination.Shelves
        else -> null // La barra se oculta en el lector.
    }
    Column(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onBookClick = { navController.navigate(Routes.reader(it)) },
                    onOpenLibrary = { navController.navigateTo(Routes.LIBRARY) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onBookClick = { bookId -> navController.navigate(Routes.reader(bookId)) })
            }
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument(ReaderViewModel.ARG_BOOK_ID) { type = NavType.StringType }),
            ) {
                ReaderScreen(onBack = { navController.popBackStack() })
            }
        }
        if (selected != null) {
            MarginBottomBar(selected) { destination ->
                when (destination) {
                    MainDestination.Home -> navController.navigateTo(Routes.HOME)
                    MainDestination.Shelves -> navController.navigateTo(Routes.LIBRARY)
                    // TODO: Buscar (LIB-006) y Perfil (ACC) aún no existen (HOM-006).
                    MainDestination.Search, MainDestination.Profile ->
                        Toast.makeText(context, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** Cambia de destino de la barra sin apilar copias: Inicio queda como base. */
private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
        launchSingleTop = true
    }
}
