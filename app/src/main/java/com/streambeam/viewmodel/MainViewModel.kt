package com.streambeam.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streambeam.addons.AddonManager
import com.streambeam.cast.CastManager
import com.streambeam.data.DataStoreManager
import com.streambeam.model.Meta
import com.streambeam.model.Stream
import com.streambeam.realdebrid.RealDebridException
import com.streambeam.realdebrid.RealDebridManager
import com.streambeam.ui.state.StreamLoadingState
import com.streambeam.utils.Constants
import com.streambeam.utils.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val addonManager = AddonManager()
    val castManager = CastManager(application)
    private val dataStoreManager = DataStoreManager(application)
    
    private var realDebridManager: RealDebridManager? = null
    
    // Content type enum
    enum class ContentType { MOVIES, TV_SHOWS }
    
    private val _currentContentType = MutableStateFlow(ContentType.MOVIES)
    val currentContentType: StateFlow<ContentType> = _currentContentType
    
    // Movies state
    private val _movies = MutableStateFlow<List<Meta>>(emptyList())
    val movies: StateFlow<List<Meta>> = _movies
    
    // TV Shows state
    private val _tvShows = MutableStateFlow<List<Meta>>(emptyList())
    val tvShows: StateFlow<List<Meta>> = _tvShows
    
    // Search results state
    private val _searchResults = MutableStateFlow<List<Meta>>(emptyList())
    val searchResults: StateFlow<List<Meta>> = _searchResults
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    
    private val _streams = MutableStateFlow<List<Stream>>(emptyList())
    val streams: StateFlow<List<Stream>> = _streams
    
    // Grouped streams by addon source
    private val _groupedStreams = MutableStateFlow<Map<String, List<Stream>>>(emptyMap())
    val groupedStreams: StateFlow<Map<String, List<Stream>>> = _groupedStreams
    
    // New: Stream loading state using sealed class pattern
    private val _streamLoadingState = MutableStateFlow<StreamLoadingState>(StreamLoadingState.Idle)
    val streamLoadingState: StateFlow<StreamLoadingState> = _streamLoadingState
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    // Selected TV show with full episode data
    private val _selectedTVShow = MutableStateFlow<Meta?>(null)
    val selectedTVShow: StateFlow<Meta?> = _selectedTVShow
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _realDebridKey = MutableStateFlow("")
    val realDebridKey: StateFlow<String> = _realDebridKey
    
    private val _cometUrl = MutableStateFlow("")
    val cometUrl: StateFlow<String> = _cometUrl
    
    private val _preferredLanguages = MutableStateFlow<Set<String>>(setOf("en"))
    val preferredLanguages: StateFlow<Set<String>> = _preferredLanguages
    
    init {
        loadMovies()
        loadTVShows()
        loadSavedApiKey()
        loadSavedCometUrl()
        loadPreferredLanguages()
    }
    
    private fun loadPreferredLanguages() {
        viewModelScope.launch {
            dataStoreManager.preferredLanguages.collect { languages ->
                _preferredLanguages.value = languages
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loaded preferred languages: $languages")
            }
        }
    }
    
    fun setPreferredLanguages(languages: Set<String>) {
        viewModelScope.launch {
            dataStoreManager.savePreferredLanguages(languages)
            _preferredLanguages.value = languages
        }
    }
    
    private fun loadSavedCometUrl() {
        viewModelScope.launch {
            dataStoreManager.cometUrl.collect { url ->
                _cometUrl.value = url
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Comet URL ${if (url.isNotEmpty()) "loaded" else "not set"}")
            }
        }
    }
    
    fun setCometUrl(url: String) {
        viewModelScope.launch {
            // Remove /manifest.json suffix if present to get base URL
            val baseUrl = url.removeSuffix("/manifest.json").removeSuffix("/")
            dataStoreManager.saveCometUrl(baseUrl)
            _cometUrl.value = baseUrl
        }
    }
    
    fun clearCometUrl() {
        viewModelScope.launch {
            dataStoreManager.clearCometUrl()
            _cometUrl.value = ""
        }
    }
    
    fun togglePreferredLanguage(language: String) {
        viewModelScope.launch {
            val current = _preferredLanguages.value
            if (current.contains(language)) {
                dataStoreManager.removePreferredLanguage(language)
            } else {
                dataStoreManager.addPreferredLanguage(language)
            }
        }
    }
    
    fun setContentType(type: ContentType) {
        _currentContentType.value = type
        // Load content if empty
        when (type) {
            ContentType.MOVIES -> if (_movies.value.isEmpty()) loadMovies()
            ContentType.TV_SHOWS -> if (_tvShows.value.isEmpty()) loadTVShows()
        }
    }
    
    private fun loadSavedApiKey() {
        viewModelScope.launch {
            dataStoreManager.realDebridKey.collect { key ->
                _realDebridKey.value = key
                if (key.isNotEmpty()) {
                    realDebridManager = RealDebridManager(key)
                }
            }
        }
    }
    
    fun setRealDebridKey(key: String) {
        viewModelScope.launch {
            dataStoreManager.saveRealDebridKey(key)
            _realDebridKey.value = key
            if (key.isNotEmpty()) {
                realDebridManager = RealDebridManager(key)
                // Verify account
                try {
                    val status = realDebridManager?.verifyAccount()
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Real-Debrid account: $status")
                } catch (e: Exception) {
                    android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to verify account: ${e.message}")
                }
            } else {
                realDebridManager = null
            }
        }
    }
    
    fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Try multiple addons in order
            val addonsToTry = listOf(
                Constants.Addons.CINEMETA to "Cinemeta",
                Constants.Addons.CINEMETA_V3 to "Cinemeta v3",
                Constants.Addons.TORRENTIO_BASE to "Torrentio"
            )
            
            var lastError: String? = null
            
            for ((url, name) in addonsToTry) {
                try {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Trying addon: $name at $url")
                    val addon = addonManager.getClient(url)
                    
                    // Try different catalog IDs
                    val catalogIds = listOf("top")
                    for (catalogId in catalogIds) {
                        try {
                            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Trying catalog: $catalogId")
                            val response = addon.getCatalog("movie", catalogId)
                            if (response.metas.isNotEmpty()) {
                                _movies.value = response.metas
                                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Successfully loaded ${response.metas.size} movies from $name/$catalogId")
                                return@launch // Success, exit
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(Constants.LogTags.VIEW_MODEL, "Catalog $catalogId failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to load from $name: ${e.message}", e)
                    lastError = "$name: ${e.message}"
                }
            }
            
            // All addons failed
            _error.value = "Unable to connect to movie database.\n\nError: $lastError\n\nPlease check:\n• Internet connection\n• VPN if required\n• Try again later"
        }.invokeOnCompletion {
            _isLoading.value = false
        }
    }
    
    fun loadTVShows() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val addonsToTry = listOf(
                Constants.Addons.CINEMETA to "Cinemeta",
                Constants.Addons.CINEMETA_V3 to "Cinemeta v3"
            )
            
            var lastError: String? = null
            
            for ((url, name) in addonsToTry) {
                try {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Trying addon for TV: $name at $url")
                    val addon = addonManager.getClient(url)
                    
                    val catalogIds = listOf("top", "popular")
                    for (catalogId in catalogIds) {
                        try {
                            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Trying TV catalog: $catalogId")
                            val response = addon.getCatalog("series", catalogId)
                            if (response.metas.isNotEmpty()) {
                                _tvShows.value = response.metas
                                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Successfully loaded ${response.metas.size} TV shows from $name/$catalogId")
                                return@launch
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(Constants.LogTags.VIEW_MODEL, "TV catalog $catalogId failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to load TV from $name: ${e.message}", e)
                    lastError = "$name: ${e.message}"
                }
            }
            
            _error.value = "Unable to load TV shows.\n\nError: $lastError"
        }.invokeOnCompletion {
            _isLoading.value = false
        }
    }
    
    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            
            val allResults = mutableListOf<Meta>()
            val searchQuery = query.trim().lowercase()
            
            try {
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Searching '$query' with Cinemeta v3")
                val addon = addonManager.getClient(Constants.Addons.CINEMETA_V3)
                
                // Search both movies and TV in parallel with timeout
                val movieJob = launch {
                    try {
                        val movieResults = withTimeout(Constants.Delays.SEARCH_TIMEOUT) {
                            addon.search("movie", "top", searchQuery)
                        }
                        allResults.addAll(movieResults.metas)
                        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Movie search: ${movieResults.metas.size} results")
                    } catch (e: Exception) {
                        android.util.Log.w(Constants.LogTags.VIEW_MODEL, "Movie search failed: ${e.message}")
                    }
                }
                
                val tvJob = launch {
                    try {
                        val tvResults = withTimeout(Constants.Delays.SEARCH_TIMEOUT) {
                            addon.search("series", "top", searchQuery)
                        }
                        allResults.addAll(tvResults.metas)
                        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "TV search: ${tvResults.metas.size} results")
                    } catch (e: Exception) {
                        android.util.Log.w(Constants.LogTags.VIEW_MODEL, "TV search failed: ${e.message}")
                    }
                }
                
                // Wait for both searches to complete
                movieJob.join()
                tvJob.join()
            } catch (e: Exception) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Search error: ${e.message}")
            }
            
            _searchResults.value = allResults.distinctBy { it.id }
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Search found ${allResults.size} unique results")
        }.invokeOnCompletion {
            _isSearching.value = false
        }
    }
    
    fun clearSearch() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
    
    fun loadTVShowDetails(tvShowId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loading full TV show details for: $tvShowId")
                val addon = addonManager.getClient(Constants.Addons.CINEMETA_V3)
                
                // Fetch full metadata which includes all episodes
                val response = addon.getMeta("series", tvShowId)
                val fullMeta = response.meta
                _selectedTVShow.value = fullMeta
                
                // Count episodes by season
                val episodesBySeason = fullMeta.videos?.groupBy { it.season ?: 0 }
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loaded TV show with ${fullMeta.videos?.size} episodes across ${episodesBySeason?.size} seasons")
                episodesBySeason?.forEach { (season, episodes) ->
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Season $season: ${episodes.size} episodes")
                }
            } catch (e: Exception) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to load TV show details: ${e.message}", e)
                _error.value = "Failed to load episode data: ${e.message}"
            }
            
            _isLoading.value = false
        }
    }
    
    fun clearSelectedTVShow() {
        _selectedTVShow.value = null
        _error.value = null // Clear error when leaving TV show detail
    }

    
    fun loadStreams(metaId: String, type: String = "movie", season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _groupedStreams.value = emptyMap()
            _streamLoadingState.value = StreamLoadingState.Loading
            
            // Build the proper ID for TV episodes
            // Format: seriesId:season:episode (e.g., tt0944947:1:1)
            val streamId = when {
                type == "series" && season != null && episode != null -> "$metaId:$season:$episode"
                type == "series" && metaId.contains(":") -> metaId // Already has season:episode
                else -> metaId
            }
            
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loading streams for: $streamId (type: $type)")
            
            // Get preferred languages for filtering
            val preferredLangs = _preferredLanguages.value
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Preferred languages: $preferredLangs")
            
            // Build addon URLs with language filters
            // Comet format: /language=de,en/ or /language=de/
            // Torrentio: supports language param in path
            val langParam = preferredLangs.joinToString(",")
            
            // Define addon configurations
            val addonConfigs = buildList {
                // Torrentio variants
                add(Triple(
                    "${Constants.Addons.TORRENTIO_BASE}${if (preferredLangs != setOf("en")) "language=$langParam/" else ""}", 
                    "Torrentio", 
                    "torrentio"
                ))
                add(Triple(
                    "${Constants.Addons.TORRENTIO_BASE}${if (preferredLangs != setOf("en")) "language=$langParam|" else ""}sort=qualitysize|asc/", 
                    "Torrentio (Quality)", 
                    "torrentio"
                ))
                
                // Comet - use configured URL if available, otherwise skip
                val cometBaseUrl = _cometUrl.value
                if (cometBaseUrl.isNotEmpty()) {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Using configured Comet URL")
                    add(Triple(
                        "$cometBaseUrl/", 
                        "Comet", 
                        "comet"
                    ))
                } else {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Comet URL not configured, skipping")
                }
            }
            
            val errors = mutableListOf<String>()
            val allStreams = mutableListOf<Stream>()
            
            for ((url, internalName, sourceKey) in addonConfigs) {
                try {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Trying addon: $internalName at $url")
                    val addon = addonManager.getClient(url)
                    val response = addon.getStreams(type, streamId)
                    
                    if (response.streams.isNotEmpty()) {
                        // Tag streams with addon source info in the name field
                        val taggedStreams = response.streams.map { stream ->
                            stream.copy(
                                name = "${stream.name ?: internalName} [$internalName]"
                            )
                        }
                        
                        allStreams.addAll(taggedStreams)
                        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Got ${response.streams.size} streams from $internalName")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to get streams from $internalName: ${e.message}", e)
                    errors.add("$internalName: ${e.message}")
                }
            }
            
            if (allStreams.isNotEmpty()) {
                // Group by quality for folder display
                val qualityGroups = allStreams
                    .sortedWith(
                        compareByDescending<Stream> { 
                            // Language score first
                            var score = 0
                            val langs = it.getAudioLanguages()
                            for (lang in preferredLangs) {
                                if (langs.contains(lang)) {
                                    score += if (lang == "en") 10 else 5
                                }
                            }
                            if (it.isMultiAudio()) score += 3
                            score
                        }.thenByDescending { 
                            // Then by seeders
                            extractSeeders(it.title) 
                        }
                    )
                    .groupBy { extractQualityCategory(it.title ?: "") }
                
                // Sort by quality priority: 4K HDR, 4K, 1080p, 720p, SD
                val qualityOrder = listOf("4K HDR", "4K", "1080p", "720p", "SD", "Unknown")
                val sortedGroups = qualityGroups.toSortedMap(compareBy { 
                    qualityOrder.indexOf(it).takeIf { idx -> idx >= 0 } ?: qualityOrder.size 
                })
                
                _groupedStreams.value = sortedGroups
                _streams.value = allStreams
                _streamLoadingState.value = StreamLoadingState.Success(sortedGroups)
                
                val totalStreams = sortedGroups.values.sumOf { it.size }
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Quality groups: ${sortedGroups.keys}, Total: $totalStreams")
            } else {
                val cometHint = if (_cometUrl.value.isEmpty()) 
                    "\n\nTip: Configure Comet in Settings for more streams" 
                else 
                    ""
                val errorMessage = "No streams found.\n\n${errors.joinToString("\n")}$cometHint"
                _error.value = errorMessage
                _streamLoadingState.value = StreamLoadingState.Error(errorMessage)
            }
        }.invokeOnCompletion {
            _isLoading.value = false
            if (_streamLoadingState.value is StreamLoadingState.Loading) {
                _streamLoadingState.value = StreamLoadingState.Idle
            }
        }
    }
    
    fun processTorrentAndPlay(
        infoHash: String,
        title: String,
        onUrlReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = "Adding to Real-Debrid..."
            
            try {
                val apiKey = _realDebridKey.value
                if (apiKey.isBlank()) {
                    _error.value = "Real-Debrid API key not configured.\n\nPlease go to Settings and add your Real-Debrid API token."
                    return@launch
                }
                
                if (realDebridManager == null) {
                    realDebridManager = RealDebridManager(apiKey)
                }
                
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Processing torrent: $infoHash")
                val magnet = "magnet:?xt=urn:btih:$infoHash"
                
                val streamingUrl = realDebridManager?.getStreamingUrl(magnet)
                    ?: throw RealDebridException("Failed to get streaming URL")
                
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Got streaming URL successfully")
                _error.value = null
                onUrlReady(streamingUrl)
                
            } catch (e: RealDebridException) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Real-Debrid error: ${e.message}", e)
                _error.value = e.message ?: "Real-Debrid error"
            } catch (e: Exception) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Error processing torrent: ${e.message}", e)
                _error.value = "Error: ${e.message ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun playStream(stream: Stream, title: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val url = when {
                    stream.url != null -> stream.url
                    stream.infoHash != null && realDebridManager != null -> {
                        val magnet = "magnet:?xt=urn:btih:${stream.infoHash}"
                        realDebridManager?.getStreamingUrl(magnet)
                    }
                    else -> null
                }
                
                url?.let {
                    if (castManager.isConnected.value) {
                        castManager.castVideo(it, title)
                    } else {
                        castManager.playLocally(it, title)
                    }
                } ?: run {
                    _error.value = "No playable URL found"
                }
            } catch (e: Exception) {
                _error.value = "Failed to play stream: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshCastState() {
        // Cast state is managed by CastManager
    }
    
    fun cleanup() {
        castManager.release()
    }
    
    private fun extractQuality(title: String?): String {
        if (title == null) return "SD"
        return when {
            title.contains("4K", ignoreCase = true) || 
            title.contains("2160p", ignoreCase = true) -> "4K"
            title.contains("1080p", ignoreCase = true) || 
            title.contains("1080i", ignoreCase = true) -> "1080p"
            title.contains("720p", ignoreCase = true) || 
            title.contains("720i", ignoreCase = true) -> "720p"
            else -> "SD"
        }
    }
    
    /**
     * Extract quality category for folder grouping.
     * Distinguishes between 4K HDR and regular 4K.
     */
    private fun extractQualityCategory(title: String?): String {
        if (title == null) return "Unknown"
        val titleUpper = title.uppercase()
        
        return when {
            // 4K with HDR/Dolby Vision
            (titleUpper.contains("2160P") || titleUpper.contains("4K")) && 
            (titleUpper.contains("HDR") || titleUpper.contains("DV") || 
             titleUpper.contains("DOVI") || titleUpper.contains("DOLBY VISION")) -> "4K HDR"
            
            // Regular 4K
            titleUpper.contains("2160P") || titleUpper.contains("4K") -> "4K"
            
            // 1080p
            titleUpper.contains("1080P") || titleUpper.contains("1080I") -> "1080p"
            
            // 720p
            titleUpper.contains("720P") || titleUpper.contains("720I") -> "720p"
            
            // SD (480p and below)
            titleUpper.contains("480P") || titleUpper.contains("360P") || 
            titleUpper.contains("SD") -> "SD"
            
            else -> "Unknown"
        }
    }
    
    private fun extractSeeders(title: String?): Int {
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
    
    private fun processStreamsByQuality(streams: List<Stream>): List<Stream> {
        return processStreamsByQualityAndLanguage(streams, _preferredLanguages.value)
    }
    
    private fun processStreamsByQualityAndLanguage(
        streams: List<Stream>, 
        preferredLanguages: Set<String>
    ): List<Stream> {
        // Define quality priority order
        val qualityOrder = listOf("4K", "1080p", "720p", "SD")
        
        // Calculate language match score for each stream
        fun getLanguageScore(stream: Stream): Int {
            val streamLanguages = stream.getAudioLanguages()
            // Higher score if stream contains preferred languages
            var score = 0
            for (lang in preferredLanguages) {
                if (streamLanguages.contains(lang)) {
                    score += when (lang) {
                        "en" -> 10 // English gets higher priority
                        else -> 5  // Other languages
                    }
                }
            }
            // Bonus for MULTI audio (more language options)
            if (stream.isMultiAudio()) {
                score += 3
            }
            return score
        }
        
        // Group streams by quality
        val streamsByQuality = streams.groupBy { extractQuality(it.title ?: "") }
        
        // Process each quality group: sort by language score then seeders, take top 6
        val processedStreams = mutableListOf<Stream>()
        
        for (quality in qualityOrder) {
            val qualityStreams = streamsByQuality[quality] ?: emptyList()
            
            // Sort by: 1) Language match score (descending), 2) Seed count (descending)
            val topStreams = qualityStreams
                .sortedWith(
                    compareByDescending<Stream> { getLanguageScore(it) }
                        .thenByDescending { extractSeeders(it.title) }
                )
                .take(6)
            
            processedStreams.addAll(topStreams)
        }
        
        // Log language distribution
        val langDistribution = streams.flatMap { it.getAudioLanguages() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        
        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Processed streams: 4K=${streamsByQuality["4K"]?.size ?: 0}, " +
            "1080p=${streamsByQuality["1080p"]?.size ?: 0}, " +
            "720p=${streamsByQuality["720p"]?.size ?: 0}, " +
            "SD=${streamsByQuality["SD"]?.size ?: 0}, " +
            "Final=${processedStreams.size}, " +
            "Languages: ${langDistribution}")
        
        return processedStreams
    }
}
