package com.futsegunda.live.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.futsegunda.live.data.LiveEventRepository
import java.util.concurrent.TimeUnit

/**
 * Reenvia a fila offline assim que houver rede. Roda a cada 15 min (mínimo
 * permitido pelo WorkManager para trabalho periódico) — o app também chama
 * `flushPending()` direto ao reabrir/reconectar, então esse worker é só a
 * garantia de fundo caso o app fique fechado.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = LiveEventRepository(applicationContext)
        val allSent = repo.flushPending()
        return if (allSent) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "fut_live_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
