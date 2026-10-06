package com.pluk.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pluk.reader.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
}

@Composable
fun AppNavHost(onOpenBook: (Uri) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onBookPicked = onOpenBook) }
    }
}
