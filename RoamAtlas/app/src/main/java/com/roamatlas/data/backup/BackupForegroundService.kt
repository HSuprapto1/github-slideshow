package com.roamatlas.data.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.roamatlas.MainActivity
import com.roamatlas.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BackupForegroundService : Service() {

    @Inject lateinit var manager: EncryptedBackupManager
    @Inject lateinit var jobController: BackupJobController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        if (running) return START_NOT_STICKY
        when (action) {
            ACTION_RESTORE -> startRestore()
            ACTION_EXPORT -> startExport()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRestore() {
        val file = jobController.takeRestoreFile()
        val password = jobController.takePassword()
        if (file == null || password == null) {
            jobController.complete("Restore could not start. Try again.", isError = true)
            stopSelf()
            return
        }
        running = true
        startAsForeground("Restoring backup", "Keep RoamAtlas installed — restore continues in background.")
        scope.launch {
            try {
                val result = manager.restore(file, password) { stage ->
                    jobController.updateProgress(stage)
                    updateNotification("Restoring backup", stage)
                }
                jobController.complete(
                    message = "Restore successful.\n\n${result.summary}.\n\nOpen Dashboard to see your data.",
                    isError = false
                )
                updateNotification("Restore complete", result.summary)
            } catch (e: Exception) {
                jobController.complete(message = friendlyError(e), isError = true)
                updateNotification("Restore failed", friendlyError(e).lines().first())
            } finally {
                password.fill('\u0000')
                file.delete()
                running = false
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun startExport() {
        val uriString = jobController.takeExportUri()
        val password = jobController.takePassword()
        if (uriString.isNullOrBlank() || password == null) {
            jobController.complete("Backup could not start. Try again.", isError = true)
            stopSelf()
            return
        }
        running = true
        startAsForeground("Creating backup", "Export continues in the background.")
        scope.launch {
            try {
                val result = manager.export(Uri.parse(uriString), password) { stage ->
                    jobController.updateProgress(stage)
                    updateNotification("Creating backup", stage)
                }
                jobController.complete(
                    message = "Backup saved successfully.\n\n${result.summary}.",
                    isError = false
                )
                updateNotification("Backup complete", result.summary)
            } catch (e: Exception) {
                jobController.complete(message = friendlyError(e), isError = true)
                updateNotification("Backup failed", friendlyError(e).lines().first())
            } finally {
                password.fill('\u0000')
                running = false
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun startAsForeground(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(jobController.isWorking())
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Backup & restore",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while RoamAtlas restores or creates an encrypted backup"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("unlock", ignoreCase = true) ||
                text.contains("password", ignoreCase = true) ->
                "Wrong password or damaged backup file. Check the password and try again."
            text.contains("not a RoamAtlas", ignoreCase = true) ->
                "That file is not a RoamAtlas encrypted backup (.rabackup)."
            text.isNotBlank() -> text
            else -> "Backup operation failed. Please try again."
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "roamatlas_backup"
        private const val NOTIFICATION_ID = 44021
        const val ACTION_RESTORE = "com.roamatlas.backup.RESTORE"
        const val ACTION_EXPORT = "com.roamatlas.backup.EXPORT"

        fun startRestore(context: Context) {
            val intent = Intent(context, BackupForegroundService::class.java).apply {
                action = ACTION_RESTORE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startExport(context: Context) {
            val intent = Intent(context, BackupForegroundService::class.java).apply {
                action = ACTION_EXPORT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
