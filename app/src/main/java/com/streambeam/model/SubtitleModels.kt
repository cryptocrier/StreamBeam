package com.streambeam.model

import com.google.gson.annotations.SerializedName

/**
 * Subtitle data models for OpenSubtitles API integration
 */

/**
 * Represents a subtitle file from OpenSubtitles
 */
data class Subtitle(
    val id: String,
    val language: String,
    val languageName: String,
    val filename: String,
    val downloadUrl: String? = null,
    val isHearingImpaired: Boolean = false,
    val score: Double = 0.0,
    val votes: Int = 0,
    val downloadCount: Int = 0
) {
    fun getLanguageDisplayName(): String {
        return when (language.lowercase()) {
            "en" -> "English"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "ru" -> "Russian"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "pl" -> "Polish"
            "nl" -> "Dutch"
            "tr" -> "Turkish"
            "sv" -> "Swedish"
            "da" -> "Danish"
            "no" -> "Norwegian"
            "fi" -> "Finnish"
            "cs" -> "Czech"
            "hu" -> "Hungarian"
            "el" -> "Greek"
            "he" -> "Hebrew"
            "th" -> "Thai"
            "vi" -> "Vietnamese"
            "id" -> "Indonesian"
            else -> language.uppercase()
        }
    }
    
    fun getFlag(): String {
        return when (language.lowercase()) {
            "en" -> "🇺🇸"
            "es" -> "🇪🇸"
            "fr" -> "🇫🇷"
            "de" -> "🇩🇪"
            "it" -> "🇮🇹"
            "pt" -> "🇵🇹"
            "ru" -> "🇷🇺"
            "ja" -> "🇯🇵"
            "ko" -> "🇰🇷"
            "zh" -> "🇨🇳"
            "ar" -> "🇸🇦"
            "hi" -> "🇮🇳"
            "pl" -> "🇵🇱"
            "nl" -> "🇳🇱"
            "tr" -> "🇹🇷"
            "sv" -> "🇸🇪"
            "da" -> "🇩🇰"
            "no" -> "🇳🇴"
            "fi" -> "🇫🇮"
            "cs" -> "🇨🇿"
            "hu" -> "🇭🇺"
            "el" -> "🇬🇷"
            "he" -> "🇮🇱"
            "th" -> "🇹🇭"
            "vi" -> "🇻🇳"
            "id" -> "🇮🇩"
            else -> "🌐"
        }
    }
}

/**
 * Subtitle search request parameters
 */
data class SubtitleSearchRequest(
    val query: String? = null,
    val languages: List<String> = listOf("en"),
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val movieHash: String? = null,
    val movieBytes: Long? = null
)

/**
 * OpenSubtitles API response models
 */
data class OpenSubtitlesSearchResponse(
    @SerializedName("data") val data: List<OpenSubtitlesSubtitleData>
)

data class OpenSubtitlesSubtitleData(
    @SerializedName("id") val id: String,
    @SerializedName("attributes") val attributes: OpenSubtitlesAttributes
)

data class OpenSubtitlesAttributes(
    @SerializedName("language") val language: String,
    @SerializedName("feature_details") val featureDetails: OpenSubtitlesFeatureDetails?,
    @SerializedName("files") val files: List<OpenSubtitlesFile>?,
    @SerializedName("hearing_impaired") val hearingImpaired: Boolean?,
    @SerializedName("votes") val votes: Int?,
    @SerializedName("ratings") val ratings: Double?,
    @SerializedName("download_count") val downloadCount: Int?
)

data class OpenSubtitlesFeatureDetails(
    @SerializedName("title") val title: String?,
    @SerializedName("imdb_id") val imdbId: Int?,
    @SerializedName("tmdb_id") val tmdbId: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("year") val year: Int?
)

data class OpenSubtitlesFile(
    @SerializedName("file_id") val fileId: Int,
    @SerializedName("file_name") val fileName: String?
)

data class OpenSubtitlesDownloadResponse(
    @SerializedName("link") val link: String,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("remaining") val remaining: Int?
)

/**
 * User preferences for subtitles
 */
data class SubtitlePreferences(
    val preferredLanguages: List<String> = listOf("en"),
    val autoDownload: Boolean = false,
    val textSize: Float = 1.0f,
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#000000",
    val backgroundOpacity: Float = 0.5f,
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.OUTLINE
)

enum class SubtitleEdgeStyle {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED
}

/**
 * Downloaded subtitle cache entry
 */
data class DownloadedSubtitle(
    val id: String,
    val videoId: String,
    val language: String,
    val localPath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
