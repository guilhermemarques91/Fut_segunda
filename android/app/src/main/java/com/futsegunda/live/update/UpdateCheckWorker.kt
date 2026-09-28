package com.futsegunda.live.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.futsegunda.live.R
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "fut_update_prefs"
private const val KEY_LAST_NOTIFIED = "last_notified_version_code"
private const val CHANNEL_ID = "app_updates"
private const val NOTIFICATION_ID = 1001

/**
 * Roda de tempos em tempos e, se o painel web publicou uma build mais nova
 * que a instalada, mostra uma notificação — tocar nela abre o link de
 * download no navegador (que baixa e oferece "instalar", igual qualquer
 * apk baixado manualmente). Só notifica uma vez por versão nova
 * (`KEY_LAST_NOTIFIED`), pra não repetir a cada 6h enquanto o usuário
 * ainda não atualizou.
 */
class UpdateCheckWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val latest = try {
            UpdateChecker.checkForUpdate()
        } catch (e: Exception) {
            null
        } ?: return Result.success()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED, 0)
        if (latest.versionCode <= lastNotified) return Result.success()

        showUpdateNotification(context, latest.versionName)
        prefs.edit().putInt(KEY_LAST_NOTIFIED, latest.versionCode).apply()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "fut_update_check"

        fun schedule(context: Context) {
            createChannel(context)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        fun showUpdateNotification(context: Context, versionName: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return // usuário não concedeu a permissão de notificação — sem isso não dá pra avisar
            }
            createChannel(context)
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ANDROID_DOWNLOAD_URL))
            val pendingIntent = PendingIntent.getActivity(
                context, 0, downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Nova versão disponível: $versionName")
                .setContentText("Toque para baixar e instalar a atualização")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            try {
                androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // permissão revogada entre o check acima e o notify — ignora silenciosamente
            }
        }
    }
}
