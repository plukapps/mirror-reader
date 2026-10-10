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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.pluk.reader.ui.onboarding.AllSetScreen
import com.pluk.reader.ui.onboarding.ForgotPasswordScreen
import com.pluk.reader.ui.onboarding.ForgotPasswordViewModel
import com.pluk.reader.ui.onboarding.GoalScreen
import com.pluk.reader.ui.onboarding.InterestsScreen
import com.pluk.reader.ui.onboarding.ResetPasswordScreen
import com.pluk.reader.ui.onboarding.ResetPasswordViewModel
import com.pluk.reader.ui.onboarding.SignInScreen
import com.pluk.reader.ui.onboarding.SignUpScreen
import com.pluk.reader.ui.onboarding.SignUpViewModel
import com.pluk.reader.ui.onboarding.VerifyEmailScreen
import com.pluk.reader.ui.onboarding.VerifyEmailViewModel
import com.pluk.reader.ui.search.SearchScreen
import com.pluk.reader.ui.welcome.WelcomeScreen
import android.net.Uri

object Routes {
    /** Inicio es la pantalla de arranque (HOM-005), salvo que corresponda el onboarding (ONB-019). */
    const val HOME = "home"
    const val WELCOME = "welcome"

    // Onboarding (ONB): alta, verificación, intereses, meta, listo; ingreso, olvido y contraseña nueva.
    const val SIGNUP = "signup?${SignUpViewModel.ARG_NAME}={${SignUpViewModel.ARG_NAME}}&${SignUpViewModel.ARG_EMAIL}={${SignUpViewModel.ARG_EMAIL}}"
    const val VERIFY = "verify?${VerifyEmailViewModel.ARG_JUST_SENT}={${VerifyEmailViewModel.ARG_JUST_SENT}}"
    const val INTERESTS = "interests"
    const val GOAL = "goal"
    const val ALL_SET = "allset"
    const val SIGNIN = "signin"
    const val FORGOT = "forgot?${ForgotPasswordViewModel.ARG_EMAIL}={${ForgotPasswordViewModel.ARG_EMAIL}}"
    const val RESET = "reset?${ResetPasswordViewModel.ARG_CODE}={${ResetPasswordViewModel.ARG_CODE}}"
    const val LIBRARY = "library?${LibraryViewModel.ARG_FILTER}={${LibraryViewModel.ARG_FILTER}}"
    const val READER = "reader/{${ReaderViewModel.ARG_BOOK_ID}}"
    const val SEARCH = "search"

    fun reader(bookId: String): String = "reader/$bookId"

    fun signUp(name: String = "", email: String = ""): String =
        "signup?${SignUpViewModel.ARG_NAME}=${Uri.encode(name)}&${SignUpViewModel.ARG_EMAIL}=${Uri.encode(email)}"

    fun verify(justSent: Boolean = false): String = "verify?${VerifyEmailViewModel.ARG_JUST_SENT}=$justSent"

    fun forgot(email: String): String = "forgot?${ForgotPasswordViewModel.ARG_EMAIL}=${Uri.encode(email)}"

    fun reset(code: String): String = "reset?${ResetPasswordViewModel.ARG_CODE}=${Uri.encode(code)}"

    /** Biblioteca con el filtro dado (HOM-011), o en "Todos" sin filtro. */
    fun library(filter: LibraryFilter? = null): String =
        if (filter == null) "library" else "library?${LibraryViewModel.ARG_FILTER}=${filter.name}"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    val context = LocalContext.current
    // ONB-013: "Importar mis libros" abre el selector apenas aparece Inicio.
    var pendingImport by rememberSaveable { mutableStateOf(false) }
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
            NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onCreateAccount = { navController.navigate(Routes.signUp()) },
                        onSignIn = { navController.navigate(Routes.SIGNIN) },
                    )
                }
                composable(
                    Routes.SIGNUP,
                    arguments = listOf(
                        navArgument(SignUpViewModel.ARG_NAME) { type = NavType.StringType; defaultValue = "" },
                        navArgument(SignUpViewModel.ARG_EMAIL) { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    SignUpScreen(
                        onBack = { navController.popOrStartOver() },
                        // Con la cuenta creada, crear cuenta ya no está detrás.
                        onVerifyEmail = { navController.navigate(Routes.verify(justSent = true)) { popUpTo(Routes.SIGNUP) { inclusive = true } } },
                        onInterests = { navController.startOver(Routes.INTERESTS) },
                        onSignIn = { navController.navigate(Routes.SIGNIN) { popUpTo(Routes.SIGNUP) { inclusive = true } } },
                    )
                }
                composable(
                    Routes.VERIFY,
                    arguments = listOf(navArgument(VerifyEmailViewModel.ARG_JUST_SENT) { type = NavType.BoolType; defaultValue = false }),
                ) {
                    VerifyEmailScreen(
                        onVerified = { navController.startOver(Routes.INTERESTS) },
                        // ONB-008: de vuelta a crear cuenta, con la bienvenida detrás.
                        onChange = { name, email ->
                            navController.startOver(Routes.WELCOME)
                            navController.navigate(Routes.signUp(name, email))
                        },
                    )
                }
                composable(Routes.INTERESTS) {
                    InterestsScreen(
                        onBack = null,
                        onDone = { navController.navigate(Routes.GOAL) },
                    )
                }
                composable(Routes.GOAL) {
                    GoalScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.startOver(Routes.ALL_SET) },
                    )
                }
                composable(Routes.ALL_SET) {
                    AllSetScreen(
                        onStartReading = { navController.startOver(Routes.HOME) },
                        onImportBooks = {
                            pendingImport = true
                            navController.startOver(Routes.HOME)
                        },
                    )
                }
                composable(Routes.SIGNIN) {
                    SignInScreen(
                        onBack = { navController.popOrStartOver() },
                        onHome = { navController.startOver(Routes.HOME) },
                        onVerifyEmail = { navController.startOver(Routes.verify()) },
                        onForgot = { email -> navController.navigate(Routes.forgot(email)) },
                        onCreateAccount = { navController.navigate(Routes.signUp()) { popUpTo(Routes.SIGNIN) { inclusive = true } } },
                    )
                }
                composable(
                    Routes.FORGOT,
                    arguments = listOf(navArgument(ForgotPasswordViewModel.ARG_EMAIL) { type = NavType.StringType; defaultValue = "" }),
                ) {
                    ForgotPasswordScreen(onBack = { navController.popOrStartOver() })
                }
                composable(
                    Routes.RESET,
                    arguments = listOf(navArgument(ResetPasswordViewModel.ARG_CODE) { type = NavType.StringType; defaultValue = "" }),
                ) {
                    ResetPasswordScreen(
                        onClose = { navController.popOrStartOver() },
                        onHome = { navController.startOver(Routes.HOME) },
                        onSignIn = {
                            navController.startOver(Routes.WELCOME)
                            navController.navigate(Routes.SIGNIN)
                        },
                        onRequestLink = { email ->
                            navController.startOver(Routes.WELCOME)
                            navController.navigate(Routes.SIGNIN)
                            navController.navigate(Routes.forgot(email))
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onBookClick = { navController.navigate(Routes.reader(it)) },
                        onSeeAll = { filter -> navController.navigateTo(Routes.library(filter)) },
                        openImportPicker = pendingImport,
                        onImportPickerOpened = { pendingImport = false },
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

/**
 * Va a [route] vaciando la pila: atrás no vuelve al onboarding (ONB-013, ONB-016) ni a un paso ya cumplido.
 */
private fun NavHostController.startOver(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Atrás dentro del onboarding; si no hay nada detrás (se entró por un enlace), a la bienvenida. */
private fun NavHostController.popOrStartOver() {
    if (!popBackStack()) startOver(Routes.WELCOME)
}

/** Cambia de destino de la barra sin apilar copias: Inicio queda como base. */
private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
        launchSingleTop = true
    }
}
