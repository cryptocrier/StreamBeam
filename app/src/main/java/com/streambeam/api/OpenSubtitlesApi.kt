package com.streambeam.api

import com.streambeam.model.OpenSubtitlesDownloadResponse
import com.streambeam.model.OpenSubtitlesSearchResponse
import com.streambeam.model.Subtitle
import com.streambeam.model.SubtitleSearchRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles API v1 integration
 * 
 * Note: This uses the free API. For production, you should:
 * 1. Register at https://www.opensubtitles.com/en/consumers
 * 2. Get an API key
 * 3. Implement proper authentication
 */
interface OpenSubtitlesApi {
    
    /**
     * Search for subtitles
     */
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Query("query") query: String? = null,
        @Query("languages") languages: String? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("season_number") season: Int? = null,
        @Query("episode_number") episode: Int? = null,
        @Query("year") year: Int? = null,
        @Query("moviehash") movieHash: String? = null,
        @Query("moviebytesize") movieBytes: Long? = null,
        @Query("hearing_impaired") hearingImpaired: String? = null,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<OpenSubtitlesSearchResponse>
    
    /**
     * Get download link for a subtitle
     */
    @POST("download")
    suspend fun downloadSubtitle(
        @Header("Api-Key") apiKey: String,
        @Body request: DownloadRequest
    ): Response<OpenSubtitlesDownloadResponse>
    
    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
        
        // Free API key (limited usage). For production, get your own at:
        // https://www.opensubtitles.com/en/consumers
        const val DEFAULT_API_KEY = "YOUR_API_KEY_HERE"
        
        fun create(): OpenSubtitlesApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenSubtitlesApi::class.java)
        }
    }
}

data class DownloadRequest(
    val file_id: Int,
    val sub_format: String = "srt"
)

/**
 * Repository for subtitle operations
 */
class SubtitleRepository private constructor() {
    
    private val api = OpenSubtitlesApi.create()
    private var apiKey = OpenSubtitlesApi.DEFAULT_API_KEY
    
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    /**
     * Search for subtitles based on request parameters
     */
    suspend fun searchSubtitles(request: SubtitleSearchRequest): List<Subtitle> {
        return try {
            val response = api.searchSubtitles(
                apiKey = apiKey,
                query = request.query,
                languages = request.languages.joinToString(","),
                imdbId = request.imdbId,
                tmdbId = request.tmdbId,
                season = request.season,
                episode = request.episode,
                year = request.year,
                movieHash = request.movieHash,
                movieBytes = request.movieBytes
            )
            
            if (response.isSuccessful) {
                response.body()?.data?.map { data ->
                    val attrs = data.attributes
                    val file = attrs.files?.firstOrNull()
                    Subtitle(
                        id = data.id,
                        language = attrs.language,
                        languageName = getLanguageName(attrs.language),
                        filename = file?.fileName ?: attrs.featureDetails?.title ?: "Unknown",
                        isHearingImpaired = attrs.hearingImpaired ?: false,
                        score = attrs.ratings ?: 0.0,
                        votes = attrs.votes ?: 0,
                        downloadCount = attrs.downloadCount ?: 0
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleRepository", "Error searching subtitles: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get download URL for a subtitle
     */
    suspend fun getDownloadUrl(fileId: Int): String? {
        return try {
            val response = api.downloadSubtitle(
                apiKey = apiKey,
                request = DownloadRequest(file_id = fileId)
            )
            
            if (response.isSuccessful) {
                response.body()?.link
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleRepository", "Error getting download URL: ${e.message}")
            null
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
            else -> code.uppercase()
        }
    }
    
    companion object {
        @Volatile
        private var instance: SubtitleRepository? = null
        
        fun getInstance(): SubtitleRepository {
            return instance ?: synchronized(this) {
                instance ?: SubtitleRepository().also { instance = it }
            }
        }
    }
}
