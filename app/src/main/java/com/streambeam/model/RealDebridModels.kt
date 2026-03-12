package com.streambeam.model

import com.google.gson.annotations.SerializedName

// Real-Debrid Models

data class MagnetRequest(
    val magnet: String
)

data class TorrentResponse(
    val id: String,
    val uri: String
)

data class TorrentInfo(
    val id: String,
    val filename: String,
    val hash: String,
    val bytes: Long,
    val host: String,
    val split: Int,
    val progress: Double,
    val status: String, // magnet_error, magnet_conversion, waiting_files_selection, queued, downloading, downloaded, error, virus, compressing, uploading, dead
    val added: String,
    val files: List<TorrentFile>?,
    val links: List<String>?,
    val ended: String?,
    val speed: Long?,
    val seeders: Int?
)

data class TorrentFile(
    val id: Int,
    val path: String,
    val bytes: Long,
    val selected: Int
)

data class UnrestrictRequest(
    val link: String
)

data class UnrestrictResponse(
    val id: String,
    val filename: String,
    val mimeType: String,
    val filesize: Long,
    val link: String,
    val host: String,
    val chunks: Int,
    val crc: Int,
    val download: String, // Direct download URL
    val streamable: Int
)

data class UserInfo(
    val id: Int,
    val username: String,
    val email: String,
    val points: Int,
    val locale: String,
    val avatar: String,
    val type: String, // "premium" or "free"
    val premium: Int, // seconds remaining
    val expiration: String?
)
