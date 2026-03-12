package com.streambeam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streambeam.ui.theme.TextSecondary
import com.streambeam.utils.Constants
import com.streambeam.utils.FormatUtils
import com.streambeam.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    streamUrl: String,
    title: String,
    posterUrl: String? = null,
    metaId: String? = null,
    type: String = "movie",
    season: Int? = null,
    episode: Int? = null,
    episodeTitle: String? = null,
    resumePosition: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val isCasting by viewModel.castManager.isConnected.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Local seeking state for smooth slider interaction during cast
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    
    // Auto-hide controls after delay
    LaunchedEffect(showControls) {
        if (showControls && isReady && !isBuffering) {
            delay(Constants.Delays.CONTROLS_HIDE)
            showControls = false
        }
    }
    
    // Update progress periodically - works for both local and cast playback
    LaunchedEffect(isCasting) {
        // For casting: always update (unless user is seeking), for local: only when ready
        while (true) {
            if (isCasting) {
                // Get position from cast session
                val client = viewModel.castManager.getRemoteMediaClient()
                if (client != null) {
                    val mediaStatus = client.mediaStatus
                    if (mediaStatus != null) {
                        // Only update position if user is not dragging the slider
                        // Use approximateStreamPosition for real-time updates during playback
                        if (!isSeeking) {
                            currentPosition = client.approximateStreamPosition
                        }
                        duration = mediaStatus.mediaInfo?.streamDuration ?: 0L
                        val playerState = mediaStatus.playerState
                        isPlaying = playerState == 2 // PLAYER_STATE_PLAYING
                        isBuffering = playerState == 3 // PLAYER_STATE_BUFFERING
                        isReady = true // Cast is ready when we have media status
                    }
                }
            } else if (isReady) {
                // Get position from local player
                val player = viewModel.castManager.exoPlayer
                if (player != null) {
                    currentPosition = player.currentPosition
                    duration = player.duration.coerceAtLeast(0L)
                    isPlaying = player.isPlaying
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                }
            }
            if (duration > 0) {
                progress = currentPosition.toFloat() / duration.toFloat()
            }
            delay(Constants.Delays.POSITION_UPDATE)
        }
    }
    
    DisposableEffect(streamUrl) {
        val player = viewModel.castManager.initializePlayer()
        
        // Add listener to track player state
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        isReady = true
                        isBuffering = false
                        duration = player.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                    }
                    Player.STATE_IDLE -> {
                        isReady = false
                    }
                }
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) isBuffering = false
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
                isBuffering = false
                
                // Check for codec errors
                errorMessage = when {
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Video codec not supported on this device.\n\nThis video uses HEVC (H.265) format which requires hardware decoding not available on this emulator.\n\nTry:\n• Using a physical device\n• Casting to Chromecast\n• Selecting a different stream"
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Video decoding failed. The video format may not be supported.\n\nTry casting to Chromecast or selecting a different stream."
                    else -> "Playback error: ${error.message} (code: ${error.errorCode})"
                }
            }
        }
        
        player.addListener(listener)
        
        if (isCasting) {
            // Only start new cast if not already casting this URL
            val client = viewModel.castManager.getRemoteMediaClient()
            val currentMedia = client?.mediaInfo
            val currentUrl = currentMedia?.contentId
            
            if (currentUrl != streamUrl) {
                // New cast session needed
                viewModel.castManager.castVideo(streamUrl, title)
            } else {
                // Already casting this URL - don't restart, just observe
                android.util.Log.d(Constants.LogTags.PLAYER, "Already casting this URL, not restarting")
            }
        } else {
            viewModel.castManager.playLocally(streamUrl, title)
        }
        
        // Resume from saved position if available
        if (resumePosition > 0 && !isCasting) {
            player.seekTo(resumePosition)
        }
        
        onDispose {
            player.removeListener(listener)
            
            // Save watch progress before leaving
            if (metaId != null && duration > 0) {
                viewModel.saveWatchProgress(
                    metaId = metaId,
                    type = type,
                    name = title,
                    poster = posterUrl,
                    position = currentPosition,
                    duration = duration,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle
                )
            }
            
            // Only stop local playback when leaving
            // Casting continues in the background - user can control via persistent cast bar
            if (!isCasting) {
                player.stop()
                player.clearMediaItems()
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            // Cast button - simple IconButton that toggles cast state
                            IconButton(
                                onClick = { 
                                    activity?.let { viewModel.castManager.toggleCast(it) }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = if (isCasting) "Disconnect Cast" else "Cast",
                                    tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                }
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                // Get available audio tracks when casting
                val availableAudioTracks by viewModel.castManager.availableAudioTracks.collectAsState()
                val currentAudioTrackId by viewModel.castManager.currentAudioTrackId.collectAsState()
                
                if (isCasting) {
                    // Chromecast UI with poster, scrubber, and controls
                    CastingOverlay(
                        title = title,
                        posterUrl = posterUrl,
                        isPlaying = isPlaying,
                        currentPosition = if (isSeeking) seekPosition else currentPosition,
                        duration = duration,
                        availableAudioTracks = availableAudioTracks,
                        currentAudioTrackId = currentAudioTrackId,
                        onPlayPause = {
                            isPlaying = !isPlaying
                            if (isPlaying) viewModel.castManager.resume() 
                            else viewModel.castManager.pause()
                        },
                        onSeekForward = { viewModel.castManager.seekTo(currentPosition + Constants.Seek.FORWARD_MS) },
                        onSeekBackward = { viewModel.castManager.seekTo(currentPosition - Constants.Seek.BACKWARD_MS) },
                        onSeekTo = { position -> 
                            seekPosition = position
                            isSeeking = true
                        },
                        onSeekFinished = {
                            viewModel.castManager.seekTo(seekPosition)
                            isSeeking = false
                        },
                        onSelectAudioTrack = { trackId ->
                            viewModel.castManager.setActiveAudioTrack(trackId)
                        }
                    )
                } else {
                    // Local player with PlayerView (includes default controls)
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.castManager.exoPlayer
                                useController = true // Use default controls
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Error overlay
                    if (hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Playback Error",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
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
fun AudioTrackSelector(
    tracks: List<com.streambeam.cast.CastManager.AudioTrack>,
    currentTrackId: Long?,
    onSelectTrack: (Long) -> Unit,
    isCasting: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Find current track name
    val currentTrack = tracks.find { it.id == currentTrackId }
    val currentTrackName = currentTrack?.let { 
        "${it.name} ${if (it.isEnglish) "🇺🇸" else "(${it.language})"}"
    } ?: "Select Audio"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Audio Language (Local Only)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isCasting) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.clickable(enabled = !isCasting) { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isCasting) "$currentTrackName (Cast: Locked)" else currentTrackName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCasting) TextSecondary else Color.White
                )
                if (!isCasting) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Track options - only show when NOT casting
        if (!isCasting) {
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tracks.forEach { track ->
                        val isSelected = track.id == currentTrackId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTrack(track.id)
                                    expanded = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    if (track.isEnglish) {
                                        Text(
                                            text = "🇺🇸",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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
fun CastingOverlay(
    title: String,
    posterUrl: String? = null,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    availableAudioTracks: List<com.streambeam.cast.CastManager.AudioTrack>,
    currentAudioTrackId: Long?,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekFinished: () -> Unit = {},
    onSelectAudioTrack: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Cover Art / Poster
        if (posterUrl != null) {
            Card(
                modifier = Modifier
                    .height(220.dp)
                    .aspectRatio(2f / 3f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Fallback icon
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Playing on TV",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Cast info - clean and simple
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Casting to TV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        
        // Progress Bar (Scrubber)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (duration > 0) {
                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FormatUtils.formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = FormatUtils.formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // Seek bar
                Slider(
                    value = currentPosition.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    onValueChangeFinished = { onSeekFinished() },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Show indeterminate progress when duration unknown
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Playback controls with 15s rewind and 30s fast forward
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind 15 seconds
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay5,
                        contentDescription = "Rewind 15s",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "15s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            
            // Play/Pause
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }
            
            // Forward 30 seconds
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = "Forward 30s",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
                Text(
                    text = "30s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
