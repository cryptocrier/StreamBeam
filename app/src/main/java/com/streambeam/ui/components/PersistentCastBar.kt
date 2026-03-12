package com.streambeam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.streambeam.cast.CastManager
import com.streambeam.ui.theme.TextSecondary
import com.streambeam.utils.Constants
import com.streambeam.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PersistentCastBar(
    castManager: CastManager,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCastingActive by castManager.isCastingActive.collectAsState()
    val isConnected by castManager.isConnected.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    
    // Show bar when casting is active and connected
    AnimatedVisibility(
        visible = isCastingActive && isConnected,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        CastBarContent(
            castManager = castManager,
            isExpanded = isExpanded,
            onToggleExpand = { isExpanded = !isExpanded },
            onNavigateToPlayer = onNavigateToPlayer
        )
    }
}

@Composable
private fun CastBarContent(
    castManager: CastManager,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Casting...") }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    
    // Function to refresh cast state from remote
    fun refreshCastState() {
        val client = castManager.getRemoteMediaClient()
        if (client != null) {
            val mediaStatus = client.mediaStatus
            if (mediaStatus != null) {
                // Always update duration and playing state
                duration = mediaStatus.mediaInfo?.streamDuration ?: 0L
                isPlaying = mediaStatus.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING
                
                // Only update position if not currently seeking (user dragging)
                // Use approximateStreamPosition for real-time updates during playback
                if (!isSeeking) {
                    currentPosition = client.approximateStreamPosition
                }
                
                val mediaInfo = client.mediaInfo
                if (mediaInfo != null) {
                    title = mediaInfo.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE) 
                        ?: "Casting..."
                }
            }
        }
    }
    
    // Refresh immediately when composable enters composition
    LaunchedEffect(Unit) {
        refreshCastState()
    }
    
    // Listen to lifecycle events to refresh when app resumes
    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.d(Constants.LogTags.CAST_BAR, "App resumed - refreshing cast state")
                refreshCastState()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    
    // Update playback state periodically - use shorter delay for smoother updates
    LaunchedEffect(Unit) {
        while (true) {
            refreshCastState()
            delay(Constants.Delays.POSITION_UPDATE)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main bar content - clicking center opens full player
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToPlayer() }
                ) {
                    // Cast icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Title and status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isPlaying) "Playing on TV" else "Paused on TV",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Play/Pause button (quick action - doesn't navigate)
                IconButton(
                    onClick = {
                        if (isPlaying) castManager.pause() else castManager.resume()
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Expand/Collapse button - toggles expanded view with scrub bar
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSecondary
                    )
                }
            }
            
            // Expanded content - shows scrub bar when expanded
            if (isExpanded && duration > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Seek slider
                    val sliderValue = if (isSeeking) seekPosition.toFloat() else currentPosition.toFloat()
                    androidx.compose.material3.Slider(
                        value = sliderValue.coerceIn(0f, duration.toFloat()),
                        onValueChange = { newValue ->
                            isSeeking = true
                            seekPosition = newValue.toLong()
                        },
                        onValueChangeFinished = {
                            android.util.Log.d(Constants.LogTags.CAST_BAR, "Seeking to $seekPosition")
                            castManager.seekTo(seekPosition)
                            // Small delay before resuming normal updates
                            coroutineScope.launch {
                                delay(Constants.Delays.SEEK_DEBOUNCE)
                                isSeeking = false
                            }
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    
                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(if (isSeeking) seekPosition else currentPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSeeking) MaterialTheme.colorScheme.primary else TextSecondary
                        )
                        Text(
                            text = FormatUtils.formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Mini progress bar (when not expanded)
                if (duration > 0) {
                    val progress = currentPosition.toFloat() / duration.toFloat()
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
