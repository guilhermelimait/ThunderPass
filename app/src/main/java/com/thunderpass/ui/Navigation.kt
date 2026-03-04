package com.thunderpass.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thunderpass.ui.theme.ThunderPassTheme

internal object Routes {
    const val SPLASH            = "splash"
    const val ONBOARDING        = "onboarding"
    const val HOME              = "home"
    const val ENCOUNTERS        = "encounters"
    const val ENCOUNTER_DETAIL  = "encounter_detail/{encounterId}"
    const val PROFILE           = "profile"
    const val BADGES            = "badges"
    const val BADGES_CATEGORY   = "badges_category/{categoryName}"
    const val SHOP              = "shop"
    const val SETTINGS          = "settings"
    const val ABOUT             = "about"
    const val SPARKY_EDITOR     = "sparky_editor"
    fun encounterDetail(id: Long) = "encounter_detail/$id"
    fun badgesCategory(name: String) = "badges_category/$name"
}


@Composable
fun ThunderPassNavGraph(
    navController: NavHostController = rememberNavController(),
    onMusicChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("tp_settings", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    val homeVm: HomeViewModel = viewModel()
    val pendingFriendUserId by homeVm.friendInviteUserId.collectAsState()
    val friendInviteResult  by homeVm.friendInviteResult.collectAsState()

    ThunderPassTheme(darkTheme = darkMode) {

        // ── Friend-invite dialogs (shown over any screen) ─────────────────────
        pendingFriendUserId?.let { userId ->
            AlertDialog(
                onDismissRequest = { homeVm.resolveFriendInvite(userId) },
                title   = { Text("Friend Invite 🤝") },
                text    = {
                    Text(
                        "Someone sent you a friend invite. Should ThunderPass mark them as a friend?\n\n" +
                        "If you haven't encountered this person via BLE yet, walk near each other with ThunderPass active first."
                    )
                },
                confirmButton = {
                    Button(onClick = { homeVm.resolveFriendInvite(userId) }) { Text("Connect") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        // Dismiss without resolving — clear the pref so it doesn't show again
                        homeVm.resolveFriendInvite("")
                    }) { Text("Dismiss") }
                },
            )
        }

        friendInviteResult?.let { result ->
            AlertDialog(
                onDismissRequest = { homeVm.dismissFriendInviteResult() },
                title   = { Text(if (result is HomeViewModel.FriendInviteResult.Added) "Friend Added! ⚡" else "Not yet connected") },
                text    = {
                    when (result) {
                        is HomeViewModel.FriendInviteResult.Added ->
                            Text("${result.displayName} is now in your friends list.")
                        is HomeViewModel.FriendInviteResult.NotMetYet ->
                            Text("No encounter found with this person yet. Walk near each other with ThunderPass active — once you Spark, they'll appear in your Passes and can be marked as a friend.")
                    }
                },
                confirmButton = {
                    Button(onClick = { homeVm.dismissFriendInviteResult() }) { Text("OK") }
                },
            )
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Routes.SPLASH,
                modifier         = Modifier.padding(innerPadding),
                enterTransition  = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) +
                    fadeIn(animationSpec = tween(280))
                },
                exitTransition   = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(280)) +
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition  = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(280)) +
                    fadeIn(animationSpec = tween(280))
                },
                popExitTransition   = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) +
                    fadeOut(animationSpec = tween(200))
                },
            ) {
                composable(Routes.SPLASH) {
                    LaunchedEffect(Unit) {
                        // Always go to Home — device name is auto-seeded (5.1), no mandatory profile setup.
                        navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToDetail = { id -> navController.navigate(Routes.encounterDetail(id)) },
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        vm = homeVm,
                    )
                }
                composable(Routes.ENCOUNTERS) {
                    EncounterListScreen(
                        onNavigateToDetail = { id -> navController.navigate(Routes.encounterDetail(id)) },
                        onBack = { navController.popBackStack() },
                        vm = homeVm,
                    )
                }
                composable(
                    route     = Routes.ENCOUNTER_DETAIL,
                    arguments = listOf(navArgument("encounterId") { type = NavType.LongType }),
                ) { bs ->
                    val id = bs.arguments?.getLong("encounterId") ?: return@composable
                    EncounterDetailScreen(
                        encounterId = id,
                        onBack      = { navController.popBackStack() },
                        vm          = homeVm,
                    )
                }
                composable(
                    route     = "${Routes.PROFILE}?firstRun={firstRun}",
                    arguments = listOf(navArgument("firstRun") {
                        type         = NavType.BoolType
                        defaultValue = false
                    }),
                ) { bs ->
                    val firstRun = bs.arguments?.getBoolean("firstRun") ?: false
                    ProfileScreen(
                        firstRun     = firstRun,
                        onEditSparky = { navController.navigate(Routes.SPARKY_EDITOR) },
                        onComplete   = if (firstRun) {
                            { navController.navigate(Routes.HOME) { popUpTo(Routes.PROFILE) { inclusive = true } } }
                        } else null,
                        onBack = if (!firstRun) { { navController.popBackStack() } } else null,
                    )
                }
                composable(Routes.BADGES) {
                    BadgesScreen(
                        onNavigateToCategory = { cat -> navController.navigate(Routes.badgesCategory(cat)) },
                        onBack = { navController.popBackStack() },
                        vm = homeVm,
                    )
                }
                composable(
                    route     = Routes.BADGES_CATEGORY,
                    arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                ) { bs ->
                    val categoryName = bs.arguments?.getString("categoryName") ?: return@composable
                    BadgeCategoryDetailScreen(
                        categoryName = categoryName,
                        onBack       = { navController.popBackStack() },
                    )
                }
                composable(Routes.SHOP)  { ShopScreen(onBack = { navController.popBackStack() }, vm = homeVm) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        darkMode         = darkMode,
                        onDarkModeToggle = { enabled ->
                            darkMode = enabled
                            prefs.edit().putBoolean("dark_mode", enabled).apply()
                        },
                        onMusicChange = onMusicChange,
                        onBack = { navController.popBackStack() },
                        vm = homeVm,
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen()
                }
                composable(Routes.SPARKY_EDITOR) {
                    SparkyEditorScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
