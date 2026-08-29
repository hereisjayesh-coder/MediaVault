package com.mediavault.app.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.mediavault.app.ui.screens.imageviewer.ImageViewerScreen
import com.mediavault.app.ui.screens.imageviewer.ImageViewerViewModel
import com.mediavault.app.ui.screens.legal.LegalDocumentScreen
import com.mediavault.app.ui.screens.library.LibraryScreen
import com.mediavault.app.ui.screens.player.PlayerHubScreen
import com.mediavault.app.ui.screens.player.PlayerScreen
import com.mediavault.app.ui.screens.player.PlayerViewModel
import com.mediavault.app.ui.screens.settings.SettingsScreen
import com.mediavault.app.ui.screens.sources.SourceDetailScreen
import com.mediavault.app.ui.screens.sources.SourceDetailViewModel
import com.mediavault.app.ui.screens.sources.SourcesScreen
import com.mediavault.app.R

private const val SOURCES_ROUTE = "sources"
private const val SOURCE_DETAIL_ROUTE = "sources/{${SourceDetailViewModel.SOURCE_ID_ARG}}"
private const val PLAYER_ITEM_ROUTE = "player/{${PlayerViewModel.MEDIA_ITEM_ID_ARG}}"
private const val IMAGE_VIEWER_ROUTE = "image/{${ImageViewerViewModel.MEDIA_ITEM_ID_ARG}}"
private const val SETTINGS_PRIVACY_ROUTE = "settings/privacy"
private const val SETTINGS_TERMS_ROUTE = "settings/terms"
private const val SETTINGS_LICENSES_ROUTE = "settings/licenses"

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
        // On a tablet, an unfolded foldable, or a phone in landscape, every other route here
        // (Home, Downloads, Library, Settings — all single-column, form/list-oriented content)
        // would otherwise stretch edge-to-edge: very long text lines, oversized cards, awkward
        // whitespace. Centering with a max width at Material's own "compact" breakpoint (600dp —
        // the same threshold `WindowSizeClass` uses to mean "phone-sized, don't bother
        // constraining") fixes this everywhere at once, with zero visual change on any phone in
        // portrait (which is already narrower than 600dp), preserving the approved design exactly
        // where it was designed for. The dedicated Player screen is deliberately excluded — video
        // content should use the full available width on any device, same as before.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isOnDedicatedPlayer) Modifier else Modifier.padding(innerPadding)),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = MediaVaultDestination.HOME.route,
                // fillMaxHeight() first, then cap+fill *width* separately — chaining fillMaxSize()
                // before widthIn() instead reports the pre-cap full width back to the centering Box
                // above (fillMaxSize's exact constraints "win" over a narrower widthIn max applied
                // after it), which visually caps the content but leaves it flush against the start
                // edge instead of centered. This order actually centers it.
                modifier = if (isOnDedicatedPlayer) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxHeight().widthIn(max = 600.dp).fillMaxWidth()
                },
                // One coordinated crossfade for every route change (bottom tabs and the dedicated
                // Player screen alike) — replaces the previous instant cut, which is what made the
                // Library<->Player transition specifically feel broken: the destination's background
                // appeared before its content (the video surface) was ready. A short, snappy fade
                // (not a slide/scale) keeps every other tab transition feeling the same as before,
                // just smoothed. Navigation-Compose keeps the outgoing entry composed (so
                // PlayerScreen's own onDispose-driven pause/release) until this exit animation
                // actually finishes — audio/video keep running through the fade instead of cutting
                // off mid-frame.
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(220)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                // Deliberately NOT a plain fadeOut() for every route — see the Player-pop decision
                // log entry. PlayerView is a SurfaceView (kept, not TextureView — see that same
                // entry for why not): it composites on its own SurfaceFlinger layer outside
                // Compose's draw/alpha pipeline, so animating *its* alpha via fadeOut() does nothing
                // to the actual video pixels — the destination fades in on schedule while the
                // still-fully-opaque video sits there unchanged, then vanishes in one frame the
                // instant the exit transition elapses and Compose actually disposes it.
                // ExitTransition.None leaves Player's content fully visible and unanimated for the
                // whole pop (Navigation-Compose still keeps it composed for exactly as long as
                // popEnterTransition's 220ms, same as before) so the incoming screen's fade-in is
                // what visually covers it — by the time it's actually removed, it's already hidden
                // under fully-opaque destination content, with nothing left to visibly pop.
                //
                // Every OTHER route is ordinary Compose content, not a SurfaceView — applying that
                // same ExitTransition.None there (as an earlier version of this file did) is a
                // different bug, not the same fix: an ordinary screen's alpha *is* part of Compose's
                // normal draw pipeline, so leaving it un-faded while the destination fades in on top
                // alpha-blends both screens together for the full 220ms — visible as lingering
                // text/icons/buttons from the exiting screen ghosting through the incoming one
                // (found on Source Details -> Back). Those routes need the symmetric fadeOut() they
                // had before, so only the Player pop keeps the special-cased transition.
                popExitTransition = {
                    val isLeavingPlayer = initialState.destination.hierarchy.any { it.route == PLAYER_ITEM_ROUTE }
                    if (isLeavingPlayer) ExitTransition.None else fadeOut(animationSpec = tween(220))
                },
            ) {
                composable(MediaVaultDestination.HOME.route) {
                    HomeScreen(
                        onNavigateToDestination = { navigateToDestination(navController, it) },
                        onNavigateToSources = { navController.navigate(SOURCES_ROUTE) },
                    )
                }
                composable(MediaVaultDestination.DOWNLOADS.route) {
                    DownloadsScreen(
                        onOpenPlayer = { mediaItemId -> navController.navigate("player/$mediaItemId") },
                        onOpenImageViewer = { mediaItemId -> navController.navigate("image/$mediaItemId") },
                    )
                }
                composable(MediaVaultDestination.LIBRARY.route) {
                    LibraryScreen(
                        onOpenPlayer = { mediaItemId -> navController.navigate("player/$mediaItemId") },
                        onOpenImageViewer = { mediaItemId -> navController.navigate("image/$mediaItemId") },
                    )
                }
                composable(MediaVaultDestination.PLAYER.route) {
                    PlayerHubScreen(
                        onOpenPlayer = { mediaItemId -> navController.navigate("player/$mediaItemId") },
                        onOpenLibrary = { navigateToDestination(navController, MediaVaultDestination.LIBRARY) },
                    )
                }
                composable(MediaVaultDestination.SETTINGS.route) {
                    SettingsScreen(
                        onNavigateToLibrary = { navigateToDestination(navController, MediaVaultDestination.LIBRARY) },
                        onNavigateToPrivacy = { navController.navigate(SETTINGS_PRIVACY_ROUTE) },
                        onNavigateToTerms = { navController.navigate(SETTINGS_TERMS_ROUTE) },
                        onNavigateToLicenses = { navController.navigate(SETTINGS_LICENSES_ROUTE) },
                    )
                }
                composable(SETTINGS_PRIVACY_ROUTE) {
                    LegalDocumentScreen(title = stringResource(R.string.settings_legal_privacy), assetFileName = "privacy.md")
                }
                composable(SETTINGS_TERMS_ROUTE) {
                    LegalDocumentScreen(title = stringResource(R.string.settings_legal_terms), assetFileName = "terms.md")
                }
                composable(SETTINGS_LICENSES_ROUTE) {
                    LegalDocumentScreen(title = stringResource(R.string.settings_legal_licenses), assetFileName = "third_party_notices.md")
                }
    
                // The dedicated, immersive playback screen — always reached with a specific item
                // id (from Library or the Player tab's "Resume" card), never as a bare tab page.
                composable(
                    route = PLAYER_ITEM_ROUTE,
                    arguments = listOf(navArgument(PlayerViewModel.MEDIA_ITEM_ID_ARG) { type = NavType.StringType }),
                ) {
                    PlayerScreen(onBackToLibrary = { navController.popBackStack() })
                }
    
                // A downloaded image's dedicated preview — never the video/audio Player route above.
                composable(
                    route = IMAGE_VIEWER_ROUTE,
                    arguments = listOf(navArgument(ImageViewerViewModel.MEDIA_ITEM_ID_ARG) { type = NavType.StringType }),
                ) {
                    ImageViewerScreen(onBack = { navController.popBackStack() })
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
}
