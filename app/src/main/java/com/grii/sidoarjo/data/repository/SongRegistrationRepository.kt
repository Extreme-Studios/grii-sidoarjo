package com.grii.sidoarjo.data.repository

import com.grii.sidoarjo.data.model.SongRegistrationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SongRegistrationRepository {
    companion object {
        const val GOOGLE_APPS_SCRIPT_ENDPOINT = "https://script.google.com/macros/s/AKfycbzU6UrouyxvwN2VASE-BKo85NUN4SGwt9yISmiO_hoUuAXM8NeEpkaYylSJbgr8rjLRaA/exec"
        private const val NETWORK_TIMEOUT_MS = 90_000
    }

    suspend fun submitRegistration(payload: SongRegistrationPayload): Result<Unit> = withContext(Dispatchers.IO) {
        if (!GOOGLE_APPS_SCRIPT_ENDPOINT.startsWith("http")) {
            return@withContext Result.failure(IllegalStateException("Google Apps Script endpoint is not configured."))
        }

        runCatching {
            val connection = (URL(GOOGLE_APPS_SCRIPT_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val body = JSONObject().apply {
                put("type", "live_registration")
                put("title", payload.title)
                put("year", payload.year)
                put("deskripsi", payload.synopsis)
                put("synopsis", payload.synopsis)
                put("kategori", payload.kategori)
                put("category", payload.kategori)
                put("video_url", payload.uploadLink)
                put("videoUrl", payload.uploadLink)
                put("youtube_url", payload.uploadLink)
                put("youtubeUrl", payload.uploadLink)
                put("live_url", payload.uploadLink)
                put("liveUrl", payload.uploadLink)
                put("stream_url", payload.uploadLink)
                put("streamUrl", payload.uploadLink)
                put("upload_link", payload.uploadLink)
                put("link_upload", payload.uploadLink)
                put("copyrightAgreement", payload.copyrightAgreement)
                if (!payload.coverImageBase64.isNullOrBlank()) {
                    put("coverImageBase64", payload.coverImageBase64)
                    put("coverImageFileName", payload.coverImageFileName.orEmpty())
                    put("coverImageMimeType", payload.coverImageMimeType.orEmpty())
                }
            }.toString()

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw IllegalStateException("Failed to send data (HTTP $responseCode). $errorBody")
            }
            val responseBody = connection.inputStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend endpoint returned HTML instead of JSON. Redeploy Apps Script and ensure Web App is active.")
            }
            val responseJson = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()
            val success = responseJson.optBoolean("success", false)
 
            if (!success) {
                val message = responseJson.optString("message").ifBlank {
                    "Failed to process upload registration."
                }
                throw IllegalStateException(message)
            }
            connection.disconnect()
        }.recoverCatching { throwable ->
            throw IllegalStateException(throwable.message ?: "An error occurred while sending registration.")
        }
    }
    suspend fun fetchYoutubeMetadata(youtubeUrl: String): Result<YoutubeMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = youtubeUrl.trim()
            if (normalizedUrl.isBlank()) throw IllegalStateException("YouTube link is empty.")

            val requestUrl = buildString {
                append(GOOGLE_APPS_SCRIPT_ENDPOINT)
                append("?type=youtube_meta")
                append("&url=")
                append(java.net.URLEncoder.encode(normalizedUrl, "UTF-8"))
            }

            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                .orEmpty()
            connection.disconnect()

            if (responseCode !in 200..299) {
                throw IllegalStateException("Failed to fetch YouTube metadata (HTTP $responseCode). $responseBody")
            }
 
            if (responseBody.trimStart().startsWith("<")) {
                throw IllegalStateException("Backend endpoint returned HTML instead of JSON. Redeploy Apps Script and ensure Web App is active.")
            }
            val responseJson = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()
            if (!responseJson.optBoolean("success", false)) {
                throw IllegalStateException(responseJson.optString("message").ifBlank { "YouTube metadata not available." })
            }

            YoutubeMetadata(
                title = responseJson.optString("title"),
                description = responseJson.optString("description"),
                thumbnailUrl = responseJson.optString("thumbnail_url").ifBlank { responseJson.optString("thumbnailUrl") },
                videoId = responseJson.optString("video_id").ifBlank { responseJson.optString("videoId") }
            )
        }
    }
}

data class YoutubeMetadata(
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val videoId: String = ""
)


