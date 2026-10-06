package com.pluk.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pluk.reader.ui.home.HomeScreen
import com.pluk.reader.ui.reader.ReaderScreen
import com.pluk.reader.ui.reader.ReaderViewModel

object Routes {
    const val HOME = "home"
    const val READER = "reader/{${ReaderViewModel.ARG_URI}}"

    fun reader(bookUri: Uri): String = "reader/${Uri.encode(bookUri.toString())}"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onBookPicked = { uri -> navController.navigate(Routes.reader(uri)) })
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(ReaderViewModel.ARG_URI) { type = NavType.StringType }),
        ) {
            ReaderScreen(onBack = { navController.popBackStack() })
        }
    }
}
