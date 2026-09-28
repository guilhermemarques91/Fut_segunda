package com.futsegunda.wear.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.futsegunda.wear.data.LiveEventRepository
import java.util.concurrent.TimeUnit

/**
 * Escoa a fila offline assim que o relógio pegar WiFi/LTE. O app também
 * tenta mandar cada evento na hora (LiveEventRepository); este worker é só
 * a garantia de fundo para quando o relógio ficou sem rede na quadra e só
 * reconecta bem mais tarde (ex.: voltando pro WiFi de casa).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = LiveEventRepository(applicationContext)
        val allSent = repo.flushPending()
        return if (allSent) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "fut_wear_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
