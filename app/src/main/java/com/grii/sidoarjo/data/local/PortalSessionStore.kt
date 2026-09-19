package com.grii.sidoarjo.data.local

import android.util.Log
import android.content.Context
import com.grii.sidoarjo.data.model.PortalUserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PortalSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    init {
        ensureInitialized(prefs)
    }

    val sessionFlow: StateFlow<PortalUserSession?> = sharedSessionFlow.asStateFlow()

    fun saveSession(session: PortalUserSession) {
        prefs.edit()
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_CREATED_AT, session.createdAt)
            .putString(KEY_STATUS, session.status)
            .putString(KEY_PASSWORD, session.password)
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
        sharedSessionFlow.value = session
        Log.d("HubAuth", "saved session username=${session.username}, status=${session.status}")
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_LOGGED_IN)
            .remove(KEY_USERNAME)
            .remove(KEY_CREATED_AT)
            .remove(KEY_STATUS)
            .remove(KEY_PASSWORD)
            .apply()
        sharedSessionFlow.value = null
    }

    companion object {
        private const val PREFS_NAME = "portal_session_store"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_STATUS = "status"
        private const val KEY_PASSWORD = "password"

        private var initialized = false
        private val sharedSessionFlow = MutableStateFlow<PortalUserSession?>(null)

        @Synchronized
        private fun ensureInitialized(prefs: android.content.SharedPreferences) {
            if (initialized) return
            sharedSessionFlow.value = loadSession(prefs)
            initialized = true
        }

        private fun loadSession(prefs: android.content.SharedPreferences): PortalUserSession? {
            if (!prefs.getBoolean(KEY_LOGGED_IN, false)) return null
            val username = readStringCompat(prefs, KEY_USERNAME)
            val createdAt = prefs.getString(KEY_CREATED_AT, "").orEmpty()
            val status = prefs.getString(KEY_STATUS, "ACTIVE").orEmpty()
            val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
            if (username.isBlank()) return null
            return PortalUserSession(username = username, status = status, createdAt = createdAt, password = password)
        }

        private fun readStringCompat(
            prefs: android.content.SharedPreferences,
            key: String
        ): String = when (val value = prefs.all[key]) {
            is String -> value
            is Number -> value.toString()
            else -> ""
        }

    }
}
