package com.stremioclone.realdebrid

import com.stremioclone.model.TorrentInfo
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
    
    suspend fun getUnrestrictedLink(link: String): String {
        val response = api.unrestrictLink(getAuthHeader(), link)
        return response.download
    }
    
    suspend fun getStreamingUrl(magnetLink: String): String {
        try {
            // Add magnet
            val torrentId = addMagnet(magnetLink)
            
            // Select all files
            selectAllFiles(torrentId)
            
            // Wait for download
            val info = waitForDownload(torrentId)
            
            // Get unrestricted link
            val link = info.links?.firstOrNull()
                ?: throw RealDebridException("No downloadable links found in torrent")
            
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
    
    companion object {
        const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"
    }
}

class RealDebridException(message: String) : Exception(message)
