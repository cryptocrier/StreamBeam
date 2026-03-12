package com.stremioclone.addons

import com.stremioclone.model.CatalogResponse
import com.stremioclone.model.Manifest
import com.stremioclone.model.MetaResponse
import com.stremioclone.model.StreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StremioAddonApi {
    
    @GET("manifest.json")
    suspend fun getManifest(): Manifest
    
    @GET("catalog/{type}/{id}.json")
    suspend fun getCatalog(
        @Path("type") type: String,
        @Path("id") id: String,
        @Query("skip") skip: Int? = null
    ): CatalogResponse
    
    @GET("catalog/{type}/{id}/{extra}.json")
    suspend fun getCatalogWithExtra(
        @Path("type") type: String,
        @Path("id") id: String,
        @Path("extra") extra: String,
        @Query("skip") skip: Int? = null
    ): CatalogResponse
    
    @GET("meta/{type}/{id}.json")
    suspend fun getMeta(
        @Path("type") type: String,
        @Path("id") id: String
    ): MetaResponse
    
    @GET("stream/{type}/{id}.json")
    suspend fun getStreams(
        @Path("type") type: String,
        @Path("id") id: String
    ): StreamResponse
    
    // Search using extra path segment (Cinemeta format)
    @GET("catalog/{type}/{id}/search={query}.json")
    suspend fun search(
        @Path("type") type: String,
        @Path("id") id: String,
        @Path(value = "query", encoded = false) query: String,
        @Query("skip") skip: Int? = null
    ): CatalogResponse
}