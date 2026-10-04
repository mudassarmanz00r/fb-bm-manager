package com.teamc.fbbmmanager.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teamc.fbbmmanager.ui.PageDetailViewModel
import com.teamc.fbbmmanager.ui.PagesViewModel
import com.teamc.fbbmmanager.ui.SessionViewModel
import com.teamc.fbbmmanager.ui.UploadViewModel
import com.teamc.fbbmmanager.ui.screens.BmManageScreen
import com.teamc.fbbmmanager.ui.screens.LoginScreen
import com.teamc.fbbmmanager.ui.screens.PageDetailScreen
import com.teamc.fbbmmanager.ui.screens.PagesListScreen
import com.teamc.fbbmmanager.ui.screens.UploadScreen

object Routes {
    const val LOGIN = "login"
    const val BM = "bm"
    const val PAGES = "pages"
    const val DETAIL = "detail/{pageId}"
    const val UPLOAD = "upload"

    fun detail(pageId: String) = "detail/$pageId"
}

private const val TRANSITION_MS = 280

@Composable
fun AppNav(
    activity: FragmentActivity,
    sessionVm: SessionViewModel,
    pagesVm: PagesViewModel,
    detailVm: PageDetailViewModel,
    uploadVm: UploadViewModel
) {
    val navController = rememberNavController()
    val start = when {
        !sessionVm.isLoggedIn -> Routes.LOGIN
        sessionVm.hasBm -> Routes.PAGES
        else -> Routes.BM
    }

    // After a successful login, move forward automatically.
    LaunchedEffect(sessionVm.isLoggedIn) {
        if (sessionVm.isLoggedIn &&
            navController.currentDestination?.route == Routes.LOGIN
        ) {
            val dest = if (sessionVm.hasBm) Routes.PAGES else Routes.BM
            navController.navigate(dest) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    // Global session-expiry: force logout and route back to Login once.
    LaunchedEffect(sessionVm.authExpiredEvent) {
        if (sessionVm.authExpiredEvent) {
            sessionVm.authExpiredConsumed()
            sessionVm.logout()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(TRANSITION_MS)) +
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MS)
            )
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(TRANSITION_MS)) +
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MS)
            )
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(TRANSITION_MS)) +
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MS)
            )
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(TRANSITION_MS)) +
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MS)
            )
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                vm = sessionVm,
                activity = activity,
                onLoggedIn = {
                    val dest = if (sessionVm.hasBm) Routes.PAGES else Routes.BM
                    navController.navigate(dest) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BM) {
            BmManageScreen(
                vm = sessionVm,
                onContinue = { navController.navigate(Routes.PAGES) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.BM) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PAGES) {
            PagesListScreen(
                pagesVm = pagesVm,
                sessionVm = sessionVm,
                onPageClick = { page -> navController.navigate(Routes.detail(page.id)) },
                onUploadClick = { navController.navigate(Routes.UPLOAD) },
                onManageBms = { navController.navigate(Routes.BM) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.PAGES) { inclusive = true }
                    }
                },
                onRelogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.PAGES) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { entry ->
            PageDetailScreen(
                pageId = entry.arguments?.getString("pageId").orEmpty(),
                vm = detailVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.UPLOAD) {
            UploadScreen(
                uploadVm = uploadVm,
                pagesVm = pagesVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
