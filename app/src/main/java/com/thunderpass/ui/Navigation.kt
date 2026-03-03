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
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth

private object Routes {
    const val SPLASH            = "splash"
    const val AUTH              = "auth"
    const val ONBOARDING        = "onboarding"
    const val HOME              = "home"
    const val ENCOUNTERS        = "encounters"
    const val ENCOUNTER_DETAIL  = "encounter_detail/{encounterId}"
    const val PROFILE           = "profile"
    const val SHOP              = "shop"
    const val STICKER_BOOK      = "sticker_book"
    fun encounterDetail(id: Long) = "encounter_detail/$id"
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
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                if (session == null) {
                    // No session — go to auth screen
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                } else {
                    val firstRun = profileVm.isFirstRun()
                    val target   = if (firstRun) Routes.ONBOARDING else Routes.HOME
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // ── Email OTP auth ──────────────────────────────────────────────────
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        // ── The Grid — first-run onboarding ───────────────────────────────────
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onEnter = {
                    navController.navigate("${Routes.PROFILE}?firstRun=true") {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEncounters  = { navController.navigate(Routes.ENCOUNTERS) },
                onNavigateToProfile     = { navController.navigate(Routes.PROFILE) },
                onNavigateToDetail      = { id -> navController.navigate(Routes.encounterDetail(id)) },
                onNavigateToShop        = { navController.navigate(Routes.SHOP) },
                onNavigateToStickerBook = { navController.navigate(Routes.STICKER_BOOK) },
                vm = homeVm,
            )
        }

        // ── Encounters ────────────────────────────────────────────────────────
        composable(Routes.ENCOUNTERS) {
            EncounterListScreen(
                onBack              = { navController.popBackStack() },
                onNavigateToHome    = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToDetail  = { id -> navController.navigate(Routes.encounterDetail(id)) },
                vm                  = homeVm,
            )
        }

        // ── Encounter Detail ──────────────────────────────────────────────────
        composable(
            route     = Routes.ENCOUNTER_DETAIL,
            arguments = listOf(
                navArgument("encounterId") { type = NavType.LongType }
            ),
        ) { backStack ->
            val id = backStack.arguments?.getLong("encounterId") ?: return@composable
            EncounterDetailScreen(
                encounterId = id,
                onBack      = { navController.popBackStack() },
                vm          = homeVm,
            )
        }

        // ── Visual Shop ───────────────────────────────────────────────────────
        composable(Routes.SHOP) {
            ShopScreen(onBack = { navController.popBackStack() }, vm = homeVm)
        }

        // ── Sticker Book ──────────────────────────────────────────────────────
        composable(Routes.STICKER_BOOK) {
            StickerBookScreen(onBack = { navController.popBackStack() }, vm = homeVm)
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
                onBack                 = { navController.popBackStack() },
                onNavigateToHome       = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToEncounters = { navController.navigate(Routes.ENCOUNTERS) },
                firstRun               = firstRun,
                onComplete             = if (firstRun) {
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
