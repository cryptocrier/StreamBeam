package com.streambeam.ui.state

import com.streambeam.cast.CastManager

/**
 * Sealed class representing all possible player states.
 * This replaces multiple boolean flags with a type-safe state machine.
 */
sealed class PlayerState {
    abstract val position: Long
    abstract val duration: Long
    
    data class Idle(
        override val position: Long = 0L,
        override val duration: Long = 0L
    ) : PlayerState()
    
    data class Buffering(
        override val position: Long = 0L,
        override val duration: Long = 0L,
        val progress: Float = 0f
    ) : PlayerState()
    
    data class Playing(
        override val position: Long,
        override val duration: Long,
        val isCasting: Boolean = false
    ) : PlayerState()
    
    data class Paused(
        override val position: Long,
        override val duration: Long,
        val isCasting: Boolean = false
    ) : PlayerState()
    
    data class Error(
        override val position: Long = 0L,
        override val duration: Long = 0L,
        val message: String,
        val isRecoverable: Boolean = true
    ) : PlayerState()
    
    /**
     * Convenience property to check if playback is active
     */
    val isPlaying: Boolean
        get() = this is Playing
    
    /**
     * Convenience property to check if buffering
     */
    val isBuffering: Boolean
        get() = this is Buffering
    
    /**
     * Convenience property to check if in error state
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Convenience property to check if ready for interaction
     */
    val isReady: Boolean
        get() = this is Playing || this is Paused
}

/**
 * Data class representing the complete UI state for the player screen.
 * This consolidates all UI-related state into a single immutable object.
 */
data class PlayerUiState(
    val playerState: PlayerState = PlayerState.Idle(),
    val title: String = "",
    val posterUrl: String? = null,
    val showControls: Boolean = true,
    val availableAudioTracks: List<CastManager.AudioTrack> = emptyList(),
    val currentAudioTrackId: Long? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null
) {
    /**
     * Progress as a float between 0 and 1
     */
    val progress: Float
        get() = if (playerState.duration > 0) {
            playerState.position.toFloat() / playerState.duration.toFloat()
        } else 0f
    
    /**
     * Formatted position string (e.g., "1:30:45")
     */
    val positionText: String
        get() = formatDuration(playerState.position)
    
    /**
     * Formatted duration string
     */
    val durationText: String
        get() = formatDuration(playerState.duration)
    
    companion object {
        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "0:00"
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * Sealed class representing cast connection states
 */
sealed class CastConnectionState {
    object Disconnected : CastConnectionState()
    data class Connecting(val deviceName: String? = null) : CastConnectionState()
    data class Connected(val deviceName: String) : CastConnectionState()
    data class Error(val message: String) : CastConnectionState()
}

/**
 * Sealed class representing stream loading states
 */
sealed class StreamLoadingState {
    object Idle : StreamLoadingState()
    object Loading : StreamLoadingState()
    data class Success(val streams: Map<String, List<com.streambeam.model.Stream>>) : StreamLoadingState()
    data class Error(val message: String) : StreamLoadingState()
}
