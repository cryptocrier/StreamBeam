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
        
        // Check for MULTI/DUAL indicator first
        if (isMultiAudio()) {
            // MULTI streams typically have English + other languages
            languages.add("en")
        }
        
        // Language detection patterns
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
        
        // Russian
        if (Regex("[ ._\\-\\[\\(](RUS|RUSSIAN|РУССКИЙ|RUS-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(RUS|RUSSIAN|РУССКИЙ)\\b").find(filenameText) != null) {
            languages.add("ru")
        }
        
        // Japanese
        if (Regex("[ ._\\-\\[\\(](JPN|JAP|JAPANESE|日本語|JPN-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(JPN|JAP|JAPANESE|日本語)\\b").find(filenameText) != null) {
            languages.add("ja")
        }
        
        // Korean
        if (Regex("[ ._\\-\\[\\(](KOR|KOREAN|한국어|KOR-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(KOR|KOREAN|한국어)\\b").find(filenameText) != null) {
            languages.add("ko")
        }
        
        // Chinese
        if (Regex("[ ._\\-\\[\\(](CHI|CHN|CHINESE|中文|MANDARIN|CANTONESE|CAN|CHS|CHT)[ ._\\-\\]\\)]").find(combinedText) != null ||
           Regex("\\b(CHI|CHN|CHINESE|中文|MANDARIN|CANTONESE|CHS|CHT)\\b").find(filenameText) != null) {
            languages.add("zh")
        }
        
        // Hindi
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
        
        // English - check last to avoid false positives
        if (Regex("[ ._\\-\\[\\(](ENG|ENGLISH|ENG-AUDIO)[ ._\\-\\]\\)]").find(combinedText) != null ||
            Regex("[ ._\\-\\[\\(]EN[ ._\\-\\[\\(]?(US|GB|CA|UK|AU)[ ._\\-\\]\\)]").find(combinedText) != null ||
            Regex("\\b(ENG|ENGLISH)\\b").find(filenameText) != null) {
            languages.add("en")
        }
        
        // If no specific language detected, assume English as default
        if (languages.isEmpty()) {
            languages.add("en")
        }
        
        return languages.sorted()
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
