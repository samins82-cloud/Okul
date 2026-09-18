package com.elak.okulum.rehber

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RehberSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val session = RehberSession(applicationContext)
        val token = session.token ?: return Result.success()
        return try {
            val payload = RehberApi.sync(token)
            CallerCache(applicationContext).replaceFromSync(payload)
            session.lastSync = System.currentTimeMillis()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RehberSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "elak-rehber-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
