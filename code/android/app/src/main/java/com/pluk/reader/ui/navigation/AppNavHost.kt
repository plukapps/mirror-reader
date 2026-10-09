package com.pluk.reader.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.pluk.reader.R
import com.pluk.reader.ui.home.HomeScreen
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.ui.library.LibraryScreen
import com.pluk.reader.ui.library.LibraryViewModel
import com.pluk.reader.ui.reader.ReaderScreen
import com.pluk.reader.ui.reader.ReaderViewModel
import com.pluk.reader.ui.search.SearchScreen
import com.pluk.reader.ui.welcome.WelcomeScreen

object Routes {
    /** Inicio es la pantalla de arranque (HOM-005), salvo que corresponda la bienvenida (WEL-003). */
    const val HOME = "home"
    const val WELCOME = "welcome"
    const val LIBRARY = "library?${LibraryViewModel.ARG_FILTER}={${LibraryViewModel.ARG_FILTER}}"
    const val READER = "reader/{${ReaderViewModel.ARG_BOOK_ID}}"
    const val SEARCH = "search"

    fun reader(bookId: String): String = "reader/$bookId"

    /** Biblioteca con el filtro dado (HOM-011), o en "Todos" sin filtro. */
    fun library(filter: LibraryFilter? = null): String =
        if (filter == null) "library" else "library?${LibraryViewModel.ARG_FILTER}=${filter.name}"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    showWelcome: Boolean,
    onWelcomeCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val route by navController.currentBackStackEntryAsState()
    val selected = when (route?.destination?.route) {
        Routes.HOME -> MainDestination.Home
        Routes.LIBRARY -> MainDestination.Shelves
        Routes.SEARCH -> MainDestination.Search
        else -> null // La barra se oculta en el lector.
    }
    // La barra flota sobre el contenido, que pasa por debajo; las pantallas dejan este espacio al final.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (selected != null) BottomBarHeight + navBarInset else 0.dp
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarPadding provides bottomPadding) {
            NavHost(navController = navController, startDestination = if (showWelcome) Routes.WELCOME else Routes.HOME, modifier = Modifier.fillMaxSize()) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onStart = {
                            onWelcomeCompleted()
                            // WEL-005: atrás desde Inicio sale de la app, no vuelve a la bienvenida.
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onBookClick = { navController.navigate(Routes.reader(it)) },
                        onSeeAll = { filter -> navController.navigateTo(Routes.library(filter)) },
                    )
                }
                composable(
                    route = Routes.LIBRARY,
                    arguments = listOf(
                        navArgument(LibraryViewModel.ARG_FILTER) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    LibraryScreen(onBookClick = { bookId -> navController.navigate(Routes.reader(bookId)) })
                }
                composable(Routes.SEARCH) {
                    SearchScreen(onBookClick = { bookId -> navController.navigate(Routes.reader(bookId)) })
                }
                composable(
                    route = Routes.READER,
                    arguments = listOf(navArgument(ReaderViewModel.ARG_BOOK_ID) { type = NavType.StringType }),
                ) {
                    ReaderScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        if (selected != null) {
            MarginBottomBar(selected, Modifier.align(Alignment.BottomCenter)) { destination ->
                when (destination) {
                    MainDestination.Home -> navController.navigateTo(Routes.HOME)
                    MainDestination.Shelves -> navController.navigateTo(Routes.library())
                    MainDestination.Search -> navController.navigateTo(Routes.SEARCH)
                    // TODO: Perfil (ACC) aún no existe (HOM-006).
                    MainDestination.Profile ->
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
