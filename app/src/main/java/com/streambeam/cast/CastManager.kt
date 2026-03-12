package com.streambeam.cast

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.streambeam.utils.Constants
import com.streambeam.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * CastManager handles Google Cast functionality for the app.
 * 
 * IMPORTANT - Audio Codec Compatibility:
 * 
 * Older Chromecast devices (1st/2nd gen, Chromecast Audio) have limited audio codec support:
 * - Supported: AAC (up to 48kHz), MP3, MP2, PCM, Vorbis
 * - NOT supported: E-AC3 (Dolby Digital Plus), DTS, DTS-HD, TrueHD, high-bitrate AAC
 * 
 * Newer Chromecast devices (Ultra, 3rd gen, Chromecast with Google TV) and built-in TV cast
 * receivers typically support all common audio codecs including E-AC3 and DTS.
 * 
 * If users experience video but no audio on older Chromecasts, the stream likely contains
 * an unsupported audio codec. The video source would need to be transcoded to AAC audio
 * for full compatibility with older devices.
 * 
 * The app now includes:
 * - Device capability detection (isOlderChromecastDevice)
 * - Detailed logging for debugging cast issues
 * - Error callbacks to identify codec/load failures
 */
class CastManager(private val context: Context) {
    
    private val castContext = CastContext.getSharedInstance(context)
    private val sessionManager = castContext.sessionManager
    
    // Coroutine scope for async operations (replaces Handler)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    var exoPlayer: ExoPlayer? = null
        private set
    private var mediaSession: MediaSession? = null
    
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            android.util.Log.d(Constants.LogTags.CAST, "Session starting...")
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            android.util.Log.e(Constants.LogTags.CAST, "Session start failed: $error")
            _isConnected.value = false
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            android.util.Log.d(Constants.LogTags.CAST, "Session started: $sessionId")
            _isConnected.value = true
            // Auto-switch current playback to cast after a short delay
            coroutineScope.launch {
                delay(Constants.Delays.CAST_SWITCH)
                switchToCast()
            }
        }
        override fun onSessionEnding(session: CastSession) {
            android.util.Log.d(Constants.LogTags.CAST, "Session ending...")
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            android.util.Log.d(Constants.LogTags.CAST, "Session ended: $error")
            _isConnected.value = false
            _isCastingActive.value = false
            // Switch back to local playback
            switchToLocal()
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            android.util.Log.d(Constants.LogTags.CAST, "Session resuming...")
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            android.util.Log.e(Constants.LogTags.CAST, "Session resume failed: $error")
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            android.util.Log.d(Constants.LogTags.CAST, "Session resumed")
            _isConnected.value = true
            coroutineScope.launch {
                delay(Constants.Delays.CAST_SWITCH)
                switchToCast()
            }
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            android.util.Log.d(Constants.LogTags.CAST, "Session suspended: $reason")
            _isConnected.value = false
        }
    }
    
    init {
        sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        _isConnected.value = sessionManager.currentCastSession != null
    }
    
    fun initializePlayer(): ExoPlayer {
        // Return existing player if already initialized
        exoPlayer?.let { return it }
        
        // Use OkHttp with proper headers for Real-Debrid
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(Constants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.Network.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(Constants.Network.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(Constants.Network.USER_AGENT)
        
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        // Optimize load control for faster startup
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Constants.Buffer.MIN_BUFFER_MS,
                Constants.Buffer.MAX_BUFFER_MS,
                Constants.Buffer.PLAYBACK_MS,
                Constants.Buffer.REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        // Default renderers - hardware decoding (may fail on 4K/HDR in emulator)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        
        val newPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addAnalyticsListener(EventLogger())
            }
        
        exoPlayer = newPlayer
        return newPlayer
    }
    
    // Store current media info for switching between local and cast
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var currentPosterUrl: String? = null
    
    // Track if we have an active cast session with media loaded
    private val _isCastingActive = MutableStateFlow(false)
    val isCastingActive: StateFlow<Boolean> = _isCastingActive
    
    // Track available audio tracks and current selection
    private val _availableAudioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val availableAudioTracks: StateFlow<List<AudioTrack>> = _availableAudioTracks
    
    private val _currentAudioTrackId = MutableStateFlow<Long?>(null)
    val currentAudioTrackId: StateFlow<Long?> = _currentAudioTrackId
    
    data class AudioTrack(
        val id: Long,
        val name: String,
        val language: String,
        val isEnglish: Boolean
    )
    
    fun castVideo(
        url: String,
        title: String,
        subtitle: String? = null,
        posterUrl: String? = null,
        durationMs: Long = 0
    ) {
        // Store for potential switching later
        currentUrl = url
        currentTitle = title
        currentPosterUrl = posterUrl
        
        val remoteMediaClient = sessionManager.currentCastSession?.remoteMediaClient
            ?: return
        
        val castDevice = sessionManager.currentCastSession?.castDevice
        val deviceVersion = castDevice?.deviceVersion ?: "unknown"
        val deviceName = castDevice?.friendlyName ?: "unknown"
        
        android.util.Log.d(Constants.LogTags.CAST, "Casting to device: $deviceName (version: $deviceVersion)")
        android.util.Log.d(Constants.LogTags.CAST, "Detected languages: $detectedAudioLanguages")
        
        val mediaMetadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(CastMediaMetadata.KEY_TITLE, title)
            subtitle?.let { putString(CastMediaMetadata.KEY_SUBTITLE, it) }
            posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        
        // Detect content type for casting - use MP4 as default for better compatibility
        val contentType = getMimeTypeFromUrl(url) ?: "video/mp4"
        
        // Build media info with proper configuration for audio/video
        val customData = org.json.JSONObject().apply {
            put("url", url)
            put("title", title)
            put("languages", detectedAudioLanguages.joinToString(","))
        }
        
        // Build MediaInfo with additional metadata for better compatibility
        val mediaInfoBuilder = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(mediaMetadata)
            .setCustomData(customData)
        
        // Set stream duration if known (helps with seeking and progress display)
        if (durationMs > 0) {
            mediaInfoBuilder.setStreamDuration(durationMs)
        }
        
        val mediaInfo = mediaInfoBuilder.build()
        
        // Build load request with active track IDs if we have detected English
        val loadRequestBuilder = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
        
        val mediaLoadRequestData = loadRequestBuilder.build()
        
        // Pause local playback before casting (don't stop to avoid state issues)
        exoPlayer?.pause()
        
        // Mark casting as active
        _isCastingActive.value = true
        
        // Load with result callback to handle errors
        remoteMediaClient.load(mediaLoadRequestData)?.setResultCallback { result ->
            if (result.status.isSuccess) {
                android.util.Log.d(Constants.LogTags.CAST, "Media loaded successfully on cast device")
                // Wait for tracks to be available, then update and auto-select English
                coroutineScope.launch {
                    delay(Constants.Delays.AUDIO_TRACK_UPDATE)
                    updateAvailableAudioTracks()
                }
            } else {
                val errorCode = result.status.statusCode
                val errorMessage = result.status.statusMessage
                android.util.Log.e(Constants.LogTags.CAST, "Failed to load media on cast device: $errorCode - $errorMessage")
                
                // Log specific error information for debugging
                when (errorCode) {
                    com.google.android.gms.cast.CastStatusCodes.TIMEOUT -> {
                        android.util.Log.e(Constants.LogTags.CAST, "Cast timeout - device may not support this media format")
                    }
                    com.google.android.gms.cast.CastStatusCodes.NETWORK_ERROR -> {
                        android.util.Log.e(Constants.LogTags.CAST, "Network error during cast")
                    }
                    com.google.android.gms.cast.CastStatusCodes.ERROR -> {
                        android.util.Log.e(Constants.LogTags.CAST, "General cast error - possibly incompatible audio/video codec")
                    }
                }
            }
        }
    }
    
    /**
     * Switch currently playing content to cast
     */
    fun switchToCast() {
        android.util.Log.d(Constants.LogTags.CAST, "Switching to cast - url: $currentUrl, title: $currentTitle, connected: ${_isConnected.value}")
        
        if (!_isConnected.value) {
            android.util.Log.w(Constants.LogTags.CAST, "Cannot cast: not connected to cast session")
            return
        }
        
        currentUrl?.let { url ->
            currentTitle?.let { title ->
                val deviceName = sessionManager.currentCastSession?.castDevice?.friendlyName ?: "Unknown"
                android.util.Log.d(Constants.LogTags.CAST, "Casting: $title to $deviceName")
                
                // Log device capabilities for debugging
                logDeviceCapabilities()
                
                castVideo(url, title, null, currentPosterUrl, getCurrentPosition())
            } ?: android.util.Log.w(Constants.LogTags.CAST, "Cannot cast: title is null")
        } ?: android.util.Log.w(Constants.LogTags.CAST, "Cannot cast: url is null")
    }
    
    /**
     * Log device capabilities for debugging audio issues
     */
    private fun logDeviceCapabilities() {
        val castDevice = sessionManager.currentCastSession?.castDevice
        castDevice?.let { device ->
            android.util.Log.d(Constants.LogTags.CAST, "Device Info:")
            android.util.Log.d(Constants.LogTags.CAST, "  - Name: ${device.friendlyName}")
            android.util.Log.d(Constants.LogTags.CAST, "  - Version: ${device.deviceVersion}")
            android.util.Log.d(Constants.LogTags.CAST, "  - Model: ${device.modelName}")
            
            // Detect if this is an older Chromecast that may have audio issues
            val isOlderChromecast = isOlderChromecastDevice(device)
            android.util.Log.d(Constants.LogTags.CAST, "  - Is older Chromecast (potential audio issues): $isOlderChromecast")
        }
    }
    
    /**
     * Detect if the connected device is an older Chromecast with limited audio codec support
     */
    private fun isOlderChromecastDevice(device: com.google.android.gms.cast.CastDevice): Boolean {
        val modelName = device.modelName?.lowercase() ?: ""
        val deviceVersion = device.deviceVersion?.lowercase() ?: ""
        
        // Chromecast 1st gen (2013) - model names: Chromecast, D2C
        // Chromecast 2nd gen (2015) - model names: Chromecast, NC2
        // Chromecast Audio (2015) - model names: Chromecast Audio, RUX
        // Newer devices have different model names
        return when {
            // 1st gen Chromecast (2013) - most limited audio support
            modelName.contains("chromecast") && (deviceVersion.contains("1.") || device.friendlyName?.contains("Chromecast") == true && deviceVersion.isEmpty()) -> true
            
            // Check for specific older model indicators
            deviceVersion.startsWith("1.") -> true
            
            // Chromecast Audio is 1st/2nd gen era but audio-focused
            modelName.contains("audio") -> true
            
            else -> false
        }
    }
    
    /**
     * Get current playback position
     */
    fun getCurrentPosition(): Long {
        return if (_isConnected.value) {
            sessionManager.currentCastSession?.remoteMediaClient?.approximateStreamPosition ?: 0
        } else {
            exoPlayer?.currentPosition ?: 0
        }
    }
    
    /**
     * Switch currently playing content to local playback
     */
    fun switchToLocal() {
        android.util.Log.d(Constants.LogTags.CAST, "Switching to local - url: $currentUrl, title: $currentTitle")
        currentUrl?.let { url ->
            currentTitle?.let { title ->
                android.util.Log.d(Constants.LogTags.CAST, "Playing locally: $title")
                playLocally(url, title, currentPosterUrl)
            } ?: android.util.Log.w(Constants.LogTags.CAST, "Cannot play locally: title is null")
        } ?: android.util.Log.w(Constants.LogTags.CAST, "Cannot play locally: url is null")
    }
    
    /**
     * Check if we have current media to cast
     */
    fun hasCurrentMedia(): Boolean = currentUrl != null && currentTitle != null
    
    /**
     * Manually trigger cast of current media
     */
    fun castCurrentMedia() {
        android.util.Log.d(Constants.LogTags.CAST, "Manually casting current media")
        switchToCast()
    }
    
    /**
     * Get information about the connected cast device for debugging
     */
    fun getConnectedDeviceInfo(): String {
        val castDevice = sessionManager.currentCastSession?.castDevice
        return if (castDevice != null) {
            val isOlder = isOlderChromecastDevice(castDevice)
            """Device: ${castDevice.friendlyName}
                |Model: ${castDevice.modelName}
                |Version: ${castDevice.deviceVersion}
                |Older Device (limited audio): $isOlder""".trimMargin()
        } else {
            "No cast device connected"
        }
    }
    
    /**
     * Check if cast session is ready for playback
     */
    fun isCastSessionReady(): Boolean {
        val session = sessionManager.currentCastSession
        val ready = session != null && session.isConnected
        android.util.Log.d(Constants.LogTags.CAST, "Cast session ready: $ready (session: $session)")
        return ready
    }
    
    /**
     * Get the RemoteMediaClient for controlling cast playback
     */
    fun getRemoteMediaClient(): RemoteMediaClient? {
        return sessionManager.currentCastSession?.remoteMediaClient
    }
    
    fun playLocally(url: String, title: String, posterUrl: String? = null) {
        // Store for potential switching later
        currentUrl = url
        currentTitle = title
        currentPosterUrl = posterUrl
        
        exoPlayer?.let { player ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(title)
                .apply {
                    posterUrl?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()
            
            // Detect MIME type from file extension for proper format support
            val mimeType = getMimeTypeFromUrl(url)
            
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(mediaMetadata)
                .apply {
                    // Set MIME type if detected to help ExoPlayer choose correct extractor
                    mimeType?.let { setMimeType(it) }
                }
                .build()
            
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }
    
    private fun getMimeTypeFromUrl(url: String): String? {
        return when {
            url.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            url.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            url.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            url.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            url.endsWith(".webm", ignoreCase = true) -> "video/webm"
            url.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
            url.endsWith(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            url.endsWith(".mpd", ignoreCase = true) -> "application/dash+xml"
            else -> null // Let ExoPlayer auto-detect
        }
    }
    
    fun pause() {
        if (_isConnected.value) {
            sessionManager.currentCastSession?.remoteMediaClient?.pause()
        } else {
            exoPlayer?.pause()
        }
    }
    
    fun resume() {
        if (_isConnected.value) {
            sessionManager.currentCastSession?.remoteMediaClient?.play()
        } else {
            exoPlayer?.play()
        }
    }
    
    fun seekTo(positionMs: Long) {
        if (_isConnected.value) {
            sessionManager.currentCastSession?.remoteMediaClient?.seek(positionMs)
        } else {
            exoPlayer?.seekTo(positionMs)
        }
    }
    
    /**
     * Stop cast playback and clear current media
     */
    fun stopCastPlayback() {
        // Stop local playback
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        
        // Stop cast playback if connected
        if (_isConnected.value) {
            sessionManager.currentCastSession?.remoteMediaClient?.stop()
        }
        
        // Clear stored media info
        currentUrl = null
        currentTitle = null
        currentPosterUrl = null
        _isCastingActive.value = false
        _availableAudioTracks.value = emptyList()
        _currentAudioTrackId.value = null
    }
    
    // Store detected audio tracks from stream metadata
    private var detectedAudioLanguages: List<String> = emptyList()
    
    /**
     * Set detected audio languages from stream metadata (before casting)
     */
    fun setDetectedAudioLanguages(languages: List<String>) {
        detectedAudioLanguages = languages
        android.util.Log.d(Constants.LogTags.CAST, "Detected audio languages: $languages")
    }
    
    /**
     * Get available audio tracks from the cast session
     * Note: This requires a custom receiver that supports track switching
     */
    fun updateAvailableAudioTracks() {
        val remoteMediaClient = sessionManager.currentCastSession?.remoteMediaClient
        val mediaStatus = remoteMediaClient?.mediaStatus
        
        if (mediaStatus != null) {
            val mediaInfo = mediaStatus.mediaInfo
            val tracks = mediaInfo?.mediaTracks
            
            android.util.Log.d(Constants.LogTags.CAST, "MediaInfo tracks: ${tracks?.size ?: 0}")
            
            if (tracks != null && tracks.isNotEmpty()) {
                val audioTracks = tracks
                    .filter { it.type == com.google.android.gms.cast.MediaTrack.TYPE_AUDIO }
                    .map { track ->
                        val lang = track.language?.uppercase() ?: "UNKNOWN"
                        val name = track.name ?: when {
                            lang == "EN" || lang == "ENG" || lang.startsWith("EN-") -> "English"
                            lang == "ES" -> "Spanish"
                            lang == "FR" -> "French"
                            lang == "DE" -> "German"
                            lang == "IT" -> "Italian"
                            lang == "PT" -> "Portuguese"
                            lang == "RU" -> "Russian"
                            lang == "JA" -> "Japanese"
                            else -> "Audio ${track.id}"
                        }
                        AudioTrack(
                            id = track.id,
                            name = name,
                            language = lang,
                            isEnglish = lang == "EN" || lang == "ENG" || lang.startsWith("EN-") || lang == "EN"
                        )
                    }
                
                _availableAudioTracks.value = audioTracks
                android.util.Log.d(Constants.LogTags.CAST, "Found ${audioTracks.size} audio tracks")
                audioTracks.forEach { track ->
                    android.util.Log.d(Constants.LogTags.CAST, "  Track ${track.id}: ${track.name} (${track.language})${if (track.isEnglish) " [ENGLISH]" else ""}")
                }
                
                // Auto-select English track if available and no track currently selected
                if (_currentAudioTrackId.value == null && audioTracks.isNotEmpty()) {
                    val englishTrack = audioTracks.firstOrNull { it.isEnglish }
                    if (englishTrack != null) {
                        android.util.Log.d(Constants.LogTags.CAST, "Auto-selecting English track: ${englishTrack.id}")
                        setActiveAudioTrack(englishTrack.id)
                    } else {
                        // Default to first track if no English found
                        android.util.Log.d(Constants.LogTags.CAST, "No English track found, using first track: ${audioTracks[0].id}")
                        _currentAudioTrackId.value = audioTracks[0].id
                    }
                }
            } else {
                // No tracks exposed by receiver - create from detected languages
                if (detectedAudioLanguages.isNotEmpty()) {
                    val fallbackTracks = detectedAudioLanguages.mapIndexed { index, lang ->
                        AudioTrack(
                            id = index.toLong(),
                            name = getLanguageName(lang),
                            language = lang.uppercase(),
                            isEnglish = lang == "en"
                        )
                    }
                    _availableAudioTracks.value = fallbackTracks
                    android.util.Log.d(Constants.LogTags.CAST, "Using ${fallbackTracks.size} fallback tracks from metadata")
                } else {
                    _availableAudioTracks.value = emptyList()
                }
            }
        }
    }
    
    private fun getLanguageName(code: String): String {
        return when (code.lowercase()) {
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
            else -> code.uppercase()
        }
    }
    
    /**
     * Set the active audio track by ID
     * Note: This requires a custom receiver that supports track switching
     */
    fun setActiveAudioTrack(trackId: Long) {
        val remoteMediaClient = sessionManager.currentCastSession?.remoteMediaClient
        if (remoteMediaClient != null) {
            android.util.Log.d(Constants.LogTags.CAST, "Setting active audio track: $trackId")
            remoteMediaClient.setActiveMediaTracks(longArrayOf(trackId))
                .setResultCallback { result ->
                    if (result.status.isSuccess) {
                        _currentAudioTrackId.value = trackId
                        android.util.Log.d(Constants.LogTags.CAST, "Successfully set audio track to: $trackId")
                    } else {
                        android.util.Log.e(Constants.LogTags.CAST, "Failed to set audio track: ${result.status.statusCode}")
                        // Still update UI to show selection attempt
                        _currentAudioTrackId.value = trackId
                    }
                }
        }
    }
    
    /**
     * Select English audio track if available
     */
    fun selectEnglishAudio() {
        val tracks = _availableAudioTracks.value
        val englishTrack = tracks.firstOrNull { it.isEnglish }
        if (englishTrack != null) {
            setActiveAudioTrack(englishTrack.id)
        }
    }
    
    fun release() {
        coroutineScope.cancel()
        stopCastPlayback()
        mediaSession?.release()
        exoPlayer?.release()
        exoPlayer = null
        sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }
    
    /**
     * Toggle cast connection state.
     * Returns true if a cast session was ended, false otherwise.
     */
    fun toggleCast(activity: android.app.Activity): Boolean {
        android.util.Log.d(Constants.LogTags.CAST, "Toggle cast - currently connected: ${_isConnected.value}")
        return if (_isConnected.value) {
            android.util.Log.d(Constants.LogTags.CAST, "Ending cast session")
            sessionManager.endCurrentSession(true)
            true
        } else {
            // Show the cast device chooser dialog
            android.util.Log.d(Constants.LogTags.CAST, "Showing cast chooser dialog")
            showCastChooserDialog(activity)
            false
        }
    }
    
    /**
     * Shows the cast device chooser dialog to allow user to select a cast device.
     */
    private fun showCastChooserDialog(activity: android.app.Activity) {
        try {
            android.util.Log.d(Constants.LogTags.CAST, "Opening cast device picker...")
            
            // Get the MediaRouteSelector from CastContext
            val selector = castContext.mergedSelector
                ?: androidx.mediarouter.media.MediaRouteSelector.Builder()
                    .addControlCategory(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                    .build()
            
            // Create and show the chooser dialog
            val dialog = androidx.mediarouter.app.MediaRouteChooserDialog(activity)
            dialog.routeSelector = selector
            dialog.show()
            
            android.util.Log.d(Constants.LogTags.CAST, "Cast dialog shown")
        } catch (e: Exception) {
            android.util.Log.e(Constants.LogTags.CAST, "Failed to show cast dialog: ${e.message}", e)
        }
    }
}
