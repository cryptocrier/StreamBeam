package com.stremioclone.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stremioclone.model.Stream
import com.stremioclone.ui.theme.AccentBlue
import com.stremioclone.ui.theme.AccentGreen
import androidx.compose.ui.text.style.TextAlign
import com.stremioclone.ui.theme.Quality1080p
import com.stremioclone.ui.theme.Quality4K
import com.stremioclone.ui.theme.Quality720p
import com.stremioclone.ui.theme.QualitySD
import com.stremioclone.ui.theme.TextSecondary
import com.stremioclone.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSelectionScreen(
    viewModel: MainViewModel,
    metaId: String,
    type: String,
    title: String,
    posterUrl: String? = null,
    onNavigateToPlayer: (String) -> Unit,
    onBack: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val groupedStreams by viewModel.groupedStreams.collectAsState()
    val error by viewModel.error.collectAsState()
    val realDebridKey by viewModel.realDebridKey.collectAsState()
    val preferredLanguages by viewModel.preferredLanguages.collectAsState()
    val isRealDebridConfigured = realDebridKey.isNotBlank()
    
    LaunchedEffect(metaId) {
        viewModel.loadStreams(metaId, type)
    }
    
    // Calculate total streams for header
    val totalStreams = remember(groupedStreams) {
        groupedStreams.values.sumOf { it.size }
    }
    
    // Get all streams with MULTI audio for special handling
    val allStreams = remember(groupedStreams) {
        groupedStreams.values.flatten()
    }
    val multiAudioStreams = remember(allStreams) {
        allStreams.filter { it.isMultiAudio() }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$totalStreams streams from ${groupedStreams.size} source${if (groupedStreams.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> LoadingStreamsList()
                error != null -> StreamErrorState(
                    message = error ?: "Failed to load streams",
                    onRetry = { viewModel.loadStreams(metaId, type) }
                )
                groupedStreams.isEmpty() -> EmptyStreamsState()
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // Real-Debrid warning
                            if (!isRealDebridConfigured) {
                                RealDebridWarningBanner(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                // Info banner
                                InfoBanner(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        
                        // Language filter chips
                        item {
                            LanguageFilterChips(
                                selectedLanguages = preferredLanguages,
                                onLanguageToggle = { lang ->
                                    viewModel.togglePreferredLanguage(lang)
                                    // Reload streams with new language filter
                                    viewModel.loadStreams(metaId, type)
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        // MULTI audio info banner
                        if (multiAudioStreams.isNotEmpty()) {
                            item {
                                MultiAudioInfoBanner(
                                    count = multiAudioStreams.size,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        
                        // Grouped streams by quality folder
                        groupedStreams.forEach { (quality, streams) ->
                            // Section header for each quality folder
                            item(key = "header_$quality") {
                                QualityFolderHeader(
                                    quality = quality,
                                    streamCount = streams.size,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            
                            // Streams for this quality
                            items(
                                items = streams,
                                key = { "${quality}_${it.hashCode()}" }
                            ) { stream ->
                                StreamCard(
                                    stream = stream,
                                    onClick = {
                                        // Store detected audio languages for CastManager
                                        val languages = stream.getAudioLanguages()
                                        viewModel.castManager.setDetectedAudioLanguages(languages)
                                        
                                        val directUrl = stream.url
                                        if (directUrl != null) {
                                            onNavigateToPlayer(directUrl)
                                        } else if (stream.infoHash != null) {
                                            viewModel.processTorrentAndPlay(
                                                infoHash = stream.infoHash,
                                                title = title,
                                                onUrlReady = { processedUrl ->
                                                    onNavigateToPlayer(processedUrl)
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QualityFolderHeader(
    quality: String,
    streamCount: Int,
    modifier: Modifier = Modifier
) {
    val (icon, label, color) = when (quality) {
        "4K HDR" -> Triple("✨", "4K HDR", Color(0xFFFFD700)) // Gold
        "4K" -> Triple("🎬", "4K", Color(0xFF00BCD4)) // Cyan
        "1080p" -> Triple("📺", "1080p Full HD", Color(0xFF4CAF50)) // Green
        "720p" -> Triple("📼", "720p HD", Color(0xFF2196F3)) // Blue
        "SD" -> Triple("📹", "SD", Color(0xFF9E9E9E)) // Gray
        else -> Triple("📡", quality, MaterialTheme.colorScheme.secondary)
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    // Show source breakdown if available
                    Text(
                        text = "$streamCount streams",
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.7f)
                    )
                }
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "$streamCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageFilterChips(
    selectedLanguages: Set<String>,
    onLanguageToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val languages = listOf(
        "en" to "🇺🇸 EN",
        "es" to "🇪🇸 ES",
        "fr" to "🇫🇷 FR",
        "de" to "🇩🇪 DE",
        "it" to "🇮🇹 IT",
        "pt" to "🇵🇹 PT",
        "ru" to "🇷🇺 RU",
        "ja" to "🇯🇵 JA",
        "ko" to "🇰🇷 KO",
        "zh" to "🇨🇳 ZH"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Audio Languages (tap to filter)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Show selected languages as chips
        val displayLanguages = if (expanded) languages else languages.take(5)
        
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayLanguages.forEach { (code, label) ->
                val isSelected = selectedLanguages.contains(code)
                LanguageChip(
                    label = label,
                    isSelected = isSelected,
                    onClick = { onLanguageToggle(code) }
                )
            }
            
            // More/Less button
            if (languages.size > 5) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Text(
                        text = if (expanded) "Less ↑" else "More ↓",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.primary
            )
        else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun InfoBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Select a stream to play",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Higher quality streams may take longer to load",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun RealDebridWarningBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Real-Debrid Not Configured",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Torrent streams require Real-Debrid. Go to Settings to add your API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun StreamCard(
    stream: Stream,
    onClick: () -> Unit
) {
    val fullName = stream.name ?: "Unknown Source"
    val title = stream.title ?: ""
    val quality = extractQuality(title)
    val size = extractSize(title)
    val seeds = extractSeeders(title)
    val isMulti = stream.isMultiAudio()
    val languages = if (isMulti) stream.getAudioLanguages() else emptyList()
    
    // Extract addon source from name (format: "Name [Addon]")
    val sourceRegex = Regex("\\s*\\[(.+?)\\]\\s*$")
    val addonSource = sourceRegex.find(fullName)?.groupValues?.get(1) ?: "Unknown"
    val displayName = fullName.replace(sourceRegex, "")
    
    // Determine source color/icon
    val (sourceIcon, sourceColor) = when {
        addonSource.contains("Torrentio", ignoreCase = true) -> 
            Pair("⬇️", MaterialTheme.colorScheme.primary)
        addonSource.contains("Comet", ignoreCase = true) -> 
            Pair("☄️", MaterialTheme.colorScheme.tertiary)
        else -> 
            Pair("📡", MaterialTheme.colorScheme.secondary)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play icon with background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Stream info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Source name with addon badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Addon source badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sourceColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = sourceIcon,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = addonSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Quality and size row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quality badge
                    QualityBadge(quality = quality)
                    
                    // MULTI badge
                    if (isMulti) {
                        MultiBadge()
                    }
                    
                    // Size
                    if (size.isNotEmpty()) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    // Seeders
                    if (seeds > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            seeds > 100 -> AccentGreen
                                            seeds > 20 -> AccentBlue
                                            else -> Color.Yellow
                                        }
                                    )
                            )
                            Text(
                                text = "$seeds peers",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // Filename if available
                stream.behaviorHints?.filename?.let { filename ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun QualityBadge(quality: String) {
    val (backgroundColor, textColor, icon) = when (quality.uppercase()) {
        "4K", "2160P" -> Triple(
            Quality4K.copy(alpha = 0.2f),
            Quality4K,
            Icons.Default.PlayArrow
        )
        "1080P", "1080I" -> Triple(
            Quality1080p.copy(alpha = 0.2f),
            Quality1080p,
            Icons.Default.PlayArrow
        )
        "720P", "720I" -> Triple(
            Quality720p.copy(alpha = 0.2f),
            Quality720p,
            Icons.Default.Warning
        )
        else -> Triple(
            QualitySD.copy(alpha = 0.2f),
            QualitySD,
            Icons.Default.Warning
        )
    }
    
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quality.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MultiBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    ) {
        Text(
            text = "MULTI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MultiAudioInfoBanner(
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$count streams with Multi-Language Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Select audio language in the player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun LoadingStreamsList() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(6) {
            LoadingStreamCard()
        }
    }
}

@Composable
fun LoadingStreamCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun StreamErrorState(
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
            text = "Failed to Load Streams",
            style = MaterialTheme.typography.headlineSmall,
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
fun EmptyStreamsState() {
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
            tint = TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Streams Available",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No streaming sources found for this content. Try again later or check a different title.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// Helper functions
private fun extractQuality(title: String): String {
    val patterns = listOf(
        Regex("(4K|2160p)", RegexOption.IGNORE_CASE),
        Regex("(1080p|1080i)", RegexOption.IGNORE_CASE),
        Regex("(720p|720i)", RegexOption.IGNORE_CASE),
        Regex("(480p|480i|SD)", RegexOption.IGNORE_CASE)
    )
    
    for (pattern in patterns) {
        pattern.find(title)?.let { return it.value }
    }
    return "SD"
}

private fun extractQualityScore(title: String?): Int {
    if (title == null) return 0
    return when {
        title.contains("4K", ignoreCase = true) || 
        title.contains("2160p", ignoreCase = true) -> 4
        title.contains("1080p", ignoreCase = true) || 
        title.contains("1080i", ignoreCase = true) -> 3
        title.contains("720p", ignoreCase = true) || 
        title.contains("720i", ignoreCase = true) -> 2
        else -> 1
    }
}

private fun extractSize(title: String): String {
    val pattern = Regex("""\d+\.?\d*\s*(GB|MB|TB)""", RegexOption.IGNORE_CASE)
    return pattern.find(title)?.value?.uppercase() ?: ""
}

private fun extractSeeders(title: String): Int {
    val pattern = Regex("👤\\s*(\\d+)")
    return pattern.find(title)?.groupValues?.get(1)?.toIntOrNull() 
        ?: Regex("(\\d+)\\s*seeders?", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.get(1)?.toIntOrNull() 
        ?: 0
}
