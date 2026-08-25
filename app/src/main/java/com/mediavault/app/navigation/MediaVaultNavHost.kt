package com.mediavault.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediavault.app.ui.screens.downloads.DownloadsScreen
import com.mediavault.app.ui.screens.home.HomeScreen
import com.mediavault.app.ui.screens.library.LibraryScreen
import com.mediavault.app.ui.screens.player.PlayerHubScreen
import com.mediavault.app.ui.screens.player.PlayerScreen
import com.mediavault.app.ui.screens.player.PlayerViewModel
import com.mediavault.app.ui.screens.settings.SettingsScreen
import com.mediavault.app.ui.screens.sources.SourceDetailScreen
import com.mediavault.app.ui.screens.sources.SourceDetailViewModel
import com.mediavault.app.ui.screens.sources.SourcesScreen

private const val SOURCES_ROUTE = "sources"
private const val SOURCE_DETAIL_ROUTE = "sources/{${SourceDetailViewModel.SOURCE_ID_ARG}}"
private const val PLAYER_ITEM_ROUTE = "player/{${PlayerViewModel.MEDIA_ITEM_ID_ARG}}"

private val bottomBarDestinations = listOf(
    MediaVaultDestination.HOME,
    MediaVaultDestination.DOWNLOADS,
    MediaVaultDestination.LIBRARY,
    MediaVaultDestination.PLAYER,
    MediaVaultDestination.SETTINGS,
)

private fun navigateToDestination(navController: NavHostController, destination: MediaVaultDestination) {
    // Extra drill-in routes (Sources, Source detail) can sit above a tab on the back stack.
    // If the target tab is already there, popping straight back to it is simpler and avoids
    // the saveState/restoreState machinery below mis-restoring a sibling route's saved state
    // instead of the tab's own. Only fall back to a fresh navigate for a tab never visited yet.
    if (navController.popBackStack(destination.route, false)) return

    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MediaVaultNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // The dedicated Player screen is a full immersive experience, not a fifth tab page — the
    // bottom nav would only get in the way of playback controls/fullscreen, and it manages its
    // own status-bar-safe padding (reactively, off its own fullscreen state) rather than the
    // Scaffold's, since Scaffold's inset padding doesn't react to that screen's own imperative
    // system-bar hide/show calls.
    val isOnDedicatedPlayer = currentDestination?.hierarchy?.any { it.route == PLAYER_ITEM_ROUTE } == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isOnDedicatedPlayer) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomBarDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToDestination(navController, destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MediaVaultDestination.HOME.route,
            modifier = if (isOnDedicatedPlayer) Modifier else Modifier.padding(innerPadding),
        ) {
            composable(MediaVaultDestination.HOME.route) {
                HomeScreen(
                    onNavigateToDestination = { navigateToDestination(navController, it) },
                    onNavigateToSources = { navController.navigate(SOURCES_ROUTE) },
                )
            }
            composable(MediaVaultDestination.DOWNLOADS.route) { DownloadsScreen() }
            composable(MediaVaultDestination.LIBRARY.route) {
                LibraryScreen(onOpenPlayer = { mediaItemId -> navController.navigate("player/$mediaItemId") })
            }
            composable(MediaVaultDestination.PLAYER.route) {
                PlayerHubScreen(
                    onOpenPlayer = { mediaItemId -> navController.navigate("player/$mediaItemId") },
                    onOpenLibrary = { navigateToDestination(navController, MediaVaultDestination.LIBRARY) },
                )
            }
            composable(MediaVaultDestination.SETTINGS.route) { SettingsScreen() }

            // The dedicated, immersive playback screen — always reached with a specific item
            // id (from Library or the Player tab's "Resume" card), never as a bare tab page.
            composable(
                route = PLAYER_ITEM_ROUTE,
                arguments = listOf(navArgument(PlayerViewModel.MEDIA_ITEM_ID_ARG) { type = NavType.StringType }),
            ) {
                PlayerScreen(onBackToLibrary = { navController.popBackStack() })
            }

            composable(SOURCES_ROUTE) {
                SourcesScreen(onSourceClick = { sourceId -> navController.navigate("sources/$sourceId") })
            }
            composable(
                route = SOURCE_DETAIL_ROUTE,
                arguments = listOf(navArgument(SourceDetailViewModel.SOURCE_ID_ARG) { type = NavType.StringType }),
            ) {
                SourceDetailScreen(
                    onGoToAnalyzer = { navigateToDestination(navController, MediaVaultDestination.HOME) },
                )
            }
        }
    }
}
