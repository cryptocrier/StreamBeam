package com.stremioclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stremioclone.cast.CastManager
import com.stremioclone.ui.state.CastConnectionState
import com.stremioclone.ui.state.PlayerState
import com.stremioclone.ui.state.PlayerUiState
import com.stremioclone.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel for the Player screen.
 * Manages all player-related state using a single immutable UI state object.
 */
class PlayerViewModel(
    private val castManager: CastManager
) : ViewModel() {
    
    // Single source of truth for all UI state
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Track position update job
    private var positionUpdateJob: Job? = null
    
    // Local seeking state
    private var isSeeking = false
    private var seekPosition = 0L
    
    init {
        // Start observing cast state
        viewModelScope.launch {
            castManager.isConnected.collect { isConnected ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isCasting = isConnected,
                        castDeviceName = if (isConnected) {
                            castManager.getConnectedDeviceInfo().lines().firstOrNull()?.removePrefix("Device: ")
                        } else null
                    )
                }
            }
        }
        
        // Start observing audio tracks
        viewModelScope.launch {
            castManager.availableAudioTracks.collect { tracks ->
                _uiState.update { it.copy(availableAudioTracks = tracks) }
            }
        }
        
        // Start observing current audio track
        viewModelScope.launch {
            castManager.currentAudioTrackId.collect { trackId ->
                _uiState.update { it.copy(currentAudioTrackId = trackId) }
            }
        }
    }
    
    /**
     * Initialize playback with the given stream
     */
    fun initializePlayback(streamUrl: String, title: String, posterUrl: String?) {
        _uiState.update {
            it.copy(
                title = title,
                posterUrl = posterUrl,
                playerState = PlayerState.Buffering()
            )
        }
        
        if (castManager.isConnected.value) {
            // Check if already casting this URL
            val client = castManager.getRemoteMediaClient()
            val currentUrl = client?.mediaInfo?.contentId
            
            if (currentUrl != streamUrl) {
                castManager.castVideo(streamUrl, title)
            }
        } else {
            castManager.playLocally(streamUrl, title)
        }
        
        // Start position updates
        startPositionUpdates()
    }
    
    /**
     * Start periodic position updates
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                updatePosition()
                delay(Constants.Delays.POSITION_UPDATE)
            }
        }
    }
    
    /**
     * Update position from current playback source
     */
    private fun updatePosition() {
        val isCasting = castManager.isConnected.value
        
        if (isCasting) {
            val client = castManager.getRemoteMediaClient()
            val mediaStatus = client?.mediaStatus
            
            if (mediaStatus != null) {
                val position = if (isSeeking) seekPosition else client.approximateStreamPosition
                val duration = mediaStatus.mediaInfo?.streamDuration ?: 0L
                val playerState = mediaStatus.playerState
                
                val newState = when (playerState) {
                    2 -> PlayerState.Playing(position, duration, true) // PLAYING
                    3 -> PlayerState.Buffering(position, duration)      // BUFFERING
                    4 -> PlayerState.Paused(position, duration, true)   // PAUSED
                    else -> PlayerState.Idle(position, duration)
                }
                
                _uiState.update { it.copy(playerState = newState) }
            }
        } else {
            // Local player
            val player = castManager.exoPlayer
            if (player != null) {
                val position = if (isSeeking) seekPosition else player.currentPosition
                val duration = player.duration.coerceAtLeast(0L)
                
                val newState = when (player.playbackState) {
                    androidx.media3.common.Player.STATE_READY -> {
                        if (player.isPlaying) {
                            PlayerState.Playing(position, duration, false)
                        } else {
                            PlayerState.Paused(position, duration, false)
                        }
                    }
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        PlayerState.Buffering(position, duration)
                    }
                    else -> PlayerState.Idle(position, duration)
                }
                
                _uiState.update { it.copy(playerState = newState) }
            }
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        when (_uiState.value.playerState) {
            is PlayerState.Playing -> castManager.pause()
            is PlayerState.Paused -> castManager.resume()
            else -> { /* Do nothing */ }
        }
    }
    
    /**
     * Seek to a specific position
     */
    fun seekTo(position: Long) {
        isSeeking = true
        seekPosition = position
        
        // Update UI immediately for responsiveness
        _uiState.update { currentState ->
            val newPlayerState = when (currentState.playerState) {
                is PlayerState.Playing -> currentState.playerState.copy(position = position)
                is PlayerState.Paused -> currentState.playerState.copy(position = position)
                is PlayerState.Buffering -> currentState.playerState.copy(position = position)
                else -> currentState.playerState
            }
            currentState.copy(playerState = newPlayerState)
        }
    }
    
    /**
     * Called when seek gesture is finished
     */
    fun onSeekFinished() {
        castManager.seekTo(seekPosition)
        
        viewModelScope.launch {
            delay(Constants.Delays.SEEK_DEBOUNCE)
            isSeeking = false
        }
    }
    
    /**
     * Seek forward by predefined amount
     */
    fun seekForward() {
        val currentPosition = _uiState.value.playerState.position
        seekTo(currentPosition + Constants.Seek.FORWARD_MS)
        onSeekFinished()
    }
    
    /**
     * Seek backward by predefined amount
     */
    fun seekBackward() {
        val currentPosition = _uiState.value.playerState.position
        seekTo((currentPosition - Constants.Seek.BACKWARD_MS).coerceAtLeast(0))
        onSeekFinished()
    }
    
    /**
     * Set controls visibility
     */
    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(showControls = visible) }
    }
    
    /**
     * Select audio track
     */
    fun selectAudioTrack(trackId: Long) {
        castManager.setActiveAudioTrack(trackId)
    }
    
    /**
     * Toggle cast connection
     */
    fun toggleCast(activity: android.app.Activity): Boolean {
        return castManager.toggleCast(activity)
    }
    
    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
    }
}
