package com.streambeam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.streambeam.model.Meta
import com.streambeam.model.WatchProgress
import com.streambeam.ui.theme.TextSecondary
import com.streambeam.viewmodel.MainViewModel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToStreams: (String, String, String, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecentlyWatched: (WatchProgress) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val movies by viewModel.movies.collectAsState()
    val tvShows by viewModel.tvShows.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentContentType by viewModel.currentContentType.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    // Debounce search - 400ms for responsive but not excessive
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.clearSearch()
            isSearchActive = false
        } else if (searchQuery.length >= 2) { // Only search for 2+ characters
            delay(400)
            isSearchActive = true
            viewModel.search(searchQuery)
        }
    }
    
    // Determine what content to show
    val displayItems = when {
        isSearchActive -> searchResults
        currentContentType == MainViewModel.ContentType.TV_SHOWS -> tvShows
        else -> movies
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StreamBeam",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    // Cast button - simple IconButton that toggles cast state
                    val isCasting by viewModel.castManager.isConnected.collectAsState()
                    IconButton(
                        onClick = { 
                            activity?.let { viewModel.castManager.toggleCast(it) }
                        }
                    ) {
                        Icon(
                            imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = if (isCasting) "Disconnect Cast" else "Cast",
                            tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()
        
        // Track if carousel is visible based on scroll
        val firstVisibleItemIndex by remember {
            derivedStateOf { lazyListState.firstVisibleItemIndex }
        }
        val firstVisibleItemScrollOffset by remember {
            derivedStateOf { lazyListState.firstVisibleItemScrollOffset }
        }
        
        // Determine carousel visibility
        val isCarouselVisible = firstVisibleItemIndex == 0
        
        // Snap to hide carousel when scrolling past threshold (only after user interaction)
        var hasUserScrolled by remember { mutableStateOf(false) }
        
        LaunchedEffect(firstVisibleItemIndex, firstVisibleItemScrollOffset) {
            // Only snap after user has actively scrolled (not on initial load)
            if (hasUserScrolled && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset > 300) {
                lazyListState.animateScrollToItem(1)
            }
            // Mark as user scrolled once we detect any scroll
            if (firstVisibleItemScrollOffset > 50) {
                hasUserScrolled = true
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                item {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                // Continue Watching Carousel (with peek when scrolled)
                item {
                    ContinueWatchingCarousel(
                        viewModel = viewModel,
                        onItemClick = onNavigateToRecentlyWatched,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Category Chips (sticky)
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = if (!isCarouselVisible) 4.dp else 0.dp
                    ) {
                        CategoryRow(
                            categories = listOf("Movies", "TV Shows"),
                            selectedCategory = when (currentContentType) {
                                MainViewModel.ContentType.MOVIES -> "Movies"
                                MainViewModel.ContentType.TV_SHOWS -> "TV Shows"
                                else -> "Movies"
                            },
                            onCategorySelected = { category ->
                                isSearchActive = false
                                searchQuery = ""
                                viewModel.clearSearch()
                                when (category) {
                                    "Movies" -> viewModel.setContentType(MainViewModel.ContentType.MOVIES)
                                    "TV Shows" -> viewModel.setContentType(MainViewModel.ContentType.TV_SHOWS)
                                }
                            },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                
                // Content
                when {
                    isLoading || isSearching -> {
                        item { LoadingGrid() }
                    }
                    error != null && !isSearchActive -> {
                        item {
                            ErrorState(
                                message = error ?: "Something went wrong",
                                onRetry = { 
                                    when (currentContentType) {
                                        MainViewModel.ContentType.MOVIES -> viewModel.loadMovies()
                                        MainViewModel.ContentType.TV_SHOWS -> viewModel.loadTVShows()
                                    }
                                }
                            )
                        }
                    }
                    displayItems.isEmpty() -> {
                        item {
                            EmptyState(
                                message = when {
                                    isSearchActive -> "No results found for \"$searchQuery\""
                                    currentContentType == MainViewModel.ContentType.TV_SHOWS -> "No TV shows available"
                                    else -> "No movies available"
                                }
                            )
                        }
                    }
                    else -> {
                        // Grid layout using FlowRow within LazyColumn item
                        item {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                maxItemsInEachRow = 3
                            ) {
                                displayItems.forEach { item ->
                                    MovieCard(
                                        movie = item,
                                        onClick = {
                                            onNavigateToStreams(item.id, item.type, item.name, item.poster)
                                        },
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                "Search movies & TV shows...",
                color = TextSecondary
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

@Composable
fun CategoryRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onCategorySelected(category) }
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: Meta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Poster Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                AsyncImage(
                    model = movie.poster,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                    error = painterResource(android.R.drawable.ic_menu_gallery)
                )
                
                // Gradient overlay at bottom for better text contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
                
                // Year badge if available
                movie.released?.take(4)?.let { year ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = year,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Title
            Text(
                text = movie.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoadingGrid() {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 3
    ) {
        repeat(12) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                LoadingCard()
            }
        }
    }
}

@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(
                        Brush.shimmer(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .padding(12.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Try Again",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Movie,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// Shimmer brush for loading states
private fun Brush.Companion.shimmer(colors: List<Color>): Brush {
    return linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingCarousel(
    viewModel: MainViewModel,
    onItemClick: (WatchProgress) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // Filter out completed items (>= 90% watched)
    val unfinishedItems = watchHistory.filter { !it.isCompleted && it.getProgressPercent() < 90 }
    
    // Selection state for multi-select removal
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    
    // Reset selection when items change or leaving selection mode
    LaunchedEffect(unfinishedItems.size, selectionMode) {
        if (!selectionMode) {
            selectedItems = emptySet()
        }
    }
    
    if (unfinishedItems.isEmpty()) {
        return // Don't show section if empty
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Header with title and delete button (shown in selection mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (selectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cancel button
                    TextButton(
                        onClick = { 
                            selectionMode = false
                            selectedItems = emptySet()
                        }
                    ) {
                        Text("Cancel")
                    }
                    
                    // Delete button (only if items selected)
                    if (selectedItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedItems.forEach { id ->
                                    viewModel.removeFromWatchHistory(id)
                                }
                                selectionMode = false
                                selectedItems = emptySet()
                            }
                        ) {
                            Text(
                                "Delete (${selectedItems.size})",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        
        // Horizontal carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = unfinishedItems,
                key = { it.id }
            ) { item ->
                val isSelected = selectedItems.contains(item.id)
                
                ContinueWatchingCard(
                    item = item,
                    isSelected = isSelected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            selectedItems = if (isSelected) {
                                selectedItems - item.id
                            } else {
                                selectedItems + item.id
                            }
                        } else {
                            onItemClick(item)
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!selectionMode) {
                            selectionMode = true
                            selectedItems = setOf(item.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: WatchProgress,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.6f else 1f,
        label = "selectionAlpha"
    )
    
    Column(
        modifier = Modifier
            .width(140.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .alpha(selectionAlpha)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            // Poster - use regular AsyncImage to avoid SubcomposeLayout issues
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
            
            // Selection overlay
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else 
                                Color.Black.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
            
            // Progress indicator at bottom
            LinearProgressIndicator(
                progress = item.getProgressPercent() / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
        
        // Title
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Episode info for TV shows
        item.getEpisodeDisplay()?.let { episodeInfo ->
            Text(
                text = episodeInfo,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Progress text
        Text(
            text = "${item.getProgressPercent()}%",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
fun WatchHistoryCard(
    item: WatchProgress,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                // Poster - use regular AsyncImage to avoid SubcomposeLayout issues
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                    error = painterResource(android.R.drawable.ic_menu_gallery)
                )
                
                // Progress indicator
                if (!item.isCompleted && item.getProgressPercent() > 0) {
                    LinearProgressIndicator(
                        progress = item.getProgressPercent() / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                // Completed badge
                if (item.isCompleted) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // Resume badge
                if (!item.isCompleted && item.getProgressPercent() > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${item.getProgressPercent()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Title
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Episode info for TV shows
            item.getEpisodeDisplay()?.let { episodeInfo ->
                Text(
                    text = episodeInfo,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Progress text
            Text(
                text = if (item.isCompleted) "Completed" else item.getFormattedProgress(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = TextSecondary
            )
        }
    }
}
