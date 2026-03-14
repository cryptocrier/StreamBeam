package com.streambeam.addons

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB API integration for fetching TV episode data
 * Free API key from https://www.themoviedb.org/settings/api
 */
interface TmdbApi {
    
    @GET("tv/{series_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String = TMDB_API_KEY
    ): TmdbSeasonResponse
    
    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("external_source") externalSource: String = "imdb_id",
        @Query("api_key") apiKey: String = TMDB_API_KEY
    ): TmdbFindResponse
    
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbMovieListResponse
    
    @GET("tv/popular")
    suspend fun getPopularTVShows(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbTVListResponse
    
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("page") page: Int = 1
    ): TmdbMovieListResponse
    
    @GET("trending/tv/week")
    suspend fun getTrendingTVShows(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("page") page: Int = 1
    ): TmdbTVListResponse
}

// TMDB Data Models
data class TmdbSeasonResponse(
    @SerializedName("_id") val id: String?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episodes") val episodes: List<TmdbEpisode>?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("poster_path") val posterPath: String?
)

data class TmdbEpisode(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,           // Episode title
    @SerializedName("overview") val overview: String?,    // Episode description
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("still_path") val stillPath: String?, // Episode thumbnail
    @SerializedName("runtime") val runtime: Int?
) {
    fun getFullStillPath(): String? {
        return stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
    }
}

data class TmdbFindResponse(
    @SerializedName("tv_results") val tvResults: List<TmdbTvResult>?,
    @SerializedName("tv_episode_results") val tvEpisodeResults: List<Any>?,
    @SerializedName("tv_season_results") val tvSeasonResults: List<Any>?
)

data class TmdbTvResult(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?
)

// Popular/Trending Movies Response
data class TmdbMovieListResponse(
    @SerializedName("page") val page: Int?,
    @SerializedName("results") val results: List<TmdbMovieResult>,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("total_results") val totalResults: Int?
)

data class TmdbMovieResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

// Popular/Trending TV Shows Response
data class TmdbTVListResponse(
    @SerializedName("page") val page: Int?,
    @SerializedName("results") val results: List<TmdbTVResultDetailed>,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("total_results") val totalResults: Int?
)

data class TmdbTVResultDetailed(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

// TMDB API Key - Get your own free API key from https://www.themoviedb.org/settings/api
// Sign up for a free account, go to Settings -> API, and create a new API key
// Replace the value below with your actual API key
private const val TMDB_API_KEY = "fec717042e2aa2a1ca2b0515bc71e514"

// Note: The app will still work without a valid TMDB key, but episode names may be generic
// like "Episode 1" instead of actual episode titles

// Test URL: https://api.themoviedb.org/3/find/tt0944947?external_source=imdb_id&api_key=fec717042e2aa2a1ca2b0515bc71e514

class TmdbClient {
    
    private val client: TmdbApi
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.d("TmdbClient", "Request: ${request.url}")
                val response = chain.proceed(request)
                android.util.Log.d("TmdbClient", "Response: ${response.code}")
                response
            }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        client = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
    
    /**
     * Get episode details for a specific season using TMDB series ID
     */
    suspend fun getEpisodesForSeason(tmdbSeriesId: Int, seasonNumber: Int): List<TmdbEpisode> {
        return try {
            android.util.Log.d("TmdbClient", "Getting season $seasonNumber for series $tmdbSeriesId")
            val response = client.getSeasonDetails(tmdbSeriesId, seasonNumber)
            android.util.Log.d("TmdbClient", "Got ${response.episodes?.size ?: 0} episodes for season $seasonNumber")
            response.episodes ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to get season $seasonNumber details: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Find TMDB series ID from IMDB ID
     */
    suspend fun findSeriesByImdbId(imdbId: String): Int? {
        return try {
            android.util.Log.d("TmdbClient", "Finding TMDB ID for IMDB: $imdbId")
            val response = client.findByExternalId(imdbId)
            android.util.Log.d("TmdbClient", "Find response tv_results: ${response.tvResults?.size ?: 0}")
            response.tvResults?.firstOrNull()?.id?.also {
                android.util.Log.d("TmdbClient", "Found TMDB ID: $it")
            }
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to find series: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get all episodes for a TV show by IMDB ID
     * Returns a map of season number to list of episodes
     */
    suspend fun getAllEpisodes(imdbId: String): Map<Int, List<TmdbEpisode>> {
        val tmdbId = findSeriesByImdbId(imdbId) ?: return emptyMap()
        
        val result = mutableMapOf<Int, List<TmdbEpisode>>()
        
        // Try to get seasons 1-10 (most shows won't have more)
        for (seasonNum in 1..20) {
            try {
                val episodes = getEpisodesForSeason(tmdbId, seasonNum)
                if (episodes.isNotEmpty()) {
                    result[seasonNum] = episodes
                } else {
                    break // No more seasons
                }
            } catch (e: Exception) {
                android.util.Log.e("TmdbClient", "Failed to get season $seasonNum: ${e.message}")
                break
            }
        }
        
        return result
    }
    
    /**
     * Get popular movies from TMDB
     */
    suspend fun getPopularMovies(page: Int = 1): TmdbMovieListResponse {
        return try {
            android.util.Log.d("TmdbClient", "Getting popular movies, page $page")
            client.getPopularMovies(page = page)
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to get popular movies: ${e.message}")
            TmdbMovieListResponse(1, emptyList(), 0, 0)
        }
    }
    
    /**
     * Get popular TV shows from TMDB
     */
    suspend fun getPopularTVShows(page: Int = 1): TmdbTVListResponse {
        return try {
            android.util.Log.d("TmdbClient", "Getting popular TV shows, page $page")
            client.getPopularTVShows(page = page)
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to get popular TV shows: ${e.message}")
            TmdbTVListResponse(1, emptyList(), 0, 0)
        }
    }
    
    /**
     * Get trending movies this week
     */
    suspend fun getTrendingMovies(page: Int = 1): TmdbMovieListResponse {
        return try {
            android.util.Log.d("TmdbClient", "Getting trending movies, page $page")
            client.getTrendingMovies(page = page)
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to get trending movies: ${e.message}")
            TmdbMovieListResponse(1, emptyList(), 0, 0)
        }
    }
    
    /**
     * Get trending TV shows this week
     */
    suspend fun getTrendingTVShows(page: Int = 1): TmdbTVListResponse {
        return try {
            android.util.Log.d("TmdbClient", "Getting trending TV shows, page $page")
            client.getTrendingTVShows(page = page)
        } catch (e: Exception) {
            android.util.Log.e("TmdbClient", "Failed to get trending TV shows: ${e.message}")
            TmdbTVListResponse(1, emptyList(), 0, 0)
        }
    }
}
