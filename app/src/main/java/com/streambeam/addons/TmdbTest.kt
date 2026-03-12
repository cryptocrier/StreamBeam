package com.streambeam.addons

import kotlinx.coroutines.runBlocking

/**
 * Test TMDB API - Run this to verify your API key works
 */
object TmdbTest {
    
    @JvmStatic
    fun testApiKey() = runBlocking {
        val client = TmdbClient()
        
        android.util.Log.d("TmdbTest", "Testing TMDB API with Game of Thrones (tt0944947)...")
        
        // Test 1: Find by IMDB ID
        val tmdbId = client.findSeriesByImdbId("tt0944947")
        android.util.Log.d("TmdbTest", "TMDB ID for Game of Thrones: $tmdbId")
        
        if (tmdbId == null) {
            android.util.Log.e("TmdbTest", "❌ Failed to find series - API key may be invalid")
            return@runBlocking
        }
        
        // Test 2: Get season details
        val episodes = client.getEpisodesForSeason(tmdbId, 1)
        android.util.Log.d("TmdbTest", "✅ Got ${episodes.size} episodes for Season 1")
        
        episodes.firstOrNull()?.let {
            android.util.Log.d("TmdbTest", "First episode: ${it.name}")
            android.util.Log.d("TmdbTest", "Overview: ${it.overview?.take(50)}...")
        }
    }
}
