package com.thunderpass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val SPLASH     = "splash"
    const val HOME       = "home"
    const val ENCOUNTERS = "encounters"
    const val PROFILE    = "profile"
}

/**
 * Root navigation graph for ThunderPass.
 *
 * On first launch the graph starts at a momentary splash composable that
 * reads the profile from the database and redirects to:
 *  - [Routes.PROFILE]?firstRun=true  when the profile is still at defaults
 *  - [Routes.HOME]                   for returning users
 */
@Composable
fun ThunderPassNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val homeVm: HomeViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        // ── Splash / onboarding gate ──────────────────────────────────────────
        composable(Routes.SPLASH) {
            val profileVm: ProfileViewModel = viewModel()
            LaunchedEffect(Unit) {
                val firstRun = profileVm.isFirstRun()
                val target   = if (firstRun) "${Routes.PROFILE}?firstRun=true" else Routes.HOME
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
            // Brief blank screen while the DB check completes (~1 frame)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEncounters = { navController.navigate(Routes.ENCOUNTERS) },
                onNavigateToProfile    = { navController.navigate(Routes.PROFILE) },
                vm = homeVm,
            )
        }

        // ── Encounters ────────────────────────────────────────────────────────
        composable(Routes.ENCOUNTERS) {
            EncounterListScreen(
                onBack = { navController.popBackStack() },
                vm     = homeVm,
            )
        }

        // ── Profile (with optional firstRun flag) ─────────────────────────────
        composable(
            route     = "${Routes.PROFILE}?firstRun={firstRun}",
            arguments = listOf(
                navArgument("firstRun") {
                    type         = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStack ->
            val firstRun = backStack.arguments?.getBoolean("firstRun") ?: false
            ProfileScreen(
                onBack     = { navController.popBackStack() },
                firstRun   = firstRun,
                onComplete = if (firstRun) {
                    {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PROFILE) { inclusive = true }
                        }
                    }
                } else null,
            )
        }
    }
}
