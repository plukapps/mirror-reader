package com.pluk.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pluk.reader.ui.library.LibraryScreen
import com.pluk.reader.ui.reader.ReaderScreen
import com.pluk.reader.ui.reader.ReaderViewModel

object Routes {
    /** La biblioteca es la pantalla de inicio. */
    const val LIBRARY = "library"
    const val READER = "reader/{${ReaderViewModel.ARG_BOOK_ID}}"

    fun reader(bookId: String): String = "reader/$bookId"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
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
}
