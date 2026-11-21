package it.guidatvitalia.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduling {
    fun scheduleEpgRefresh(context: Context, intervalHours: Int = 12) {
        val interval = intervalHours.coerceIn(6, 24)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(interval.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "epg_refresh", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}
