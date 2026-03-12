package com.streambeam.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streambeam.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    
    companion object {
        private val REAL_DEBRID_KEY = stringPreferencesKey("real_debrid_api_key")
        private val COMET_URL_KEY = stringPreferencesKey("comet_url")
        private val PREFERRED_LANGUAGES_KEY = stringSetPreferencesKey("preferred_languages")
        private const val DEFAULT_LANGUAGES = "en" // Default to English
        
        // Available language options for filtering
        val AVAILABLE_LANGUAGES = listOf(
            "en" to "English 🇺🇸",
            "es" to "Spanish 🇪🇸",
            "fr" to "French 🇫🇷",
            "de" to "German 🇩🇪",
            "it" to "Italian 🇮🇹",
            "pt" to "Portuguese 🇵🇹",
            "ru" to "Russian 🇷🇺",
            "ja" to "Japanese 🇯🇵",
            "ko" to "Korean 🇰🇷",
            "zh" to "Chinese 🇨🇳",
            "hi" to "Hindi 🇮🇳",
            "pl" to "Polish 🇵🇱",
            "nl" to "Dutch 🇳🇱",
            "tr" to "Turkish 🇹🇷",
            "ar" to "Arabic 🇸🇦"
        )
    }
    
    val realDebridKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[REAL_DEBRID_KEY] ?: ""
        }
    
    val cometUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[COMET_URL_KEY] ?: ""
        }
    
    val preferredLanguages: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[PREFERRED_LANGUAGES_KEY] ?: setOf(DEFAULT_LANGUAGES)
        }
    
    suspend fun saveRealDebridKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[REAL_DEBRID_KEY] = key
        }
    }
    
    suspend fun clearRealDebridKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(REAL_DEBRID_KEY)
        }
    }
    
    suspend fun saveCometUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[COMET_URL_KEY] = url
        }
    }
    
    suspend fun clearCometUrl() {
        context.dataStore.edit { preferences ->
            preferences.remove(COMET_URL_KEY)
        }
    }
    
    suspend fun savePreferredLanguages(languages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_LANGUAGES_KEY] = languages
        }
    }
    
    suspend fun addPreferredLanguage(language: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PREFERRED_LANGUAGES_KEY] ?: setOf(DEFAULT_LANGUAGES)
            preferences[PREFERRED_LANGUAGES_KEY] = current + language
        }
    }
    
    suspend fun removePreferredLanguage(language: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PREFERRED_LANGUAGES_KEY] ?: setOf(DEFAULT_LANGUAGES)
            val newSet = current - language
            // Ensure at least one language is selected
            preferences[PREFERRED_LANGUAGES_KEY] = if (newSet.isEmpty()) setOf(DEFAULT_LANGUAGES) else newSet
        }
    }
    
    // Watch History Keys
    private val WATCH_HISTORY_KEY = stringPreferencesKey("watch_history")
    private val MAX_WATCH_HISTORY_ITEMS = 50 // Keep last 50 watched items
    
    val watchHistory: Flow<List<WatchProgress>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[WATCH_HISTORY_KEY] ?: "[]"
            try {
                val type = object : TypeToken<List<WatchProgress>>() {}.type
                Gson().fromJson<List<WatchProgress>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    suspend fun saveWatchProgress(progress: WatchProgress) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[WATCH_HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<List<WatchProgress>>() {}.type
            val currentList = try {
                Gson().fromJson<List<WatchProgress>>(currentJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            // Remove existing entry with same ID if exists
            val filteredList = currentList.filter { it.id != progress.id }
            
            // Add new entry at the beginning (most recent first)
            val newList = listOf(progress) + filteredList
            
            // Limit to max items
            val limitedList = newList.take(MAX_WATCH_HISTORY_ITEMS)
            
            // Save back
            preferences[WATCH_HISTORY_KEY] = Gson().toJson(limitedList)
        }
    }
    
    suspend fun removeFromWatchHistory(id: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[WATCH_HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<List<WatchProgress>>() {}.type
            val currentList = try {
                Gson().fromJson<List<WatchProgress>>(currentJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            val newList = currentList.filter { it.id != id }
            preferences[WATCH_HISTORY_KEY] = Gson().toJson(newList)
        }
    }
    
    suspend fun clearWatchHistory() {
        context.dataStore.edit { preferences ->
            preferences[WATCH_HISTORY_KEY] = "[]"
        }
    }
}
