package com.grii.sidoarjo.data.model

data class Song(
    val id: String,
    val title: String = "",
    val year: String = "",
    val synopsis: String = "",
    val youtubeUrl: String = "",
    val videoId: String = "",
    val thumbnailUrl: String = "",
    val createdAt: String = "",
    val status: String = "",
    val views: Int = 0,
    val likes: Int = 0,
    val alreadyLiked: Boolean = false,
    val artist: String = "",
    val genre: String = "",
    val category: String = "",
    val duration: String = "",
    val director: String = "",
    val castName: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val trailerUrl: String = "",
    val movieUrl: String = "",
    val audioUrl: String = "",
    val uploadedAt: String = "",
    val featured: Boolean = false,
    val releaseYear: String = "",
    val albumName: String = "",
    val trackNumber: Int? = null
)

data class LiveStreamState(
    val isLive: Boolean = false,
    val url: String = ""
)

