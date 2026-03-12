package com.stremioclone.addons

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AddonManager {
    
    // Built-in popular addons
    val builtinAddons = listOf(
        AddonConfig(
            name = "Cinemeta",
            url = "https://cinemeta.strem.io/",
            enabled = true
        ),
        AddonConfig(
            name = "Torrentio",
            url = "https://torrentio.strem.fun/qualityfilter=4k,1080p|limit=5/",
            enabled = true
        ),
        AddonConfig(
            name = "Comet",
            url = "https://comet.elfhosted.com/eyJtYXhSZXN1bHRzUGVyUmVzb2x1dGlvbiI6MywibWF4U2l6ZSI6MCwiY2FjaGVkT25seSI6ZmFsc2UsInNvcnRDYWNoZWRVbmNhY2hlZFRvZ2V0aGVyIjpmYWxzZSwicmVtb3ZlVHJhc2giOnRydWUsInJlc3VsdEZvcm1hdCI6WyJhbGwiXSwiZGVicmlkU2VydmljZXMiOltdLCJlbmFibGVUb3JyZW50IjpmYWxzZSwiZGVkdXBsaWNhdGVTdHJlYW1zIjpmYWxzZSwiZGVicmlkU3RyZWFtUHJveHlQYXNzd29yZCI6IiIsImxhbmd1YWdlcyI6eyJyZXF1aXJlZCI6WyJlbiJdLCJhbGxvd2VkIjpbXSwiZXhjbHVkZSI6W10sInByZWZlcnJlZCI6WyJlbiJdfSwicmVzb2x1dGlvbnMiOnt9LCJvcHRpb25zIjp7InJlbW92ZV9yYW5rc191bmRlciI6LTEwMDAwMDAwMDAwLCJhbGxvd19lbmdsaXNoX2luX2xhbmd1YWdlcyI6ZmFsc2UsInJlbW92ZV91bmtub3duX2xhbmd1YWdlcyI6ZmFsc2V9fQ==/",
            enabled = true
        )
    )
    
    private val clients = mutableMapOf<String, StremioAddonApi>()
    
    fun getClient(url: String): StremioAddonApi {
        return clients.getOrPut(url) {
            createClient(url)
        }
    }
    
    private fun createClient(baseUrl: String): StremioAddonApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StremioAddonApi::class.java)
    }
}

data class AddonConfig(
    val name: String,
    val url: String,
    var enabled: Boolean
)