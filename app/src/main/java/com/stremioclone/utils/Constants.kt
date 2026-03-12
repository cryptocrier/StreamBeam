package com.stremioclone.utils

/**
 * Application constants for production release.
 * All magic numbers, hardcoded values, and configuration should live here.
 */
object Constants {
    
    // ============================================================================
    // Timing & Delays
    // ============================================================================
    object Delays {
        const val CAST_SWITCH = 500L           // Delay before switching to cast (ms)
        const val AUDIO_TRACK_UPDATE = 1500L   // Delay before updating audio tracks (ms)
        const val CONTROLS_HIDE = 3000L        // Auto-hide player controls (ms)
        const val POSITION_UPDATE = 500L       // Position polling interval (ms)
        const val SEEK_DEBOUNCE = 300L         // Debounce after seeking (ms)
        const val SEARCH_TIMEOUT = 5000L       // Addon search timeout (ms)
    }
    
    // ============================================================================
    // Seek Amounts
    // ============================================================================
    object Seek {
        const val FORWARD_MS = 30000L          // Fast forward amount (30 seconds)
        const val BACKWARD_MS = 15000L         // Rewind amount (15 seconds)
    }
    
    // ============================================================================
    // Stream Processing
    // ============================================================================
    object Streams {
        const val MAX_PER_QUALITY = 6          // Maximum streams per quality level
        const val ENGLISH_SCORE = 10           // Language match score for English
        const val OTHER_LANG_SCORE = 5         // Language match score for other languages
        const val MULTI_AUDIO_BONUS = 3        // Bonus score for MULTI audio streams
    }
    
    // ============================================================================
    // Addon URLs
    // ============================================================================
    object Addons {
        const val CINEMETA = "https://cinemeta.strem.io/"
        const val CINEMETA_V3 = "https://v3-cinemeta.strem.io/"
        const val TORRENTIO_BASE = "https://torrentio.strem.fun/"
        const val COMET_BASE = "https://comet.elfhosted.com/"
        
        object Paths {
            const val MANIFEST = "manifest.json"
            const val STREAM_MOVIE = "/stream/movie/"
            const val STREAM_SERIES = "/stream/series/"
        }
    }
    
    // ============================================================================
    // Cast SDK
    // ============================================================================
    object Cast {
        const val PLAYER_STATE_PLAYING = 2
        const val PLAYER_STATE_BUFFERING = 3
        const val PLAYER_STATE_PAUSED = 3
        const val PLAYER_STATE_IDLE = 1
    }
    
    // ============================================================================
    // UI / Theming
    // ============================================================================
    object UI {
        const val MAX_TITLE_LINES = 1
        const val MAX_OVERVIEW_LINES = 3
        const val CORNER_RADIUS_SMALL = 8
        const val CORNER_RADIUS_MEDIUM = 12
        const val CORNER_RADIUS_LARGE = 16
        const val ICON_SIZE_SMALL = 16
        const val ICON_SIZE_MEDIUM = 24
        const val ICON_SIZE_LARGE = 32
    }
    
    // ============================================================================
    // Quality Categories (for folder grouping)
    // ============================================================================
    object Quality {
        const val HDR = "4K HDR"
        const val UHD = "4K"
        const val FHD = "1080p"
        const val HD = "720p"
        const val SD = "SD"
        const val UNKNOWN = "Unknown"
        
        val ORDER = listOf(HDR, UHD, FHD, HD, SD, UNKNOWN)
    }
    
    // ============================================================================
    // Logging
    // ============================================================================
    object LogTags {
        const val CAST = "CastManager"
        const val PLAYER = "PlayerScreen"
        const val CAST_BAR = "PersistentCastBar"
        const val VIEW_MODEL = "MainViewModel"
    }
    
    // ============================================================================
    // Network
    // ============================================================================
    object Network {
        const val CONNECT_TIMEOUT = 30L        // Seconds
        const val READ_TIMEOUT = 60L           // Seconds
        const val WRITE_TIMEOUT = 30L          // Seconds
        
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    
    // ============================================================================
    // ExoPlayer Buffer Settings
    // ============================================================================
    object Buffer {
        const val MIN_BUFFER_MS = 2000
        const val MAX_BUFFER_MS = 30000
        const val PLAYBACK_MS = 1000
        const val REBUFFER_MS = 2000
    }
    
    // ============================================================================
    // Language Codes (ISO 639-1)
    // ============================================================================
    object Languages {
        const val ENGLISH = "en"
        const val SPANISH = "es"
        const val FRENCH = "fr"
        const val GERMAN = "de"
        const val ITALIAN = "it"
        const val PORTUGUESE = "pt"
        const val RUSSIAN = "ru"
        const val JAPANESE = "ja"
        const val KOREAN = "ko"
        const val CHINESE = "zh"
        const val HINDI = "hi"
        const val POLISH = "pl"
        const val DUTCH = "nl"
        const val TURKISH = "tr"
        const val ARABIC = "ar"
        
        val ALL = setOf(
            ENGLISH, SPANISH, FRENCH, GERMAN, ITALIAN,
            PORTUGUESE, RUSSIAN, JAPANESE, KOREAN, CHINESE,
            HINDI, POLISH, DUTCH, TURKISH, ARABIC
        )
    }
}
