package com.streambeam.realdebrid

import com.streambeam.model.TorrentInfo
import com.streambeam.model.TorrentResponse
import com.streambeam.model.UnrestrictResponse
import com.streambeam.model.UserInfo
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RealDebridApi {
    
    @GET("user")
    suspend fun getUserInfo(
        @Header("Authorization") token: String
    ): UserInfo
    
    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") token: String,
        @Field("magnet") magnet: String
    ): TorrentResponse
    
    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): TorrentInfo
    
    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Field("files") files: String = "all"
    ): retrofit2.Response<Unit>
    
    @POST("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") token: String,
        @Path("id") id: String
    )
    
    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") token: String,
        @Field("link") link: String
    ): UnrestrictResponse
}
