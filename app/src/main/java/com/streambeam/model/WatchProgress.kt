package com.streambeam.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the watch progress for a movie or TV episode
 */
data class WatchProgress(
    @SerializedName("id") val id: String,                    // Unique watch entry ID
    @SerializedName("metaId") val metaId: String,            // IMDB/tmdb ID of the content
    @SerializedName("type") val type: String,                // "movie" or "series"
    @SerializedName("name") val name: String,                // Title
    @SerializedName("poster") val poster: String?,           // Poster URL
    @SerializedName("season") val season: Int?,              // For TV shows
    @SerializedName("episode") val episode: Int?,            // For TV shows
    @SerializedName("episodeTitle") val episodeTitle: String?, // For TV shows
    @SerializedName("position") val position: Long,          // Current position in milliseconds
    @SerializedName("duration") val duration: Long,          // Total duration in milliseconds
    @SerializedName("lastWatched") val lastWatched: Long,    // Timestamp
    @SerializedName("isCompleted") val isCompleted: Boolean = false // Whether finished
) {
    /**
     * Returns progress percentage (0-100)
     */
    fun getProgressPercent(): Int {
        if (duration <= 0) return 0
        return ((position.toFloat() / duration) * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Returns formatted position (e.g., "45:30 / 1:30:00")
     */
    fun getFormattedProgress(): String {
        return "${formatTime(position)} / ${formatTime(duration)}"
    }
    
    /**
     * Returns display title including episode info for TV shows
     */
    fun getDisplayTitle(): String {
        return if (type == "series" && season != null && episode != null) {
            "$name - S${season}:E${episode}${episodeTitle?.let { " - $it" } ?: ""}"
        } else {
            name
        }
    }
    
    /**
     * Returns short display text for episode
     */
    fun getEpisodeDisplay(): String? {
        return if (type == "series" && season != null && episode != null) {
            "S${season}:E${episode}${episodeTitle?.let { " - $it" } ?: ""}"
        } else null
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        val remainingSeconds = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
        } else {
            String.format("%d:%02d", remainingMinutes, remainingSeconds)
        }
    }
    
    companion object {
        /**
         * Create a unique ID for the watch entry
         */
        fun createId(metaId: String, season: Int? = null, episode: Int? = null): String {
            return if (season != null && episode != null) {
                "$metaId:S${season}:E${episode}"
            } else {
                metaId
            }
        }
    }
}

/**
 * Wrapper for serializing list of WatchProgress to JSON
 */
data class WatchHistory(
    @SerializedName("items") val items: List<WatchProgress> = emptyList()
)
