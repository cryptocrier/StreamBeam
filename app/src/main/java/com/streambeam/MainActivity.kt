package com.streambeam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.cast.framework.CastContext
import com.streambeam.ui.screens.HomeScreen
import com.streambeam.ui.screens.PlayerScreen
import com.streambeam.ui.screens.RecentlyWatchedScreen
import com.streambeam.ui.screens.SettingsScreen
import com.streambeam.ui.screens.StreamSelectionScreen
import com.streambeam.ui.screens.TVShowDetailScreen
import com.streambeam.model.Meta
import com.streambeam.ui.components.PersistentCastBar
import com.streambeam.ui.theme.StreamBeamTheme
import com.streambeam.viewmodel.MainViewModel
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    
    // Store selected TV show for navigation
    private var selectedTVShow: Meta? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize CastContext
        CastContext.getSharedInstance(this)
        
        setContent {
            StreamBeamTheme {
                val navController = rememberNavController()
                
                // Track current playback info for the persistent cast bar
                val currentPlayerUrl = remember { mutableStateOf<String?>(null) }
                val currentPlayerTitle = remember { mutableStateOf("") }
                val currentPlayerPoster = remember { mutableStateOf<String?>(null) }
                
                // Track current route to hide cast bar when on player screen
                val currentRoute = remember { mutableStateOf("home") }
                
                // Listen to navigation changes
                navController.addOnDestinationChangedListener { _, destination, _ ->
                    currentRoute.value = destination.route ?: ""
                }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Persistent cast bar - shown when casting is active and NOT on player screen
                        val isPlayerScreen = currentRoute.value.startsWith("player/")
                        if (!isPlayerScreen) {
                            PersistentCastBar(
                                castManager = viewModel.castManager,
                                onNavigateToPlayer = {
                                    // Navigate back to player with current media
                                    currentPlayerUrl.value?.let { url ->
                                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                        val encodedTitle = URLEncoder.encode(currentPlayerTitle.value, StandardCharsets.UTF_8.toString())
                                        val encodedPoster = URLEncoder.encode(currentPlayerPoster.value ?: "", StandardCharsets.UTF_8.toString())
                                        navController.navigate("player/$encodedUrl?title=$encodedTitle&poster=$encodedPoster") {
                                            // Avoid duplicate navigation
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToStreams = { metaId, type, title, posterUrl ->
                                    if (type == "series") {
                                        // Find the TV show meta
                                        val tvShow = viewModel.tvShows.value.find { it.id == metaId }
                                            ?: viewModel.searchResults.value.find { it.id == metaId && it.type == "series" }
                                        tvShow?.let {
                                            selectedTVShow = it
                                            navController.navigate("tvshow/$metaId")
                                        }
                                    } else {
                                        // Movie - go directly to streams
                                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                                        val encodedPoster = URLEncoder.encode(posterUrl ?: "", StandardCharsets.UTF_8.toString())
                                        navController.navigate("streams/$metaId/$type/$encodedTitle?poster=$encodedPoster")
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToRecentlyWatched = {
                                    navController.navigate("recent")
                                }
                            )
                        }
                        composable("recent") {
                            RecentlyWatchedScreen(
                                viewModel = viewModel,
                                onItemClick = { watchItem ->
                                    // Navigate to player or stream selection based on content type
                                    if (watchItem.type == "series") {
                                        // For TV shows, go to stream selection
                                        val episodeId = if (watchItem.season != null && watchItem.episode != null) {
                                            "${watchItem.metaId}:${watchItem.season}:${watchItem.episode}"
                                        } else watchItem.metaId
                                        val encodedTitle = URLEncoder.encode(watchItem.getDisplayTitle(), StandardCharsets.UTF_8.toString())
                                        val encodedPoster = URLEncoder.encode(watchItem.poster ?: "", StandardCharsets.UTF_8.toString())
                                        navController.navigate("streams/$episodeId/series/$encodedTitle?poster=$encodedPoster")
                                    } else {
                                        // For movies, go to stream selection
                                        val encodedTitle = URLEncoder.encode(watchItem.name, StandardCharsets.UTF_8.toString())
                                        val encodedPoster = URLEncoder.encode(watchItem.poster ?: "", StandardCharsets.UTF_8.toString())
                                        navController.navigate("streams/${watchItem.metaId}/movie/$encodedTitle?poster=$encodedPoster")
                                    }
                                },
                                onClearAll = {
                                    viewModel.clearWatchHistory()
                                }
                            )
                        }
                        composable(
                            "tvshow/{metaId}",
                            arguments = listOf(navArgument("metaId") { type = NavType.StringType })
                        ) {
                            selectedTVShow?.let { tvShow ->
                                TVShowDetailScreen(
                                    viewModel = viewModel,
                                    tvShow = tvShow,
                                    onBack = { navController.popBackStack() },
                                    onEpisodeClick = { episodeId, episodeTitle ->
                                        val safeTitle = episodeTitle ?: "Episode"
                                        val encodedTitle = URLEncoder.encode(
                                            "${tvShow.name} - $safeTitle",
                                            StandardCharsets.UTF_8.toString()
                                        )
                                        val encodedPoster = URLEncoder.encode(tvShow.poster ?: "", StandardCharsets.UTF_8.toString())
                                        navController.navigate("streams/$episodeId/series/$encodedTitle?poster=$encodedPoster")
                                    }
                                )
                            }
                        }
                        // Backward compatibility: streams without poster
                        composable(
                            "streams/{metaId}/{type}/{title}",
                            arguments = listOf(
                                navArgument("metaId") { type = NavType.StringType },
                                navArgument("type") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val metaId = backStackEntry.arguments?.getString("metaId") ?: ""
                            val type = backStackEntry.arguments?.getString("type") ?: "movie"
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val title = URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.toString())
                            
                            StreamSelectionScreen(
                                viewModel = viewModel,
                                metaId = metaId,
                                type = type,
                                title = title,
                                posterUrl = null,
                                onNavigateToPlayer = { url ->
                                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                    currentPlayerUrl.value = url
                                    currentPlayerTitle.value = title
                                    currentPlayerPoster.value = null
                                    navController.navigate("player/$encodedUrl?title=$encodedTitle")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable(
                            "streams/{metaId}/{type}/{title}?poster={poster}",
                            arguments = listOf(
                                navArgument("metaId") { type = NavType.StringType },
                                navArgument("type") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                                navArgument("poster") { 
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val metaId = backStackEntry.arguments?.getString("metaId") ?: ""
                            val type = backStackEntry.arguments?.getString("type") ?: "movie"
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val encodedPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val title = URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.toString())
                            val poster = URLDecoder.decode(encodedPoster, StandardCharsets.UTF_8.toString())
                            
                            StreamSelectionScreen(
                                viewModel = viewModel,
                                metaId = metaId,
                                type = type,
                                title = title,
                                posterUrl = poster.ifEmpty { null },
                                onNavigateToPlayer = { url ->
                                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                    currentPlayerUrl.value = url
                                    currentPlayerTitle.value = title
                                    currentPlayerPoster.value = poster.ifEmpty { null }
                                    // Build player URL with metadata for progress tracking
                                    val resumePos = viewModel.getResumePosition(
                                        if (type == "series" && metaId.contains(":")) metaId else metaId
                                    )
                                    val resumeParam = if (resumePos > 0) "&resume=$resumePos" else ""
                                    navController.navigate("player/$encodedUrl?title=$encodedTitle&poster=$encodedPoster&metaId=$metaId&type=$type$resumeParam")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        // Backward compatibility: player without query params
                        composable(
                            "player/{url}",
                            arguments = listOf(
                                navArgument("url") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                            
                            PlayerScreen(
                                viewModel = viewModel,
                                streamUrl = url,
                                title = "",
                                posterUrl = null,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable(
                            "player/{url}?title={title}&poster={poster}&metaId={metaId}&type={type}&resume={resume}",
                            arguments = listOf(
                                navArgument("url") { type = NavType.StringType },
                                navArgument("title") { 
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("poster") { 
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("metaId") { 
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("type") { 
                                    type = NavType.StringType
                                    defaultValue = "movie"
                                },
                                navArgument("resume") { 
                                    type = NavType.StringType
                                    defaultValue = "0"
                                }
                            )
                        ) { backStackEntry ->
                            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val encodedPoster = backStackEntry.arguments?.getString("poster") ?: ""
                            val metaId = backStackEntry.arguments?.getString("metaId") ?: ""
                            val type = backStackEntry.arguments?.getString("type") ?: "movie"
                            val resumeStr = backStackEntry.arguments?.getString("resume") ?: "0"
                            val resumePosition = resumeStr.toLongOrNull() ?: 0L
                            
                            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                            val title = URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.toString())
                            val poster = URLDecoder.decode(encodedPoster, StandardCharsets.UTF_8.toString())
                            
                            // Parse season/episode from metaId if it's a series (format: seriesId:season:episode)
                            val season: Int? = if (type == "series" && metaId.contains(":")) {
                                metaId.split(":").getOrNull(1)?.toIntOrNull()
                            } else null
                            val episode: Int? = if (type == "series" && metaId.contains(":")) {
                                metaId.split(":").getOrNull(2)?.toIntOrNull()
                            } else null
                            val cleanMetaId = if (metaId.contains(":")) metaId.split(":")[0] else metaId
                            
                            PlayerScreen(
                                viewModel = viewModel,
                                streamUrl = url,
                                title = title,
                                posterUrl = poster.ifEmpty { null },
                                metaId = cleanMetaId.ifEmpty { null },
                                type = type,
                                season = season,
                                episode = episode,
                                resumePosition = resumePosition,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.refreshCastState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}
