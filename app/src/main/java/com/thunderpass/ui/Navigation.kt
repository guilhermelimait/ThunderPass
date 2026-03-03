package com.thunderpass.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val HOME      = "home"
    const val ENCOUNTERS = "encounters"
    const val PROFILE   = "profile"
}

/**
 * Root navigation graph for ThunderPass.
 *
 * [HomeViewModel] is scoped to the NavController's back-stack entry so that
 * [HomeScreen] and [EncounterListScreen] share the same ViewModel instance
 * (same encounter list / service state).
 */
@Composable
fun ThunderPassNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    // Hoist the shared ViewModel at the graph level so both screens see the same instance
    val homeVm: HomeViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEncounters = { navController.navigate(Routes.ENCOUNTERS) },
                onNavigateToProfile    = { navController.navigate(Routes.PROFILE) },
                vm = homeVm,
            )
        }

        composable(Routes.ENCOUNTERS) {
            EncounterListScreen(
                onBack = { navController.popBackStack() },
                vm     = homeVm,
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
