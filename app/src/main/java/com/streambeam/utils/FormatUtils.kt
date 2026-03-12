package com.streambeam.utils

import com.streambeam.model.Stream

/**
 * Utility functions for formatting and data processing.
 * All formatting logic should be centralized here.
 */
object FormatUtils {
    
    /**
     * Format milliseconds to human-readable duration string.
     * Examples: "1:30", "1:45:30", "45:30"
     */
    fun formatDuration(ms: Long): String {
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
    
    /**
     * Format duration for display in compact form.
     * Examples: "1h 30m", "45m", "2h"
     */
    fun formatDurationCompact(ms: Long): String {
        if (ms <= 0) return "0m"
        
        val totalMinutes = ms / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    
    /**
     * Extract quality category from stream title.
     * Returns one of: "4K HDR", "4K", "1080p", "720p", "SD", "Unknown"
     */
    fun extractQualityCategory(title: String?): String {
        if (title == null) return Constants.Quality.UNKNOWN
        
        val titleUpper = title.uppercase()
        
        return when {
            // 4K with HDR/Dolby Vision
            (titleUpper.contains("2160P") || titleUpper.contains("4K")) && 
            (titleUpper.contains("HDR") || titleUpper.contains("DV") || 
             titleUpper.contains("DOVI") || titleUpper.contains("DOLBY VISION")) -> Constants.Quality.HDR
            
            // Regular 4K
            titleUpper.contains("2160P") || titleUpper.contains("4K") -> Constants.Quality.UHD
            
            // 1080p
            titleUpper.contains("1080P") || titleUpper.contains("1080I") -> Constants.Quality.FHD
            
            // 720p
            titleUpper.contains("720P") || titleUpper.contains("720I") -> Constants.Quality.HD
            
            // SD
            titleUpper.contains("480P") || titleUpper.contains("360P") || 
            titleUpper.contains("SD") -> Constants.Quality.SD
            
            else -> Constants.Quality.UNKNOWN
        }
    }
    
    /**
     * Extract quality string from title for display badges.
     */
    fun extractQuality(title: String?): String {
        if (title == null) return Constants.Quality.SD
        
        return when {
            title.contains("4K", ignoreCase = true) || 
            title.contains("2160p", ignoreCase = true) -> Constants.Quality.UHD
            
            title.contains("1080p", ignoreCase = true) || 
            title.contains("1080i", ignoreCase = true) -> Constants.Quality.FHD
            
            title.contains("720p", ignoreCase = true) || 
            title.contains("720i", ignoreCase = true) -> Constants.Quality.HD
            
            else -> Constants.Quality.SD
        }
    }
    
    /**
     * Extract seed count from stream title.
     */
    fun extractSeeders(title: String?): Int {
        if (title == null) return 0
        
        // Look for patterns like "👤 123" or "123 seeders"
        val patterns = listOf(
            Regex("👤\\s*(\\d+)"),
            Regex("(\\d+)\\s*seeders?", RegexOption.IGNORE_CASE),
            Regex("☑?\\s*(\\d+)\\s*👤")
        )
        
        for (pattern in patterns) {
            pattern.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        
        return 0
    }
    
    /**
     * Extract file size from title.
     */
    fun extractSize(title: String?): String {
        if (title == null) return ""
        
        val pattern = Regex("""\d+\.?\d*\s*(GB|MB|TB)""", RegexOption.IGNORE_CASE)
        return pattern.find(title)?.value?.uppercase() ?: ""
    }
    
    /**
     * Get language display name with flag emoji.
     */
    fun getLanguageDisplayName(code: String?): String {
        return when (code) {
            Constants.Languages.ENGLISH -> "🇺🇸 English"
            Constants.Languages.SPANISH -> "🇪🇸 Spanish"
            Constants.Languages.FRENCH -> "🇫🇷 French"
            Constants.Languages.GERMAN -> "🇩🇪 German"
            Constants.Languages.ITALIAN -> "🇮🇹 Italian"
            Constants.Languages.PORTUGUESE -> "🇵🇹 Portuguese"
            Constants.Languages.RUSSIAN -> "🇷🇺 Russian"
            Constants.Languages.JAPANESE -> "🇯🇵 Japanese"
            Constants.Languages.KOREAN -> "🇰🇷 Korean"
            Constants.Languages.CHINESE -> "🇨🇳 Chinese"
            Constants.Languages.HINDI -> "🇮🇳 Hindi"
            Constants.Languages.POLISH -> "🇵🇱 Polish"
            Constants.Languages.DUTCH -> "🇳🇱 Dutch"
            Constants.Languages.TURKISH -> "🇹🇷 Turkish"
            Constants.Languages.ARABIC -> "🇸🇦 Arabic"
            else -> "🌐 Unknown"
        }
    }
    
    /**
     * Get language flag emoji only.
     */
    fun getLanguageFlag(code: String?): String {
        return when (code) {
            Constants.Languages.ENGLISH -> "🇺🇸"
            Constants.Languages.SPANISH -> "🇪🇸"
            Constants.Languages.FRENCH -> "🇫🇷"
            Constants.Languages.GERMAN -> "🇩🇪"
            Constants.Languages.ITALIAN -> "🇮🇹"
            Constants.Languages.PORTUGUESE -> "🇵🇹"
            Constants.Languages.RUSSIAN -> "🇷🇺"
            Constants.Languages.JAPANESE -> "🇯🇵"
            Constants.Languages.KOREAN -> "🇰🇷"
            Constants.Languages.CHINESE -> "🇨🇳"
            Constants.Languages.HINDI -> "🇮🇳"
            Constants.Languages.POLISH -> "🇵🇱"
            Constants.Languages.DUTCH -> "🇳🇱"
            Constants.Languages.TURKISH -> "🇹🇷"
            Constants.Languages.ARABIC -> "🇸🇦"
            else -> "🌐"
        }
    }
    
    /**
     * Calculate language match score for sorting streams.
     * Higher score = better match for preferred languages.
     */
    fun calculateLanguageScore(stream: Stream, preferredLanguages: Set<String>): Int {
        var score = 0
        val streamLanguages = stream.getAudioLanguages()
        
        for (lang in preferredLanguages) {
            if (streamLanguages.contains(lang)) {
                score += if (lang == Constants.Languages.ENGLISH) {
                    Constants.Streams.ENGLISH_SCORE
                } else {
                    Constants.Streams.OTHER_LANG_SCORE
                }
            }
        }
        
        // Bonus for MULTI audio
        if (stream.isMultiAudio()) {
            score += Constants.Streams.MULTI_AUDIO_BONUS
        }
        
        return score
    }
}
