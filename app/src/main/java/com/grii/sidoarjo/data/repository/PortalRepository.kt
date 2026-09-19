package com.grii.sidoarjo.data.repository

import android.util.Log
import com.grii.sidoarjo.data.model.PortalPost
import com.grii.sidoarjo.data.model.PortalUserSession
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PortalRepository {
    fun getCategories(): List<String> = listOf(
        "All",
        "Service & Fellowship",
        "Announcements",
        "Media & Theology"
    )

    fun getSubmissionCategories(): List<String> = getCategories().filter { it != "All" }

    suspend fun loginAdmin(username: String, password: String): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("type", "admin_login")
                put("username", username.trim())
                put("password", password)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Failed to log in as admin." })
            }
            val user = response.optJSONObject("user")
                ?: throw IllegalStateException("Admin account not found.")
            PortalUserSession(
                username = user.optString("username").ifBlank { username.trim() },
                status = user.optString("status").ifBlank { "ACTIVE" },
                createdAt = user.optString("createdAt"),
                password = password
            )
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to log in as admin.")
        }
    }
    suspend fun updateAdminCredentials(
        currentUsername: String,
        newUsername: String,
        newPassword: String
    ): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentUsername.trim().isBlank()) throw IllegalStateException("Invalid admin session.")
            if (newUsername.trim().isBlank()) throw IllegalStateException("New username is required.")
            if (newPassword.isBlank()) throw IllegalStateException("New password is required.")

            val payload = JSONObject().apply {
                put("type", "admin_update_credentials")
                put("username", currentUsername.trim())
                put("new_username", newUsername.trim())
                put("new_password", newPassword)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Failed to update admin account." })
            }
            val user = response.optJSONObject("user")
                ?: throw IllegalStateException("Admin account not found after update.")
            PortalUserSession(
                username = user.optString("username").ifBlank { newUsername.trim() },
                status = user.optString("status").ifBlank { "ACTIVE" },
                createdAt = user.optString("createdAt"),
                password = newPassword
            )
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to update admin account.")
        }
    }

    suspend fun checkAdminStatus(username: String, password: String): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("type", "admin_check_status")
                put("username", username.trim())
                put("password", password)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Failed to verify admin status." })
            }
            val user = response.optJSONObject("user")
                ?: throw IllegalStateException("Admin account not found.")
            PortalUserSession(
                username = user.optString("username").ifBlank { username.trim() },
                status = user.optString("status").ifBlank { "ACTIVE" },
                createdAt = user.optString("createdAt"),
                password = password
            )
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to verify admin status.")
        }
    }

    fun normalizeWhatsapp(rawInput: String): Result<String> = runCatching {
        val compact = rawInput
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")
            .trim()

        val normalized = when {
            compact.startsWith("0") -> "62${compact.drop(1)}"
            compact.startsWith("8") -> "62$compact"
            compact.startsWith("62") -> compact
            else -> throw IllegalStateException("Invalid WhatsApp number. Use an active Indonesian number.")
        }

        if (!normalized.all { it.isDigit() } || normalized.length !in 10..15) {
            throw IllegalStateException("Invalid WhatsApp number. Use an active Indonesian number.")
        }
        normalized
    }

    suspend fun registerHubBand(
        namaAnggota: String,
        alamat: String,
        whatsappRaw: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val whatsapp = normalizeWhatsapp(whatsappRaw).getOrThrow()
            val payload = JSONObject().apply {
                put("type", "hub_register")
                put("nama_anggota", namaAnggota)
                put("nama_kreator", namaAnggota)
                put("alamat", alamat)
                put("whatsapp", whatsapp)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(
                    response.optString("message").ifBlank { "Failed to register creator." }
                )
            }
            (response.optJSONObject("user")?.optString("whatsapp") ?: "").ifBlank { whatsapp }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to register creator.")
        }
    }

    suspend fun loginHubBand(
        whatsappRaw: String,
    ): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val account = requestHubAccount(type = "hub_login", whatsappRaw = whatsappRaw)
            val status = account.status
            if (!status.equals("ACTIVE", ignoreCase = true)) {
                Log.d("HubAuth", "login rejected reason=status_$status")
                throw IllegalStateException(
                    when {
                        status.equals("PENDING", ignoreCase = true) -> "Your registration is pending admin approval."
                        status.equals("BLOCKED", ignoreCase = true) -> "Your account is blocked by the admin."
                        else -> "GRII Sidoarjo account is not active."
                    }
                )
            }
            Log.d("HubAuth", "login accepted reason=status_ACTIVE username=${account.username}")
            account
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to log in to GRII Sidoarjo.")
        }
    }

    suspend fun checkHubStatus(
        whatsappRaw: String,
    ): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            requestHubAccount(type = "hub_check_status", whatsappRaw = whatsappRaw)
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to check GRII Sidoarjo status.")
        }
    }

    suspend fun fetchActivePosts(): Result<List<PortalPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val requestUrl = "$GOOGLE_APPS_SCRIPT_ENDPOINT?type=portal_posts&ts=${System.currentTimeMillis()}"
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code while fetching portal data.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val normalizedBody = body.trimStart()
            if (normalizedBody.startsWith("<!DOCTYPE", ignoreCase = true) || normalizedBody.startsWith("<html", ignoreCase = true)) {
                Log.w("PortalKita", "Backend returned HTML for portal posts; using empty list fallback.")
                return@runCatching emptyList<PortalPost>()
            }

            val root = JSONObject(body)
            val candidates = listOfNotNull(
                root.optJSONArray("portal_posts"),
                root.optJSONArray("posts"),
                root.optJSONArray("portalPosts")
            )
            val postsArray = candidates.firstOrNull() ?: JSONArray()
            buildList {
                for (i in 0 until postsArray.length()) {
                    val item = postsArray.optJSONObject(i) ?: continue
                    val status = item.optString("status").ifBlank { "PENDING" }
                    if (!status.equals("ACTIVE", ignoreCase = true) && !status.equals("active", ignoreCase = true)) continue
                    val posterUrl = item.optString("poster_url")
                    if (posterUrl.isBlank()) continue
                    add(
                        PortalPost(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title").ifBlank {
                                item.optString("band_name").ifBlank { item.optString("category") }
                            },
                            submitterName = item.optString("submitterName").ifBlank {
                                item.optString("band_name").ifBlank {
                                    item.optString("submitter_name").ifBlank { "Board Info" }
                                }
                            },
                            category = item.optString("category"),
                            posterUrl = posterUrl,
                            targetUrl = item.optString("target_url"),
                            shortCaption = item.optString("short_caption").ifBlank { item.optString("caption") },
                            eventDate = item.optString("event_date"),
                            status = status,
                            createdAt = item.optString("created_at")
                        )
                    )
                }
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to load Board Info.")
        }
    }



    suspend fun submitPoster(
        session: PortalUserSession,
        category: String,
        posterImageBase64: String,
        posterImageFileName: String,
        posterImageMimeType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (session.username.trim().isBlank()) {
                Log.e("PortalKirim", "Invalid admin session username=${session.username}")
                throw IllegalStateException("Invalid admin session. Please log in again.")
            }
            submitPortalMetadata(
                session = session,
                category = category,
                posterImageBase64 = posterImageBase64,
                posterImageFileName = posterImageFileName,
                posterImageMimeType = posterImageMimeType
            )
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to send poster.")
        }
    }
    suspend fun deletePoster(
        session: PortalUserSession,
        postId: String,
        posterUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!session.status.equals("ACTIVE", ignoreCase = true)) {
                throw IllegalStateException("Admin login is required.")
            }
            if (postId.trim().isBlank()) {
                throw IllegalStateException("Invalid information ID.")
            }
            val payload = JSONObject().apply {
                put("type", "portal_delete")
                put("username", session.username)
                put("password", session.password)
                put("userId", session.username)
                put("postId", postId)
                put("posterUrl", posterUrl)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Failed to delete information." })
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to delete information.")
        }
    }

    private fun submitPortalMetadata(
        session: PortalUserSession,
        category: String,
        posterImageBase64: String,
        posterImageFileName: String,
        posterImageMimeType: String
    ) {
        val payload = JSONObject().apply {
            put("type", "portal_post")
            put("username", session.username)
            put("password", session.password)
            put("userId", session.username)
            put("category", category)
            put("title", "$category - ${session.username}".trim())
            put("shortCaption", "$category poster from ${session.username}".trim())
            put("eventDate", "")
            put("posterImageBase64", posterImageBase64)
            put("posterImageFileName", posterImageFileName)
            put("posterImageMimeType", posterImageMimeType)
        }
        Log.d(
            "PortalKirim",
            "payload={type=portal_post,username=${session.username},category=$category,fileName=$posterImageFileName,mimeType=$posterImageMimeType,base64Length=${posterImageBase64.length}}"
        )
        val response = postJson(payload)
        Log.d("PortalKirim", "raw response=${response.toString()}")
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("message").ifBlank { "Failed to save poster metadata." }
            )
        }
    }

    private fun postJson(payload: JSONObject): JSONObject {
        val payloadType = payload.optString("type")
        if (payloadType == "hub_register" || payloadType == "hub_login" || payloadType == "hub_check_status") {
            Log.d("HubAuth", "payload=${payload.toString()}")
        }
        val connection = (URL(GOOGLE_APPS_SCRIPT_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (payloadType == "hub_register" || payloadType == "hub_login" || payloadType == "hub_check_status") {
            Log.d("HubAuth", "raw response=$responseBody")
        }

        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode while sending poster. $responseBody")
        }
        val normalizedBody = responseBody.trimStart()
        if (normalizedBody.startsWith("<!DOCTYPE", ignoreCase = true) || normalizedBody.startsWith("<html", ignoreCase = true)) {
            throw IllegalStateException("Endpoint did not return JSON. Redeploy Apps Script as Web App (Who has access: Anyone).")
        }
        return JSONObject(responseBody.ifBlank { "{\"success\":false}" })
    }

    private fun requestHubAccount(type: String, whatsappRaw: String): PortalUserSession {
        val whatsapp = normalizeWhatsapp(whatsappRaw).getOrThrow()
        val payload = JSONObject().apply {
            put("type", type)
            put("whatsapp", whatsapp)
        }
        val response = postJson(payload)
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("message").ifBlank {
                    when (type) {
                        "hub_login" -> "Failed to log in to GRII Sidoarjo."
                        "hub_check_status" -> "GRII Sidoarjo account info not found."
                        else -> "Failed to check GRII Sidoarjo status."
                    }
                }
            )
        }

        val user = response.optJSONObject("user")
            ?: throw IllegalStateException("GRII Sidoarjo account info not found.")
        val normalizedStatus = normalizeHubStatus(user.optString("status"))
        val parsedUserId = parseHubUserId(user, normalizedStatus)
        Log.d("HubAuth", "parsed user id=$parsedUserId")
        Log.d("HubAuth", "parsed status=$normalizedStatus")
        Log.d("HubAuth", "login status decision=${if (normalizedStatus.equals("ACTIVE", ignoreCase = true)) "accepted" else "not_active"}")
        return PortalUserSession(
            username = user.optString("username").ifBlank { user.optString("bandName").ifBlank { parsedUserId } },
            status = normalizedStatus,
            createdAt = user.optString("createdAt")
        )
    }

    private fun parseHubUserId(user: JSONObject, normalizedStatus: String): String {
        val rawId = user.opt("id")?.toString()?.trim().orEmpty()
        val username = user.optString("username").trim()
        if (rawId.isBlank() && username.isBlank()) {
            Log.e("HubAuth", "Invalid admin id from backend. rawId=$rawId status=$normalizedStatus")
            if (normalizedStatus.equals("ACTIVE", ignoreCase = true)) {
                throw IllegalStateException("Invalid admin ID.")
            }
        }
        return rawId.ifBlank { username }
    }

    private fun normalizeHubStatus(rawStatus: String): String = when (rawStatus.trim().uppercase()) {
        "ACTIVE" -> "ACTIVE"
        "BLOCKED" -> "BLOCKED"
        else -> "PENDING"
    }

    suspend fun updateLiveStreamState(
        session: PortalUserSession,
        isLive: Boolean,
        url: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!session.status.equals("ACTIVE", ignoreCase = true)) {
                throw IllegalStateException("Admin login is required.")
            }
            val payload = JSONObject().apply {
                put("type", "update_live_stream")
                put("username", session.username)
                put("password", session.password)
                put("is_live", isLive)
                put("url", url)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Failed to update live stream status." })
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Failed to update live stream status.")
        }
    }

    companion object {
        private const val GOOGLE_APPS_SCRIPT_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbzU6UrouyxvwN2VASE-BKo85NUN4SGwt9yISmiO_hoUuAXM8NeEpkaYylSJbgr8rjLRaA/exec"
    }
}


