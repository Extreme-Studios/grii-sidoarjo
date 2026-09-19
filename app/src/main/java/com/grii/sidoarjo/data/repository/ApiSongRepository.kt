package com.grii.sidoarjo.data.repository

import android.util.Log
import com.grii.sidoarjo.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.TimeZone

private const val SONGS_ENDPOINT_URL = "https://script.google.com/macros/s/AKfycbzU6UrouyxvwN2VASE-BKo85NUN4SGwt9yISmiO_hoUuAXM8NeEpkaYylSJbgr8rjLRaA/exec"

class ApiSongRepository {
    private var cachedSongs: List<Song> = emptyList()
    private var lastFetchAtMs: Long = 0L
    private var cachedLikeUserId: String? = null


    suspend fun syncDefaultChannel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val requestUrl = buildString {
                append(SONGS_ENDPOINT_URL)
                append("?type=sync_channel&ts=")
                append(System.currentTimeMillis())
            }
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 45_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend returned HTML while syncing YouTube channel.")
            }
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode during channel sync.")
            }
            val root = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()
            if (!root.optBoolean("success", false)) {
                throw IllegalStateException(root.optString("message").ifBlank { "Failed to sync channel." })
            }
            cachedSongs = emptyList()
            lastFetchAtMs = 0L
        }
    }
    suspend fun fetchSongs(likeUserId: String? = null): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val normalizedLikeUserId = likeUserId?.trim().orEmpty().ifBlank { null }
            if (cachedSongs.isNotEmpty() && cachedLikeUserId == normalizedLikeUserId && now - lastFetchAtMs < CACHE_TTL_MS) {
                Log.d(TAG, "Using in-memory cache. count=${cachedSongs.size}")
                return@runCatching cachedSongs
            }

            val requestUrl = buildString {
                append(SONGS_ENDPOINT_URL)
                append("?ts=${System.currentTimeMillis()}")
                if (!normalizedLikeUserId.isNullOrBlank()) {
                    append("&user_id=${java.net.URLEncoder.encode(normalizedLikeUserId, "UTF-8")}")
                }
            }

            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }

            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code saat mengambil data film.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            if (body.trimStart().startsWith("<")) {
                Log.w(TAG, "Backend returned HTML for songs; falling back to cache/empty list.")
                return@runCatching cachedSongs
            }

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) throw IllegalStateException("Response API tidak sukses.")
            val songsArray = root.optJSONArray("films")
                ?: root.optJSONArray("movies")
                ?: root.optJSONArray("songs")
                ?: root.optJSONArray("data")
                ?: root.optJSONObject("data")?.optJSONArray("films")
                ?: root.optJSONObject("data")?.optJSONArray("songs")
                ?: throw IllegalStateException("Field films tidak ditemukan.")

            Log.d(TAG, "Raw films from API: ${songsArray.length()}")
            var activeCount = 0
            val songs = buildList<Song> {
                for (i in 0 until songsArray.length()) {
                    val item = songsArray.optJSONObject(i) ?: continue
                    val title = readString(item, "title", "film_title", "movie_title", "judul_film", "song_title", "judul_lagu", "judul")
                    val rawYear = readString(item, "year", "release", "releaseYear", "release_year", "tahun_release", "tahun_pembuatan")
                    val youtubeUrl = readString(item, "video_url", "videoUrl", "youtube_url", "youtubeUrl", "movie_url", "movieUrl")
                    val videoId = readString(item, "video_id", "videoId")
                    val thumbnailUrl = readString(item, "thumbnail_url", "thumbnailUrl", "cover_url", "coverUrl", "poster_url", "posterUrl")
                    val status = readString(item, "status").ifBlank { "approved" }
                    if (!status.equals("approved", ignoreCase = true)) continue
                    activeCount++

                    val rawId = readString(item, "id", "film_id", "movie_id", "song_id")
                    val createdAt = readString(item, "created_at", "createdAt", "uploaded_at", "uploadedAt", "updated_at")
                    val kategori = readString(item, "category", "genre", "kategori")
                    val resolvedId = rawId.ifBlank {
                        listOf(title, rawYear, createdAt, i.toString()).joinToString("#")
                    }
                    val resolvedYoutubeUrl = youtubeUrl.ifBlank {
                        if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else ""
                    }
                    val year = rawYear.ifBlank { yearFromCreatedAt(createdAt) }
                    val synopsis = readString(item, "synopsis", "description", "deskripsi", "desc", "notes")
                    val resolvedThumbnail = thumbnailUrl.ifBlank { youtubeThumbnailUrl(videoId) }

                    Log.d(TAG, "Film from API title=$title youtube_url=$resolvedYoutubeUrl thumbnail_url=$resolvedThumbnail")

                    add(
                        Song(
                            id = resolvedId,
                            title = title,
                            year = year,
                            synopsis = synopsis,
                            youtubeUrl = resolvedYoutubeUrl,
                            videoId = videoId,
                            thumbnailUrl = resolvedThumbnail,
                            createdAt = createdAt,
                            status = status,
                            artist = kategori.ifBlank { year.ifBlank { "Live" } },
                            genre = kategori.ifBlank { year.ifBlank { "Live" } },
                            category = readString(item, "category", "genre"),
                            description = synopsis,
                            coverUrl = resolvedThumbnail,
                            movieUrl = resolvedYoutubeUrl,
                            audioUrl = resolvedYoutubeUrl,
                            uploadedAt = createdAt,
                            releaseYear = year
                        )
                    )
                }
            }
                .filter { it.title.isNotBlank() }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<Song> { parseCreatedAtMillis(it.createdAt.ifBlank { it.uploadedAt }) }
                        .thenBy { it.title.lowercase(Locale.US) }
                )

            Log.d(TAG, "Active films after status filter: $activeCount")
            Log.d(TAG, "Films after validation/dedupe/sort: ${songs.size}")
            cachedSongs = songs
            lastFetchAtMs = now
            cachedLikeUserId = normalizedLikeUserId
            songs
        }
    }

    fun getGenres(songs: List<Song>): List<String> = listOf("All") + songs.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    fun getArtists(songs: List<Song>): List<String> = songs.map { it.year }.filter { it.isNotBlank() }.distinct().sortedDescending()

    fun getFallbackSongs(): List<Song> = emptyList()



    suspend fun updateFilmMetadata(
        filmId: String,
        username: String,
        password: String,
        title: String,
        year: String,
        deskripsi: String,
        coverImageBase64: String? = null,
        coverImageFileName: String? = null,
        coverImageMimeType: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            if (filmId.trim().isBlank()) throw IllegalStateException("film_id kosong.")
            if (username.trim().isBlank()) throw IllegalStateException("Login admin diperlukan.")
            if (title.trim().isBlank()) throw IllegalStateException("Judul wajib diisi.")

            val connection = (URL(SONGS_ENDPOINT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val body = JSONObject().apply {
                put("type", "film_update")
                put("film_id", filmId)
                put("username", username)
                put("password", password)
                put("title", title)
                put("year", year)
                put("deskripsi", deskripsi)
                if (coverImageBase64 != null) {
                    put("coverImageBase64", coverImageBase64)
                    put("coverImageFileName", coverImageFileName.orEmpty())
                    put("coverImageMimeType", coverImageMimeType.orEmpty())
                }
            }.toString()
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend endpoint returned HTML instead of JSON. Redeploy Apps Script and ensure the web app URL is active.")
            }
            val responseJson = JSONObject(responseBody)
            if (!responseJson.optBoolean("success", false)) {
                throw IllegalStateException(responseJson.optString("message").ifBlank { "Failed to update movie metadata." })
            }
            val filmJson = responseJson.optJSONObject("film")
            val newThumbnail = filmJson?.optString("thumbnail_url")?.takeIf { it.isNotBlank() }
            newThumbnail
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to update movie metadata.")
        }
    }
    suspend fun deleteFilm(
        filmId: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (filmId.trim().isBlank()) throw IllegalStateException("film_id is empty.")
            if (username.trim().isBlank()) throw IllegalStateException("Admin login is required.")

            val connection = (URL(SONGS_ENDPOINT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val body = JSONObject().apply {
                put("type", "film_delete")
                put("film_id", filmId)
                put("username", username)
                put("password", password)
            }.toString()
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend endpoint returned HTML instead of JSON. Redeploy Apps Script and ensure the web app URL is active.")
            }
            val responseJson = JSONObject(responseBody)
            if (!responseJson.optBoolean("success", false)) {
                throw IllegalStateException(responseJson.optString("message").ifBlank { "Failed to delete movie." })
            }
            cachedSongs = emptyList()
            lastFetchAtMs = 0L
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to delete movie.")
        }
    }

    suspend fun fetchLiveStreamState(): Result<com.grii.sidoarjo.data.model.LiveStreamState> = withContext(Dispatchers.IO) {
        runCatching {
            val requestUrl = buildString {
                append(SONGS_ENDPOINT_URL)
                append("?type=get_live_stream&ts=")
                append(System.currentTimeMillis())
            }
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code saat mengambil live stream status.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            if (body.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend returned HTML instead of JSON.")
            }

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) throw IllegalStateException("Response API tidak sukses.")
            val isLive = root.optBoolean("is_live", false)
            val url = root.optString("url", "")
            com.grii.sidoarjo.data.model.LiveStreamState(isLive = isLive, url = url)
        }
    }

    companion object {
        private const val TAG = "ApiSongRepository"
        private const val CACHE_TTL_MS = 60_000L
    }
}



private fun readString(item: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        if (item.has(key)) {
            val value = item.opt(key)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString().trim()
                if (text.isNotBlank()) return text
            }
        }
    }
    return ""
}

private fun parseCreatedAtMillis(value: String): Long {
    if (value.isBlank()) return Long.MIN_VALUE
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        }.getOrNull()
    } ?: Long.MIN_VALUE
}

private fun youtubeThumbnailUrl(videoId: String): String {
    return if (videoId.isBlank()) "" else "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}

private fun fetchYoutubeMetadataFromBackend(youtubeUrl: String): YoutubeMetaFallback {
    val normalizedUrl = youtubeUrl.trim()
    if (normalizedUrl.isBlank()) return YoutubeMetaFallback()

    return runCatching {
        val requestUrl = buildString {
            append(SONGS_ENDPOINT_URL)
            append("?type=youtube_meta&url=")
            append(java.net.URLEncoder.encode(normalizedUrl, "UTF-8"))
            append("&ts=")
            append(System.currentTimeMillis())
        }
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            return@runCatching YoutubeMetaFallback()
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (body.trimStart().startsWith("<")) return@runCatching YoutubeMetaFallback()
        val root = JSONObject(body)
        YoutubeMetaFallback(
            title = readString(root, "title"),
            description = readString(root, "description"),
            thumbnailUrl = readString(root, "thumbnail_url", "thumbnailUrl")
        )
    }.getOrElse { YoutubeMetaFallback() }
}

private fun fetchYoutubeDescriptionFromYoutube(youtubeUrl: String, videoId: String): String {
    val normalizedUrl = youtubeUrl.trim().ifBlank {
        if (videoId.isBlank()) "" else "https://www.youtube.com/watch?v=$videoId"
    }
    if (normalizedUrl.isBlank()) return ""

    return runCatching {
        val pageUrl = if (normalizedUrl.contains("?")) "$normalizedUrl&hl=id" else "$normalizedUrl?hl=id"
        val connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            useCaches = false
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            return@runCatching ""
        }
        val html = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val metaDescription = Regex("""<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        decodeHtmlEntities(metaDescription.trim())
    }.getOrDefault("")
}

private fun yearFromCreatedAt(createdAt: String): String {
    val millis = parseCreatedAtMillis(createdAt)
    if (millis == Long.MIN_VALUE) return ""
    return runCatching {
        java.text.SimpleDateFormat("yyyy", Locale.US).format(Date(millis))
    }.getOrDefault("")
}

private fun decodeHtmlEntities(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

private data class YoutubeMetaFallback(
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = ""
)


