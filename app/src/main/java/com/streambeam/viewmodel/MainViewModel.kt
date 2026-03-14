package com.streambeam.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streambeam.addons.AddonManager
import com.streambeam.addons.TmdbClient
import com.streambeam.cast.CastManager
import com.streambeam.data.DataStoreManager
import com.streambeam.model.Meta
import com.streambeam.model.Stream
import com.streambeam.model.Video
import com.streambeam.model.WatchProgress
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
    private val tmdbClient = TmdbClient()
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
    
    // Watch History
    private val _watchHistory = MutableStateFlow<List<WatchProgress>>(emptyList())
    val watchHistory: StateFlow<List<WatchProgress>> = _watchHistory
    
    // Current episode context for season pack selection
    private var _currentSeason: Int? = null
    private var _currentEpisode: Int? = null
    
    // Torrent processing state for UI
    data class TorrentProcessingState(
        val isProcessing: Boolean = false,
        val statusMessage: String = "",
        val posterUrl: String? = null,
        val title: String = ""
    )
    private val _torrentProcessingState = MutableStateFlow(TorrentProcessingState())
    val torrentProcessingState: StateFlow<TorrentProcessingState> = _torrentProcessingState
    
    init {
        loadMovies()
        loadTVShows()
        loadSavedApiKey()
        loadSavedCometUrl()
        loadPreferredLanguages()
        loadWatchHistory()
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
    
    // Watch History Methods
    private fun loadWatchHistory() {
        viewModelScope.launch {
            dataStoreManager.watchHistory.collect { history ->
                _watchHistory.value = history
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loaded ${history.size} watch history items")
            }
        }
    }
    
    fun saveWatchProgress(
        metaId: String,
        type: String,
        name: String,
        poster: String?,
        position: Long,
        duration: Long,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        streamUrl: String? = null
    ) {
        viewModelScope.launch {
            val id = WatchProgress.createId(metaId, season, episode)
            val progress = WatchProgress(
                id = id,
                metaId = metaId,
                type = type,
                name = name,
                poster = poster,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                position = position,
                duration = duration,
                lastWatched = System.currentTimeMillis(),
                isCompleted = position > 0 && duration > 0 && position >= duration * 0.9, // 90% watched = completed
                streamUrl = streamUrl
            )
            dataStoreManager.saveWatchProgress(progress)
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Saved watch progress: $name at ${progress.getProgressPercent()}%")
        }
    }
    
    fun removeFromWatchHistory(id: String) {
        viewModelScope.launch {
            dataStoreManager.removeFromWatchHistory(id)
        }
    }
    
    fun clearWatchHistory() {
        viewModelScope.launch {
            dataStoreManager.clearWatchHistory()
        }
    }
    
    fun getResumePosition(id: String): Long {
        return _watchHistory.value.find { it.id == id }?.position ?: 0L
    }
    
    fun hasResumePosition(id: String): Boolean {
        val progress = _watchHistory.value.find { it.id == id }
        return progress != null && progress.position > 10000 && // At least 10 seconds
               !progress.isCompleted && // Not completed
               progress.duration > 0 && 
               progress.position < progress.duration * 0.9 // Less than 90% watched
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
                
                // Log what we got from Cinemeta
                fullMeta.videos?.firstOrNull()?.let {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "First episode from Cinemeta: title='${it.title}', season=${it.season}, episode=${it.episode}")
                }
                
                // Always try to enrich with TMDB for better data (thumbnails, better descriptions)
                val enrichedMeta = if (tvShowId.startsWith("tt")) {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Attempting TMDB enrichment for: $tvShowId")
                    enrichEpisodesWithTmdb(fullMeta, tvShowId)
                } else {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Skipping TMDB - not an IMDB ID: $tvShowId")
                    fullMeta
                }
                
                _selectedTVShow.value = enrichedMeta
                
                // Count episodes by season
                val episodesBySeason = enrichedMeta.videos?.groupBy { it.season ?: 0 }
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Loaded TV show with ${enrichedMeta.videos?.size} episodes across ${episodesBySeason?.size} seasons")
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
    
    /**
     * Enrich episode data with TMDB to get proper episode titles and descriptions
     */
    private suspend fun enrichEpisodesWithTmdb(meta: Meta, imdbId: String): Meta {
        return try {
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "=== ENRICHING: $imdbId ===")
            
            // Get episode data from TMDB
            val tmdbEpisodes = tmdbClient.getAllEpisodes(imdbId)
            
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "TMDB returned ${tmdbEpisodes.size} seasons")
            tmdbEpisodes.forEach { (season, eps) ->
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "  Season $season: ${eps.size} episodes")
            }
            
            if (tmdbEpisodes.isEmpty()) {
                android.util.Log.w(Constants.LogTags.VIEW_MODEL, "No TMDB data found for: $imdbId")
                return meta
            }
            
            // Create a map of season:episode to TMDB episode data
            val tmdbEpisodeMap = mutableMapOf<Pair<Int, Int>, com.streambeam.addons.TmdbEpisode>()
            tmdbEpisodes.forEach { (seasonNum, episodes) ->
                episodes.forEach { episode ->
                    val epNum = episode.episodeNumber ?: return@forEach
                    tmdbEpisodeMap[Pair(seasonNum, epNum)] = episode
                }
            }
            
            val totalVideos = meta.videos?.size ?: 0
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Matching $totalVideos videos against ${tmdbEpisodeMap.size} TMDB episodes")
            
            // Enrich existing videos with TMDB data
            var enrichedCount = 0
            val enrichedVideos = meta.videos?.map { video ->
                val season = video.season ?: 0
                val episodeNum = video.episode ?: 0
                val key = Pair(season, episodeNum)
                val tmdbEp = tmdbEpisodeMap[key]
                
                if (tmdbEp != null) {
                    enrichedCount++
                    val newTitle = tmdbEp.name?.takeIf { it.isNotBlank() } ?: video.title
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "✓ S${season}E${episodeNum}: '${video.title?.take(20)}' -> '${newTitle.take(20)}'")
                    video.copy(
                        title = newTitle,
                        overview = tmdbEp.overview?.takeIf { it.isNotBlank() } ?: video.overview,
                        thumbnail = tmdbEp.getFullStillPath() ?: video.thumbnail,
                        released = tmdbEp.airDate ?: video.released
                    )
                } else {
                    android.util.Log.d(Constants.LogTags.VIEW_MODEL, "✗ S${season}E${episodeNum}: no TMDB match (have: ${tmdbEpisodeMap.keys.joinToString { "${it.first}:${it.second}" }})")
                    video
                }
            }
            
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "=== ENRICHED: $enrichedCount / $totalVideos episodes ===")
            meta.copy(videos = enrichedVideos)
            
        } catch (e: Exception) {
            android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Failed to enrich with TMDB: ${e.message}", e)
            meta // Return original meta on failure
        }
    }
    
    fun clearSelectedTVShow() {
        _selectedTVShow.value = null
        _error.value = null // Clear error when leaving TV show detail
    }
    
    /**
     * Get the next episode info for auto-play functionality
     * Returns: Triple of (episodeId, episodeTitle, hasNextEpisode)
     */
    fun getNextEpisode(seriesId: String, currentSeason: Int, currentEpisode: Int): Triple<String?, String?, Boolean> {
        val tvShow = _selectedTVShow.value
        
        // If we have the TV show loaded with videos
        if (tvShow != null && tvShow.id == seriesId && tvShow.videos != null) {
            val videos = tvShow.videos
            
            // Try to find next episode in current season
            val nextEpisode = videos.find { 
                it.season == currentSeason && it.episode == currentEpisode + 1 
            }
            
            if (nextEpisode != null) {
                val epId = "${seriesId}:${nextEpisode.season}:${nextEpisode.episode}"
                val epTitle = "${tvShow.name} - S${nextEpisode.season}:E${nextEpisode.episode}${nextEpisode.title?.let { " - $it" } ?: ""}"
                return Triple(epId, epTitle, true)
            }
            
            // Try to find episode 1 of next season
            val nextSeasonFirstEpisode = videos.find { 
                it.season == currentSeason + 1 && it.episode == 1 
            }
            
            if (nextSeasonFirstEpisode != null) {
                val epId = "${seriesId}:${nextSeasonFirstEpisode.season}:${nextSeasonFirstEpisode.episode}"
                val epTitle = "${tvShow.name} - S${nextSeasonFirstEpisode.season}:E${nextSeasonFirstEpisode.episode}${nextSeasonFirstEpisode.title?.let { " - $it" } ?: ""}"
                return Triple(epId, epTitle, true)
            }
        }
        
        // Fallback: just increment episode number (assume same season)
        return Triple("${seriesId}:${currentSeason}:${currentEpisode + 1}", null, false)
    }
    
    fun loadStreams(metaId: String, type: String = "movie", season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _groupedStreams.value = emptyMap()
            _streamLoadingState.value = StreamLoadingState.Loading
            
            // Parse season/episode from metaId if present (format: seriesId:season:episode)
            var targetSeason = season
            var targetEpisode = episode
            val actualMetaId = when {
                metaId.contains(":") && type == "series" -> {
                    val parts = metaId.split(":")
                    if (parts.size >= 3) {
                        targetSeason = parts[1].toIntOrNull()
                        targetEpisode = parts[2].toIntOrNull()
                        parts[0]
                    } else metaId
                }
                else -> metaId
            }
            
            // Store current season/episode for torrent processing
            _currentSeason = targetSeason
            _currentEpisode = targetEpisode
            
            // Build the proper ID for TV episodes
            // Format: seriesId:season:episode (e.g., tt0944947:1:1)
            val streamId = when {
                type == "series" && targetSeason != null && targetEpisode != null -> "$actualMetaId:$targetSeason:$targetEpisode"
                type == "series" && metaId.contains(":") -> metaId // Already has season:episode
                else -> actualMetaId
            }
            
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "=== LOAD STREAMS ===")
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Request: metaId=$actualMetaId, type=$type, season=$targetSeason, episode=$targetEpisode")
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Stream ID: $streamId")
            
            // Get preferred languages for filtering
            val preferredLangs = _preferredLanguages.value
            val isEnglishPreferred = preferredLangs.contains("en") && preferredLangs.size == 1
            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Preferred languages: $preferredLangs (English only: $isEnglishPreferred)")
            
            // Build addon URLs with language filters
            // Comet format: /language=de,en/ or /language=de/
            // Torrentio: supports language param in path
            val langParam = preferredLangs.joinToString(",")
            
            // Define addon configurations
            val addonConfigs = buildList {
                // Torrentio variants
                add(Triple(
                    "${Constants.Addons.TORRENTIO_BASE}${if (!isEnglishPreferred) "language=$langParam/" else ""}", 
                    "Torrentio", 
                    "torrentio"
                ))
                add(Triple(
                    "${Constants.Addons.TORRENTIO_BASE}${if (!isEnglishPreferred) "language=$langParam|" else ""}sort=qualitysize|asc/", 
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
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "=== LANGUAGE FILTERING ===")
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Preferred: $preferredLangs, Total streams: ${allStreams.size}")
                
                // Separate streams by language match
                val langMatchedStreams = mutableListOf<Pair<Stream, Int>>()  // Stream to priority score
                val otherStreams = mutableListOf<Stream>()
                var filteredCount = 0
                
                allStreams.forEachIndexed { index, stream ->
                    val title = stream.title ?: ""
                    val name = stream.name ?: ""
                    val combinedText = "${title.uppercase()} ${name.uppercase()}"
                    
                    // Use consistent language detection from Stream model
                    val detectedLangs = stream.getAudioLanguages()
                    
                    // AGGRESSIVE filtering: If English is the ONLY preferred language, 
                    // completely exclude non-English streams
                    val isNonEnglish = detectedLangs.isNotEmpty() && 
                        !(detectedLangs.contains("en") && detectedLangs.size == 1) &&
                        !(detectedLangs.contains("en") && detectedLangs.size > 1 && !isEnglishPreferred)
                    
                    if (isEnglishPreferred && isNonEnglish) {
                        filteredCount++
                        if (index < 10) {
                            android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Stream #$index: FILTERED OUT (lang=${detectedLangs}): ${title.take(50)}...")
                        }
                        return@forEachIndexed  // Skip this stream entirely
                    }
                    
                    // Check for explicit English markers
                    val hasEnglishMarker = combinedText.let { t ->
                        Regex("[ ._\\-\\[\\(](ENG|ENGLISH|ENG-AUDIO)[ ._\\-\\]\\)]").find(t) != null ||
                        Regex("[ ._\\-\\[\\(]EN[ ._\\-\\[\\(]?(US|GB|CA|UK|AU)[ ._\\-\\]\\)]").find(t) != null ||
                        Regex("\\b(ENG|ENGLISH)\\b").find(t) != null
                    }
                    
                    // English detection
                    val isEnglish = when {
                        detectedLangs.contains("en") && detectedLangs.size == 1 -> true
                        detectedLangs.contains("en") && detectedLangs.size > 1 -> true
                        detectedLangs.isEmpty() && hasEnglishMarker -> true
                        detectedLangs.isEmpty() -> true  // Unknown = assume English for English preference
                        else -> false
                    }
                    
                    val matchesPreference = preferredLangs.any { lang ->
                        when (lang) {
                            "en" -> isEnglish
                            else -> detectedLangs.contains(lang)
                        }
                    }
                    
                    // Check if this is a multi-audio torrent
                    val isMultiAudio = combinedText.let { t ->
                        Regex("\\b(MULTI|DUAL|MULTI-AUDIO|DUAL-AUDIO)\\b", RegexOption.IGNORE_CASE).find(t) != null ||
                        Regex("\\b(MULTI\\s*DL|DUAL\\s*DL)\\b", RegexOption.IGNORE_CASE).find(t) != null
                    }
                    
                    // Calculate episode match score for TV shows
                    val episodeScore = if (type == "series" && targetSeason != null && targetEpisode != null) {
                        stream.getEpisodeMatchScore(targetSeason, targetEpisode)
                    } else 100  // Movies always get max score
                    
                    if (index < 20) {
                        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Stream #$index: ${title.take(50)}...")
                        android.util.Log.d(Constants.LogTags.VIEW_MODEL, "  -> langs=$detectedLangs, isEnglish=$isEnglish, multi=$isMultiAudio, epScore=$episodeScore")
                    }
                    
                    if (matchesPreference && episodeScore > 0) {
                        // Priority score: episode match, then pure English (no multi), then quality, then seeders
                        val qualityScore = FormatUtils.extractQualityScore(title)
                        val seederScore = extractSeeders(title)
                        
                        // When English is preferred, deprioritize MULTI torrents
                        // Pure English = +50 bonus, Multi with English = 0, Others = -50
                        val languageBonus = when {
                            isEnglishPreferred && isEnglish && !isMultiAudio -> 50  // Pure English preferred
                            isEnglishPreferred && isEnglish && isMultiAudio -> 0    // Multi with English
                            else -> 0
                        }
                        
                        // Combine: episode match is most important (100 = exact, 50 = season pack)
                        val priority = episodeScore * 1000000 + languageBonus * 10000 + qualityScore * 1000 + seederScore
                        langMatchedStreams.add(stream to priority)
                    } else {
                        otherStreams.add(stream)
                    }
                }
                
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "RESULT: Matched=${langMatchedStreams.size}, Filtered=$filteredCount, Other=${otherStreams.size}")
                
                // Sort language-matched streams by priority score (descending)
                val sortedLangMatched = langMatchedStreams
                    .sortedByDescending { it.second }
                    .map { it.first }
                
                // Sort other streams by quality and seeders
                val sortedOther = otherStreams.sortedWith(
                    compareByDescending<Stream> { FormatUtils.extractQualityScore(it.title) }
                        .thenByDescending { extractSeeders(it.title) }
                )
                
                // Combine: preferred language streams first, then others
                val allSortedStreams = sortedLangMatched + sortedOther
                
                // Group by quality for folder display
                val qualityGroups = allSortedStreams
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
        posterUrl: String? = null,
        onUrlReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _torrentProcessingState.value = TorrentProcessingState(
                isProcessing = true,
                statusMessage = "Adding to Real-Debrid...",
                posterUrl = posterUrl,
                title = title
            )
            
            try {
                val apiKey = _realDebridKey.value
                if (apiKey.isBlank()) {
                    _torrentProcessingState.value = TorrentProcessingState(
                        isProcessing = false,
                        statusMessage = "Real-Debrid not configured"
                    )
                    _error.value = "Real-Debrid API key not configured.\n\nPlease go to Settings and add your Real-Debrid API token."
                    return@launch
                }
                
                if (realDebridManager == null) {
                    realDebridManager = RealDebridManager(apiKey)
                }
                
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Processing torrent: $infoHash for S${_currentSeason}E${_currentEpisode}")
                _torrentProcessingState.value = _torrentProcessingState.value.copy(
                    statusMessage = "Adding torrent to Real-Debrid..."
                )
                val magnet = "magnet:?xt=urn:btih:$infoHash"
                
                _torrentProcessingState.value = _torrentProcessingState.value.copy(
                    statusMessage = "Waiting for download to start..."
                )
                val streamingUrl = realDebridManager?.getStreamingUrl(magnet, _currentSeason, _currentEpisode)
                    ?: throw RealDebridException("Failed to get streaming URL")
                
                android.util.Log.d(Constants.LogTags.VIEW_MODEL, "Got streaming URL successfully")
                _torrentProcessingState.value = TorrentProcessingState()
                _error.value = null
                onUrlReady(streamingUrl)
                
            } catch (e: RealDebridException) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Real-Debrid error: ${e.message}", e)
                _torrentProcessingState.value = TorrentProcessingState(
                    isProcessing = false,
                    statusMessage = e.message ?: "Real-Debrid error"
                )
                _error.value = e.message ?: "Real-Debrid error"
            } catch (e: Exception) {
                android.util.Log.e(Constants.LogTags.VIEW_MODEL, "Error processing torrent: ${e.message}", e)
                _torrentProcessingState.value = TorrentProcessingState(
                    isProcessing = false,
                    statusMessage = e.message ?: "Unknown error"
                )
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
                        realDebridManager?.getStreamingUrl(magnet, _currentSeason, _currentEpisode)
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
    
    /**
     * Check if title has explicit non-English language markers
     */
    private fun hasNonEnglishLanguageMarker(title: String?): Boolean {
        if (title == null) return false
        val upper = title.uppercase()
        
        // Common non-English markers in torrent names - be very strict
        val patterns = listOf(
            // French
            Regex("\\b(FRA|FRE|FRENCH|FRANCAIS|VF|VFF|VFQ)\\b"),
            // German  
            Regex("\\b(GER|DEU|GERMAN|DEUTSCH)\\b"),
            // Spanish
            Regex("\\b(SPA|ESP|SPANISH|ESPANOL|LATINO|LAT)\\b"),
            // Italian
            Regex("\\b(ITA|ITALIAN|ITALIANO)\\b"),
            // Portuguese
            Regex("\\b(POR|PORTUGUESE|PORTUGUES)\\b"),
            // Russian
            Regex("\\b(RUS|RUSSIAN)\\b"),
            // Japanese
            Regex("\\b(JPN|JAP|JAPANESE)\\b"),
            // Korean
            Regex("\\b(KOR|KOREAN)\\b"),
            // Chinese
            Regex("\\b(CHI|CHN|CHINESE)\\b"),
            // Polish
            Regex("\\b(POL|POLISH|POLSKI)\\b"),
            // Dutch
            Regex("\\b(DUT|DUTCH|NEDERLANDS)\\b"),
            // Turkish
            Regex("\\b(TUR|TURKISH)\\b"),
            // Arabic
            Regex("\\b(ARA|ARABIC)\\b")
        )
        
        return patterns.any { it.find(upper) != null }
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
