package com.gdad.bags.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gdad.bags.GdadApplication
import com.gdad.bags.di.ProductionAppContainer
import java.util.concurrent.TimeUnit

object OutboxWork {
    const val UNIQUE_NAME = "gdad-mutation-outbox"

    fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<OutboxWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag(UNIQUE_NAME)
        .build()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request(),
        )
    }
}

class OutboxWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? GdadApplication)?.appContainer
            as? ProductionAppContainer ?: return Result.success()
        return when (container.outboxProcessor.processActive()) {
            OutboxProcessResult.RetryScheduled -> Result.retry()
            else -> Result.success()
        }
    }
}
