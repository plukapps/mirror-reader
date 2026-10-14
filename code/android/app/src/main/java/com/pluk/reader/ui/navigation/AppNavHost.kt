package com.pluk.reader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import com.pluk.reader.ui.actions.BookActionsHost
import com.pluk.reader.ui.actions.BookActionsState
import com.pluk.reader.ui.actions.LocalBookActions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
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
import com.pluk.reader.domain.help.Faq
import com.pluk.reader.ui.profile.ChangePasswordScreen
import com.pluk.reader.ui.profile.FaqAnswerScreen
import com.pluk.reader.ui.profile.FaqListScreen
import com.pluk.reader.ui.profile.ProfileScreen
import com.pluk.reader.ui.search.SearchScreen
import com.pluk.reader.ui.welcome.WelcomeScreen
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.pluk.reader.ui.theme.LightPalette
import com.pluk.reader.ui.detail.BookDetailScreen
import com.pluk.reader.ui.detail.BookDetailViewModel
import com.pluk.reader.ui.theme.ReaderTheme
import com.pluk.reader.ui.theme.LocalMarginPalette
import com.pluk.reader.ui.theme.SystemBarsEffect

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
    const val READER = "reader/{${ReaderViewModel.ARG_BOOK_ID}}?${ReaderViewModel.ARG_NOTE_ID}={${ReaderViewModel.ARG_NOTE_ID}}"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val FAQ_LIST = "profile/faq"
    const val FAQ_ANSWER = "profile/faq/{key}"
    const val CHANGE_PASSWORD = "profile/password"
    const val BOOK_DETAIL = "book/{${BookDetailViewModel.ARG_BOOK_ID}}"
    fun bookDetail(bookId: String): String = "book/$bookId"
    fun faqAnswer(key: String): String = "profile/faq/$key"

    /** El lector en la última posición del libro o, con [noteId], en el lugar de esa nota (ANN-010). */
    fun reader(bookId: String, noteId: String? = null): String =
        if (noteId == null) "reader/$bookId" else "reader/$bookId?${ReaderViewModel.ARG_NOTE_ID}=${Uri.encode(noteId)}"

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
    // ONB-013: "Importar mis libros" abre el selector apenas aparece Inicio.
    var pendingImport by rememberSaveable { mutableStateOf(false) }
    val route by navController.currentBackStackEntryAsState()
    val selected = when (route?.destination?.route) {
        Routes.HOME -> MainDestination.Home
        Routes.LIBRARY -> MainDestination.Shelves
        Routes.SEARCH -> MainDestination.Search
        Routes.PROFILE -> MainDestination.Profile
        else -> null // La barra se oculta en el lector.
    }
    // PRF-021: onboarding y lector, siempre claros; el resto sigue la apariencia. El lector maneja sus barras.
    val routeName = route?.destination?.route
    val activity = LocalActivity.current as? ComponentActivity
    if (activity != null && routeName != null && routeName != Routes.READER) {
        SystemBarsEffect(activity, darkIcons = routeName in LightOnlyRoutes || !LocalMarginPalette.current.isDark)
    }
    // La barra flota sobre el contenido, que pasa por debajo; las pantallas dejan este espacio al final.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (selected != null) BottomBarHeight + navBarInset else 0.dp
    // LIB-016: un solo menú de libro para Inicio, Estantes y Buscar, dibujado encima de todo.
    val bookActions = remember { BookActionsState() }
    val menuOpen = bookActions.target != null
    Box(Modifier.fillMaxSize()) {
        // Con el menú abierto el fondo se desenfoca (diseño 05b; sin efecto antes de Android 12).
        Box(Modifier.fillMaxSize().then(if (menuOpen) Modifier.blur(3.dp) else Modifier)) {
            CompositionLocalProvider(LocalBottomBarPadding provides bottomPadding, LocalBookActions provides bookActions) {
                NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
                    lightComposable(Routes.WELCOME) {
                        WelcomeScreen(
                            onCreateAccount = { navController.navigate(Routes.signUp()) },
                            onSignIn = { navController.navigate(Routes.SIGNIN) },
                        )
                    }
                    lightComposable(
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
                    lightComposable(
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
                    lightComposable(Routes.INTERESTS) {
                        InterestsScreen(
                            onBack = null,
                            onDone = { navController.navigate(Routes.GOAL) },
                        )
                    }
                    lightComposable(Routes.GOAL) {
                        GoalScreen(
                            onBack = { navController.popBackStack() },
                            onDone = { navController.startOver(Routes.ALL_SET) },
                        )
                    }
                    lightComposable(Routes.ALL_SET) {
                        AllSetScreen(
                            onStartReading = { navController.startOver(Routes.HOME) },
                            onImportBooks = {
                                pendingImport = true
                                navController.startOver(Routes.HOME)
                            },
                        )
                    }
                    lightComposable(Routes.SIGNIN) {
                        SignInScreen(
                            onBack = { navController.popOrStartOver() },
                            onHome = { navController.startOver(Routes.HOME) },
                            onVerifyEmail = { justSent -> navController.startOver(Routes.verify(justSent)) },
                            onForgot = { email -> navController.navigate(Routes.forgot(email)) },
                            onCreateAccount = { navController.navigate(Routes.signUp()) { popUpTo(Routes.SIGNIN) { inclusive = true } } },
                        )
                    }
                    lightComposable(
                        Routes.FORGOT,
                        arguments = listOf(navArgument(ForgotPasswordViewModel.ARG_EMAIL) { type = NavType.StringType; defaultValue = "" }),
                    ) {
                        ForgotPasswordScreen(onBack = { navController.popOrStartOver() })
                    }
                    lightComposable(
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
                    composable(Routes.PROFILE) {
                        // PRF-009: con la sesión cerrada, a la bienvenida sin poder volver atrás.
                        ProfileScreen(
                            onSignedOut = { navController.startOver(Routes.WELCOME) },
                            onFaq = { navController.navigate(Routes.faqAnswer(it.key)) },
                            onAllFaq = { navController.navigate(Routes.FAQ_LIST) },
                            onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                        )
                    }
                    // PRF-020: cambiar contraseña, sin barra inferior.
                    composable(Routes.CHANGE_PASSWORD) {
                        ChangePasswordScreen(onBack = { navController.popBackStack() })
                    }
                    // PRF-017, PRF-018: preguntas frecuentes, sin barra inferior.
                    composable(Routes.FAQ_LIST) {
                        FaqListScreen(
                            onBack = { navController.popBackStack() },
                            onOpen = { navController.navigate(Routes.faqAnswer(it.key)) },
                        )
                    }
                    composable(Routes.FAQ_ANSWER, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        val id = Faq.byKey(entry.arguments?.getString("key").orEmpty())
                        if (id != null) FaqAnswerScreen(id, onBack = { navController.popBackStack() })
                    }
                    // LIB-022: detalle del libro, sin barra inferior. Tocar una nota abre el lector en ella (ANN-010).
                    composable(
                        Routes.BOOK_DETAIL,
                        arguments = listOf(navArgument(BookDetailViewModel.ARG_BOOK_ID) { type = NavType.StringType }),
                    ) {
                        BookDetailScreen(
                            onBack = { navController.popBackStack() },
                            onRead = { navController.navigate(Routes.reader(it)) },
                            onOpenNote = { bookId, noteId -> navController.navigate(Routes.reader(bookId, noteId)) },
                        )
                    }
                    lightComposable(
                        route = Routes.READER,
                        arguments = listOf(
                            navArgument(ReaderViewModel.ARG_BOOK_ID) { type = NavType.StringType },
                            navArgument(ReaderViewModel.ARG_NOTE_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                        ),
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
                        MainDestination.Profile -> navController.navigateTo(Routes.PROFILE)
                    }
                }
            }
        }
        if (selected != null) {
            BookActionsHost(
                bookActions,
                onRead = { navController.navigate(Routes.reader(it)) },
                onDetails = { navController.navigate(Routes.bookDetail(it)) },
                bottomPadding = bottomPadding,
            )
        }
    }
}

/** Rutas del onboarding (O1 a O9): siempre con la paleta clara (PRF-021). */
private val LightOnlyRoutes = setOf(
    Routes.WELCOME, Routes.SIGNUP, Routes.VERIFY, Routes.INTERESTS, Routes.GOAL, Routes.ALL_SET,
    Routes.SIGNIN, Routes.FORGOT, Routes.RESET,
)

/**
 * Destino que se dibuja siempre con la paleta clara (PRF-021). El onboarding también fuerza el tema claro de Material
 * (el selector de hora de O7 toma de ahí sus colores); el lector conserva el suyo.
 */
private fun NavGraphBuilder.lightComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    if (route == Routes.READER) LightPalette { content(entry) }
    else ReaderTheme(darkTheme = false) { LightPalette { content(entry) } }
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
