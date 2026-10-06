package com.pandaapps.appstore.ui.navigation

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pandaapps.appstore.appContainer
import com.pandaapps.appstore.ui.StoreEvent
import com.pandaapps.appstore.ui.StoreViewModel
import com.pandaapps.appstore.ui.components.UnknownSourcesDialog
import com.pandaapps.appstore.ui.debug.DebugConsoleScreen
import com.pandaapps.appstore.ui.detail.AppDetailScreen
import com.pandaapps.appstore.ui.home.HomeScreen
import com.pandaapps.appstore.ui.settings.SettingsScreen
import com.pandaapps.appstore.util.DeepLink
import kotlinx.coroutines.flow.filterNotNull

/** Route strings for the single-activity nav graph. */
object Routes {
    const val HOME = "home"
    const val ARG_PACKAGE = "packageName"
    const val APP = "app/{$ARG_PACKAGE}"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"

    fun app(packageName: String) = "app/${Uri.encode(packageName)}"
}

/**
 * App-wide navigation. Owns the shared [StoreViewModel] (activity-scoped), consumes deep links
 * from [com.pandaapps.appstore.AppContainer.pendingDeepLink], and hosts the cross-screen
 * one-shot UI: the "Install unknown apps" dialog and a snackbar for [StoreEvent.Message].
 */
@Composable
fun PandaNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory),
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val snackbarHostState = remember { SnackbarHostState() }
    var showInstallPermissionDialog by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                StoreEvent.NeedInstallPermission -> showInstallPermissionDialog = true
                is StoreEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    LaunchedEffect(navController) {
        container.pendingDeepLink.filterNotNull().collect { link ->
            when (link) {
                DeepLink.Home -> navController.popBackStack(Routes.HOME, inclusive = false)
                is DeepLink.App -> navController.navigate(Routes.app(link.packageName)) {
                    popUpTo(Routes.HOME)
                    launchSingleTop = true
                }
            }
            container.consumeDeepLink()
        }
    }

    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { backEnter() },
            popExitTransition = { backExit() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenApp = { navController.navigate(Routes.app(it)) { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenDebug = { navController.navigate(Routes.DEBUG) { launchSingleTop = true } },
                )
            }
            composable(
                route = Routes.APP,
                arguments = listOf(navArgument(Routes.ARG_PACKAGE) { type = NavType.StringType }),
            ) { entry ->
                AppDetailScreen(
                    packageName = entry.packageArg(),
                    viewModel = viewModel,
                    onBack = { navController.navigateUpOrHome(entry) },
                )
            }
            composable(Routes.SETTINGS) { entry ->
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUpOrHome(entry) },
                    onOpenDebug = { navController.navigate(Routes.DEBUG) { launchSingleTop = true } },
                )
            }
            composable(Routes.DEBUG) { entry ->
                DebugConsoleScreen(onBack = { navController.navigateUpOrHome(entry) })
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    if (showInstallPermissionDialog) {
        UnknownSourcesDialog(
            onOpenSettings = {
                showInstallPermissionDialog = false
                try {
                    context.startActivity(viewModel.unknownSourcesSettingsIntent())
                    viewModel.onInstallPermissionSettingsOpened()
                } catch (e: ActivityNotFoundException) {
                    container.appLog.e("Nav", "No unknown-sources settings screen", e)
                    viewModel.dismissInstallPermission()
                }
            },
            onDismiss = {
                showInstallPermissionDialog = false
                viewModel.dismissInstallPermission()
            },
        )
    }
}

private fun NavBackStackEntry.packageArg(): String =
    arguments?.getString(Routes.ARG_PACKAGE).orEmpty()

/**
 * Leaves [from]. Ignored unless [from] is the resumed (top, fully shown) entry, so a double tap on
 * Back during the exit animation can't pop the home screen and leave an empty back stack. When the
 * screen was opened straight from a deep link with nothing below it, goes home instead.
 */
private fun NavHostController.navigateUpOrHome(from: NavBackStackEntry) {
    if (from.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.HOME) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

// --- Transitions: forward = slide in from the end + fade; back = the reverse. ------------------

private const val NAV_DURATION = 320
private val navEasing = FastOutSlowInEasing

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
        initialOffset = { it / 4 },
    ) + fadeIn(tween(NAV_DURATION, easing = navEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
        targetOffset = { it / 8 },
    ) + fadeOut(tween(NAV_DURATION / 2))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backEnter(): EnterTransition =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
        initialOffset = { it / 8 },
    ) + fadeIn(tween(NAV_DURATION, easing = navEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backExit(): ExitTransition =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
        targetOffset = { it / 4 },
    ) + fadeOut(tween(NAV_DURATION / 2)) + scaleOut(targetScale = 0.98f)
