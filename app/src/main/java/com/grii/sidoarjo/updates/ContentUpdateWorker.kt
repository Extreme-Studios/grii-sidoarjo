package com.grii.sidoarjo.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grii.sidoarjo.MainActivity
import com.grii.sidoarjo.R
import com.grii.sidoarjo.data.repository.ApiSongRepository
import com.grii.sidoarjo.data.repository.PortalRepository

class ContentUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ensureChannel()

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val songRepo = ApiSongRepository()
            val portalRepo = PortalRepository()

            val latestVideoId = songRepo.fetchSongs().getOrNull()?.firstOrNull()?.id.orEmpty()
            val latestPosterId = portalRepo.fetchActivePosts().getOrNull()?.firstOrNull()?.id.orEmpty()

            val lastVideoId = prefs.getString(KEY_LAST_VIDEO_ID, "").orEmpty()
            val lastPosterId = prefs.getString(KEY_LAST_POSTER_ID, "").orEmpty()
            val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

            if (!initialized) {
                prefs.edit()
                    .putBoolean(KEY_INITIALIZED, true)
                    .putString(KEY_LAST_VIDEO_ID, latestVideoId)
                    .putString(KEY_LAST_POSTER_ID, latestPosterId)
                    .apply()
                return Result.success()
            }

            if (latestVideoId.isNotBlank() && latestVideoId != lastVideoId) {
                notify(
                    notificationId = VIDEO_NOTIFICATION_ID,
                    title = "Live Baru Tersedia",
                    text = "Ada live baru di GRII Sidoarjo TV."
                )
            }

            if (latestPosterId.isNotBlank() && latestPosterId != lastPosterId) {
                notify(
                    notificationId = POSTER_NOTIFICATION_ID,
                    title = "Informasi Baru Tersedia",
                    text = "Ada informasi baru di Board Info."
                )
            }

            prefs.edit()
                .putString(KEY_LAST_VIDEO_ID, latestVideoId)
                .putString(KEY_LAST_POSTER_ID, latestPosterId)
                .apply()

            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    private fun notify(notificationId: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Update Konten",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifikasi saat ada live atau informasi terbaru."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "content_update_worker"

        private const val CHANNEL_ID = "content_updates"
        private const val PREFS_NAME = "content_update_prefs"
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_LAST_VIDEO_ID = "last_video_id"
        private const val KEY_LAST_POSTER_ID = "last_poster_id"

        private const val VIDEO_NOTIFICATION_ID = 12001
        private const val POSTER_NOTIFICATION_ID = 12002
    }
}
