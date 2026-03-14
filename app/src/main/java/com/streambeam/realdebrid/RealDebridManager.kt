package com.streambeam.realdebrid

import com.streambeam.model.TorrentInfo
import com.streambeam.model.TorrentFile
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RealDebridManager(private val apiKey: String) {
    
    private val api: RealDebridApi
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(RealDebridApi::class.java)
    }
    
    private fun getAuthHeader(): String = "Bearer $apiKey"
    
    suspend fun getUserInfo() = api.getUserInfo(getAuthHeader())
    
    suspend fun verifyAccount(): String {
        return try {
            val userInfo = getUserInfo()
            val type = userInfo.type
            val premium = userInfo.premium > 0
            if (type == "premium" || premium) {
                "Premium account verified"
            } else {
                "Free account - torrents require premium"
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> "Invalid API key"
                else -> "Error: ${e.code()}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    suspend fun addMagnet(magnetLink: String): String {
        val response = api.addMagnet(getAuthHeader(), magnetLink)
        return response.id
    }
    
    suspend fun getTorrentInfo(torrentId: String): TorrentInfo {
        return api.getTorrentInfo(getAuthHeader(), torrentId)
    }
    
    suspend fun waitForDownload(torrentId: String, maxAttempts: Int = 60): TorrentInfo {
        var attempts = 0
        var info = getTorrentInfo(torrentId)
        
        while (info.status != "downloaded" && attempts < maxAttempts) {
            delay(2000) // Wait 2 seconds
            info = getTorrentInfo(torrentId)
            attempts++
            
            // Check for error states
            when (info.status) {
                "magnet_error" -> throw RealDebridException("Failed to convert magnet link")
                "error" -> throw RealDebridException("Torrent processing error")
                "virus" -> throw RealDebridException("File contains virus/malware")
                "dead" -> throw RealDebridException("Torrent has no seeders (dead)")
            }
        }
        
        if (info.status != "downloaded") {
            throw RealDebridException("Timeout waiting for download. Status: ${info.status}")
        }
        
        return info
    }
    
    suspend fun selectAllFiles(torrentId: String) {
        val response = api.selectFiles(getAuthHeader(), torrentId, "all")
        if (!response.isSuccessful) {
            throw RealDebridException("Failed to select files: ${response.code()}")
        }
    }
    
    suspend fun selectSpecificFiles(torrentId: String, fileIds: String) {
        val response = api.selectFiles(getAuthHeader(), torrentId, fileIds)
        if (!response.isSuccessful) {
            throw RealDebridException("Failed to select files: ${response.code()}")
        }
    }
    
    suspend fun getUnrestrictedLink(link: String): String {
        val response = api.unrestrictLink(getAuthHeader(), link)
        return response.download
    }
    
    /**
     * Find the best matching file for a specific episode from a season pack
     */
    private fun findEpisodeFileId(files: List<TorrentFile>?, season: Int?, episode: Int?): Int? {
        if (files.isNullOrEmpty()) return null
        if (season == null || episode == null) return null
        
        val seasonStr = season.toString().padStart(2, '0')
        val episodeStr = episode.toString().padStart(2, '0')
        
        // Patterns to match episode files
        val patterns = listOf(
            // S01E02
            Regex("[Ss]${seasonStr}[Ee]${episodeStr}\\b"),
            // 1x02
            Regex("\\b${season}x${episodeStr}\\b"),
            // Season 1 Episode 2
            Regex("[Ss]eason[^0-9]*$season[^0-9]*[Ee]pisode[^0-9]*$episode\\b"),
            // Ep 02
            Regex("[Ee]p[^0-9]*${episodeStr}\\b")
        )
        
        // Find video files only
        val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm")
        val videoFiles = files.filter { file ->
            videoExtensions.any { ext -> file.path.endsWith(ext, ignoreCase = true) }
        }
        
        // First try: Look for exact S01E02 match
        for (pattern in patterns) {
            val match = videoFiles.find { pattern.find(it.path) != null }
            if (match != null) {
                android.util.Log.d("RealDebridManager", "Found episode file: ${match.path}")
                return match.id
            }
        }
        
        // Second try: Look for any file with the episode number
        val loosePattern = Regex("[^0-9]${episodeStr}[^0-9]|")
        val match = videoFiles.find { loosePattern.find(it.path) != null }
        if (match != null) {
            android.util.Log.d("RealDebridManager", "Found episode file (loose match): ${match.path}")
            return match.id
        }
        
        // Fallback: Return the largest file (likely the main content)
        return videoFiles.maxByOrNull { it.bytes }?.id
    }
    
    suspend fun getStreamingUrl(magnetLink: String, season: Int? = null, episode: Int? = null): String {
        try {
            // Add magnet
            val torrentId = addMagnet(magnetLink)
            
            // Wait for magnet conversion to get file list
            var info = getTorrentInfo(torrentId)
            var attempts = 0
            while (info.status == "magnet_conversion" && attempts < 30) {
                delay(1000)
                info = getTorrentInfo(torrentId)
                attempts++
            }
            
            // If we have season/episode, try to find the specific file
            val targetFileId = if (season != null && episode != null) {
                findEpisodeFileId(info.files, season, episode)
            } else null
            
            // Select files
            if (targetFileId != null) {
                selectSpecificFiles(torrentId, targetFileId.toString())
                android.util.Log.d("RealDebridManager", "Selected specific file ID: $targetFileId for S${season}E$episode")
            } else {
                selectAllFiles(torrentId)
            }
            
            // Wait for download
            info = waitForDownload(torrentId)
            
            // Get unrestricted link
            val link = if (targetFileId != null && info.links != null) {
                // Try to find the link corresponding to our selected file
                // Real-Debrid returns links in order of selected files
                info.links.firstOrNull()
            } else {
                info.links?.firstOrNull()
            } ?: throw RealDebridException("No downloadable links found in torrent")
            
            return getUnrestrictedLink(link)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> throw RealDebridException("Invalid API key. Please check your Real-Debrid token.")
                403 -> throw RealDebridException("Account restricted or torrent blacklisted")
                404 -> throw RealDebridException("API endpoint not found (404). You may need a premium Real-Debrid account to use torrents. Free accounts don't support torrent caching.")
                503 -> throw RealDebridException("Real-Debrid service is temporarily unavailable")
                else -> throw RealDebridException("Real-Debrid API error: ${e.code()}")
            }
        } catch (e: java.net.SocketTimeoutException) {
            throw RealDebridException("Connection timed out. Torrent may still be downloading.")
        } catch (e: RealDebridException) {
            throw e
        } catch (e: Exception) {
            throw RealDebridException("Error: ${e.message ?: "Unknown error"}")
        }
    }
    
    // Backward compatibility
    suspend fun getStreamingUrl(magnetLink: String): String {
        return getStreamingUrl(magnetLink, null, null)
    }
    
    companion object {
        const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"
    }
}

class RealDebridException(message: String) : Exception(message)
