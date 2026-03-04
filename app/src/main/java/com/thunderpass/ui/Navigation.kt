package com.thunderpass.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thunderpass.ui.theme.ThunderPassTheme

private object Routes {
    const val SPLASH           = "splash"
    const val ONBOARDING       = "onboarding"
    const val HOME             = "home"
    const val ENCOUNTERS       = "encounters"
    const val ENCOUNTER_DETAIL = "encounter_detail/{encounterId}"
    const val PROFILE          = "profile"
    const val BADGES           = "badges"
    const val SHOP             = "shop"
    const val SETTINGS         = "settings"
    fun encounterDetail(id: Long) = "encounter_detail/$id"
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem(Routes.HOME,       "Home",     Icons.Filled.Home),
    NavItem(Routes.ENCOUNTERS, "Passes",   Icons.AutoMirrored.Filled.List),
    NavItem(Routes.PROFILE,    "Profile",  Icons.Filled.Person),
    NavItem(Routes.BADGES,     "Badges",   Icons.Filled.Star),
    NavItem(Routes.SHOP,       "Shop",     Icons.Filled.ShoppingCart),
    NavItem(Routes.SETTINGS,   "Settings", Icons.Filled.Settings),
)

private val TOP_LEVEL_ROUTES = NAV_ITEMS.map { it.route }.toSet()

@Composable
fun ThunderPassNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("tp_settings", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    val homeVm: HomeViewModel = viewModel()

    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES
    val isLandscape   = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ThunderPassTheme(darkTheme = darkMode) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        modifier       = if (isLandscape) Modifier.height(52.dp) else Modifier,
                        tonalElevation = 2.dp,
                    ) {
                        NAV_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick  = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    }
                                },
                                icon            = { Icon(item.icon, contentDescription = item.label) },
                                label           = { if (!isLandscape) Text(item.label) },
                                alwaysShowLabel = !isLandscape,
                            )
                        }
                    }
                }
            },
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
                    val profileVm: ProfileViewModel = viewModel()
                    LaunchedEffect(Unit) {
                        val target = if (profileVm.isFirstRun()) Routes.ONBOARDING else Routes.HOME
                        navController.navigate(target) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onEnter = {
                            navController.navigate("${Routes.PROFILE}?firstRun=true") {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToDetail = { id -> navController.navigate(Routes.encounterDetail(id)) },
                        vm = homeVm,
                    )
                }
                composable(Routes.ENCOUNTERS) {
                    EncounterListScreen(
                        onNavigateToDetail = { id -> navController.navigate(Routes.encounterDetail(id)) },
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
                        firstRun   = firstRun,
                        onComplete = if (firstRun) {
                            { navController.navigate(Routes.HOME) { popUpTo(Routes.PROFILE) { inclusive = true } } }
                        } else null,
                    )
                }
                composable(Routes.BADGES)   { BadgesScreen(vm = homeVm) }
                composable(Routes.SHOP)     { ShopScreen(onBack = { navController.popBackStack() }, vm = homeVm) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        darkMode         = darkMode,
                        onDarkModeToggle = { enabled ->
                            darkMode = enabled
                            prefs.edit().putBoolean("dark_mode", enabled).apply()
                        },
                        vm = homeVm,
                    )
                }
            }
        }
    }
}
