package com.grii.sidoarjo.data.repository

import com.grii.sidoarjo.data.model.Song

class MovieRepository(
    private val api: ApiSongRepository = ApiSongRepository()
) {
    suspend fun fetchMovies(): Result<List<Song>> = api.fetchSongs()
}

