package com.streambeam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.streambeam.model.Meta
import com.streambeam.model.Video
import com.streambeam.ui.theme.TextSecondary
import com.streambeam.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVShowDetailScreen(
    viewModel: MainViewModel,
    tvShow: Meta,
    onBack: () -> Unit,
    onEpisodeClick: (String, String?) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val fullTVShow by viewModel.selectedTVShow.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Load full episode data when screen opens
    LaunchedEffect(tvShow.id) {
        viewModel.loadTVShowDetails(tvShow.id)
    }
    
    // Clear selected TV show when leaving - without setting error
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedTVShow()
        }
    }
    
    // Use full metadata if available, otherwise fall back to initial data
    val displayTVShow = fullTVShow ?: tvShow
    
    // Group episodes by season
    val seasons = remember(displayTVShow.videos) {
        displayTVShow.videos?.groupBy { it.season ?: 0 }?.toSortedMap() ?: emptyMap()
    }
    
    var selectedSeason by remember { mutableIntStateOf(seasons.keys.firstOrNull() ?: 1) }
    
    // Keep selected season in sync when seasons data loads/changes
    LaunchedEffect(seasons.keys.toList()) {
        if (selectedSeason !in seasons.keys) {
            selectedSeason = seasons.keys.firstOrNull() ?: 1
        }
    }
    
    // Show initial seasons if no full data yet
    val hasVideos = displayTVShow.videos?.isNotEmpty() == true
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayTVShow.name ?: "Unknown", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TV Show Header with poster and info
            item {
                TVShowHeader(tvShow = displayTVShow)
            }
            
            // Loading state
            if (isLoading && !hasVideos) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading episodes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            
            // Error state
            if (error != null && !hasVideos) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error ?: "Failed to load episodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Button(onClick = { 
                                viewModel.loadTVShowDetails(tvShow.id) 
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            
            // Season selector
            if (seasons.isNotEmpty()) {
                item {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(seasons.keys.toList()) { seasonNum ->
                            SeasonChip(
                                seasonNumber = seasonNum,
                                isSelected = seasonNum == selectedSeason,
                                onClick = { selectedSeason = seasonNum }
                            )
                        }
                    }
                }
            }
            
            // Episodes for selected season
            item {
                Text(
                    text = "Episodes - Season $selectedSeason",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
            
            val episodes = seasons[selectedSeason] ?: emptyList()
            
            items(episodes.sortedBy { it.episode }) { episode ->
                EpisodeCard(
                    episode = episode,
                    onClick = { 
                        // Create episode ID for stream lookup
                        val episodeId = "${displayTVShow.id}:${episode.season}:${episode.episode}"
                        onEpisodeClick(episodeId, episode.title ?: "Episode ${episode.episode}")
                    }
                )
            }
            
            // Loading state
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            
            // Empty state
            if (episodes.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No episodes available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TVShowHeader(tvShow: Meta) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Poster
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            AsyncImage(
                model = tvShow.poster,
                contentDescription = tvShow.name ?: "TV Show",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
        }
        
        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tvShow.name ?: "Unknown",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            tvShow.released?.let { year ->
                Text(
                    text = year.take(4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            tvShow.genre?.let { genres ->
                Text(
                    text = genres.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            tvShow.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SeasonChip(
    seasonNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "Season $seasonNumber",
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun EpisodeCard(
    episode: Video,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode thumbnail or number
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                episode.thumbnail?.let { thumb ->
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                } ?: run {
                    Text(
                        text = "${episode.episode}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary
                    )
                }
                
                // Play icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Episode info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show episode title - use TMDB/Cinemeta title, fallback to "Episode X"
                val rawTitle = episode.title?.trim() ?: ""
                val displayTitle = when {
                    rawTitle.isBlank() -> "Episode ${episode.episode}"
                    rawTitle.matches(Regex("^[Ee]pisode\\s*\\d+\\.?$")) -> "Episode ${episode.episode}"
                    else -> rawTitle
                }
                
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // SxE notation
                Text(
                    text = "S${episode.season ?: 1}:E${episode.episode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                episode.released?.let { date ->
                    // Parse ISO date format (e.g., "2024-01-15T06:00:00.000Z" → "2024-01-15")
                    val cleanDate = date.take(10)
                    Text(
                        text = cleanDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                episode.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
