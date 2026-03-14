package com.streambeam.model

import com.google.gson.annotations.SerializedName

// Stremio Manifest
data class Manifest(
    val id: String,
    val name: String,
    val description: String?,
    val version: String,
    val resources: List<Resource>,
    val types: List<String>,
    val catalogs: List<CatalogDefinition>?,
    val behaviorHints: BehaviorHints?
)

data class Resource(
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String>?
)

data class CatalogDefinition(
    val type: String,
    val id: String,
    val name: String?,
    val extra: List<Extra>?,
    val behaviorHints: BehaviorHints?
)

data class Extra(
    val name: String,
    val isRequired: Boolean?,
    val options: List<String>?
)

data class BehaviorHints(
    val configurable: Boolean?,
    val configurationRequired: Boolean?
)

// Catalog Response
data class CatalogResponse(
    val metas: List<Meta>
)

// Meta Response (single item for /meta/ endpoint)
data class MetaResponse(
    val meta: Meta
)

// Meta Item
data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val description: String?,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val genre: List<String>?,
    val released: String?,
    val videos: List<Video>?
)

// Video Episode
data class Video(
    val id: String,
    val title: String,
    val released: String?,
    val season: Int?,
    val episode: Int?,
    val thumbnail: String?,
    val overview: String?
)

// Stream Response
data class StreamResponse(
    val streams: List<Stream>
)

// Stream
data class Stream(
    val name: String?,
    val title: String?,
    val url: String?,
    val externalUrl: String?,
    val ytId: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val behaviorHints: StreamBehaviorHints?
) {
    /**
     * Checks if this stream has multiple audio tracks (MULTI).
     * Returns true if the title contains MULTI indicator.
     */
    fun isMultiAudio(): Boolean {
        val titleUpper = title?.uppercase() ?: ""
        val nameUpper = name?.uppercase() ?: ""
        val combined = "$titleUpper $nameUpper"
        return combined.contains("MULTI") || 
               combined.contains("MULTI-AUDIO") ||
               combined.contains("DUAL AUDIO") ||
               combined.contains("DUAL-AUDIO")
    }
    
    /**
     * Extracts available audio languages from the stream title.
     * Returns a list of detected language codes.
     */
    fun getAudioLanguages(): List<String> {
        val languages = mutableSetOf<String>()
        val titleUpper = title?.uppercase() ?: ""
        val nameUpper = name?.uppercase() ?: ""
        
        // Combine both for searching, add delimiters to help with pattern matching
        val combinedText = " $titleUpper $nameUpper "
        val filenameText = titleUpper.replace(".", " ").replace("_", " ")
        
        // Check for Cyrillic characters (Russian/Ukrainian/Bulgarian)
        val cyrillicPattern = Regex("[А-Яа-яЁёЇїІіЄєҐґБбВвГгДдЖжЗзИиЙйЛлПпФфЦцЧчШшЩщЪъЫыЬьЭэЮюЯя]")
        if (cyrillicPattern.find(titleUpper) != null || cyrillicPattern.find(nameUpper) != null) {
            languages.add("ru")  // Mark as Russian/Cyrillic
        }
        
        // Check for other non-Latin scripts
        // Chinese characters
        if (Regex("[\u4e00-\u9fff]").find(titleUpper) != null) {
            languages.add("zh")
        }
        // Japanese Hiragana/Katakana
        if (Regex("[\u3040-\u309f\u30a0-\u30ff]").find(titleUpper) != null) {
            languages.add("ja")
        }
        // Korean Hangul
        if (Regex("[\uac00-\ud7af]").find(titleUpper) != null) {
            languages.add("ko")
        }
        // Arabic script
        if (Regex("[\u0600-\u06ff]").find(titleUpper) != null) {
            languages.add("ar")
        }
        // Hindi/Devanagari
        if (Regex("[\u0900-\u097f]").find(titleUpper) != null) {
            languages.add("hi")
        }
        
        // Language detection patterns - check non-English FIRST to avoid false positives
        
        // German
        if (Regex("[ ._\\-\\[\\(](GER|DEU|GERMAN|DEUTSCH|GER-AUDIO|AUDIO-GER)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(GER|DEU|GERMAN|DEUTSCH)\\b").find(filenameText) != null) {
            languages.add("de")
        }
        
        // Spanish
        if (Regex("[ ._\\-\\[\\(](ESP|SPA|SPANISH|ESPAÑOL|ESPANOL|ESP-AUDIO|LATINO|LAT)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(ESP|SPA|SPANISH|ESPAÑOL|ESPANOL|LATINO|LAT)\\b").find(filenameText) != null) {
            languages.add("es")
        }
        
        // French
        if (Regex("[ ._\\-\\[\\(](FRA|FRE|FRENCH|FRANÇAIS|FRANCAIS|FRA-AUDIO|VFF|VFQ|VF)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(FRA|FRE|FRENCH|FRANÇAIS|FRANCAIS|VFF|VFQ)\\b").find(filenameText) != null) {
            languages.add("fr")
        }
        
        // Italian
        if (Regex("[ ._\\-\\[\\(](ITA|ITALIAN|ITALIANO|ITA-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(ITA|ITALIAN|ITALIANO)\\b").find(filenameText) != null) {
            languages.add("it")
        }
        
        // Portuguese
        if (Regex("[ ._\\-\\[\\(](POR|PORTUGUESE|PORTUGUÊS|PORTUGUES|POR-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(POR|PORTUGUESE|PORTUGUÊS|PORTUGUES)\\b").find(filenameText) != null) {
            languages.add("pt")
        }
        
        // Russian (text markers)
        if (Regex("[ ._\\-\\[\\(](RUS|RUSSIAN|РУССКИЙ|RUS-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(RUS|RUSSIAN|РУССКИЙ)\\b").find(filenameText) != null) {
            languages.add("ru")
        }
        
        // Japanese (text markers)
        if (Regex("[ ._\\-\\[\\(](JPN|JAP|JAPANESE|日本語|JPN-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(JPN|JAP|JAPANESE|日本語)\\b").find(filenameText) != null) {
            languages.add("ja")
        }
        
        // Korean (text markers)
        if (Regex("[ ._\\-\\[\\(](KOR|KOREAN|한국어|KOR-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(KOR|KOREAN|한국어)\\b").find(filenameText) != null) {
            languages.add("ko")
        }
        
        // Chinese (text markers)
        if (Regex("[ ._\\-\\[\\(](CHI|CHN|CHINESE|中文|MANDARIN|CANTONESE|CAN|CHS|CHT)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(CHI|CHN|CHINESE|中文|MANDARIN|CANTONESE|CHS|CHT)\\b").find(filenameText) != null) {
            languages.add("zh")
        }
        
        // Hindi (text markers)
        if (Regex("[ ._\\-\\[\\(](HIN|HINDI|हिन्दी)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(HIN|HINDI|हिन्दी)\\b").find(filenameText) != null) {
            languages.add("hi")
        }
        
        // Polish
        if (Regex("[ ._\\-\\[\\(](POL|POLISH|POLSKI)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(POL|POLISH|POLSKI)\\b").find(filenameText) != null) {
            languages.add("pl")
        }
        
        // Dutch
        if (Regex("[ ._\\-\\[\\(](DUT|DUTCH|NEDERLANDS|NL)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(DUT|DUTCH|NEDERLANDS)\\b").find(filenameText) != null) {
            languages.add("nl")
        }
        
        // Turkish
        if (Regex("[ ._\\-\\[\\(](TUR|TURKISH|TÜRKÇE|TURKCE)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(TUR|TURKISH|TÜRKÇE|TURKCE)\\b").find(filenameText) != null) {
            languages.add("tr")
        }
        
        // Arabic (text markers)
        if (Regex("[ ._\\-\\[\\(](ARA|ARABIC|العربية)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(ARA|ARABIC|العربية)\\b").find(filenameText) != null) {
            languages.add("ar")
        }
        
        // Ukrainian
        if (Regex("[ ._\\-\\[\\(](UKR|UKRAINIAN|УКРАЇНСЬКА)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(UKR|UKRAINIAN|УКРАЇНСЬКА)\\b").find(filenameText) != null) {
            languages.add("uk")
        }
        
        // English - check LAST and only if no other language detected
        // Require EXPLICIT English markers
        if (languages.isEmpty()) {
            if (Regex("[ ._\\-\\[\\(](ENG|ENGLISH|ENG-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
                Regex("[ ._\\-\\[\\(]EN[ ._\\-\\[\\(]?(US|GB|CA|UK|AU)[ ._\\-\\]\\)]").find(combinedText) != null ||
                Regex("\\b(ENG|ENGLISH)\\b").find(filenameText) != null) {
                languages.add("en")
            }
        }
        
        // If still no language detected, return empty list (unknown language)
        // This is better than falsely assuming English
        return languages.sorted()
    }
    
    /**
     * Check if this is a single episode file (not a season pack)
     */
    fun isSingleEpisode(season: Int? = null, episode: Int? = null): Boolean {
        val titleUpper = title?.uppercase() ?: ""
        val filenameUpper = behaviorHints?.filename?.uppercase() ?: titleUpper
        
        // Check for single episode pattern SxxExx
        val singleEpPattern = Regex("S\\d{1,2}E\\d{1,2}\\b")
        val seasonPackPattern = Regex("S\\d{1,2}[^E]|S\\d{1,2}\\s*(COMPLETE|FULL|PACK)")
        
        val hasSingleEp = singleEpPattern.find(filenameUpper) != null || 
                         singleEpPattern.find(titleUpper) != null
        val hasSeasonPack = seasonPackPattern.find(filenameUpper) != null ||
                           seasonPackPattern.find(titleUpper) != null
        
        // If looking for specific episode, check if this file contains it
        if (season != null && episode != null && hasSingleEp) {
            val targetPattern = Regex("S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}", RegexOption.IGNORE_CASE)
            return targetPattern.find(filenameUpper) != null || 
                   targetPattern.find(titleUpper) != null
        }
        
        return hasSingleEp && !hasSeasonPack
    }
    
    /**
     * Get the episode matching score for season packs
     * Returns 100 if exact match, lower for season packs, 0 if definitely wrong episode
     */
    fun getEpisodeMatchScore(targetSeason: Int, targetEpisode: Int): Int {
        val titleUpper = title?.uppercase() ?: ""
        val filenameUpper = behaviorHints?.filename?.uppercase() ?: titleUpper
        val combinedText = "$titleUpper $filenameUpper"
        
        // Check for exact episode match S01E02
        val exactPattern = Regex("S${targetSeason.toString().padStart(2, '0')}E${targetEpisode.toString().padStart(2, '0')}", RegexOption.IGNORE_CASE)
        if (exactPattern.find(combinedText) != null) {
            return 100  // Perfect match
        }
        
        // Check for other episode patterns (S01E01, S01E03, etc) - means it's a different episode
        val otherEpPattern = Regex("S\\d{1,2}E(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
        val otherMatches = otherEpPattern.findAll(combinedText)
        for (match in otherMatches) {
            val epNum = match.groupValues[1].toIntOrNull()
            if (epNum != null && epNum != targetEpisode) {
                return 0  // Wrong episode
            }
        }
        
        // Check for season pack (S01 without specific episode)
        val seasonPackPattern = Regex("S${targetSeason.toString().padStart(2, '0')}[^E0-9]|S${targetSeason.toString().padStart(2, '0')}\$", RegexOption.IGNORE_CASE)
        val anySeasonPattern = Regex("S\\d{1,2}[^E0-9]|COMPLETE|FULL\\s*SEASON", RegexOption.IGNORE_CASE)
        
        return when {
            seasonPackPattern.find(combinedText) != null -> 50  // Right season pack
            anySeasonPattern.find(combinedText) != null -> 30   // Some season pack
            else -> 10  // Unknown
        }
    }
    
    companion object {
        fun getLanguageDisplayName(code: String?): String {
            return when (code) {
                "en" -> "English"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "ru" -> "Russian"
                "uk" -> "Ukrainian"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "zh" -> "Chinese"
                "hi" -> "Hindi"
                "ar" -> "Arabic"
                "pl" -> "Polish"
                "nl" -> "Dutch"
                "tr" -> "Turkish"
                else -> "Unknown"
            }
        }
        
        fun getLanguageFlag(code: String?): String {
            return when (code) {
                "en" -> "🇺🇸"
                "es" -> "🇪🇸"
                "fr" -> "🇫🇷"
                "de" -> "🇩🇪"
                "it" -> "🇮🇹"
                "pt" -> "🇵🇹"
                "ru" -> "🇷🇺"
                "uk" -> "🇺🇦"
                "ja" -> "🇯🇵"
                "ko" -> "🇰🇷"
                "zh" -> "🇨🇳"
                "hi" -> "🇮🇳"
                "ar" -> "🇸🇦"
                "pl" -> "🇵🇱"
                "nl" -> "🇳🇱"
                "tr" -> "🇹🇷"
                else -> "🌐"
            }
        }
    }
}

data class StreamBehaviorHints(
    val notWebReady: Boolean?,
    val bingeGroup: String?,
    val filename: String?
)
