package it.guidatvitalia.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("guida_tv", Context.MODE_PRIVATE)
        val url = prefs.getString("epg_url", null)?.trim().orEmpty()
        if (url.isBlank()) return Result.success()
        return try {
            val data = EpgSourceRepository().download(url)
            EpgCache(applicationContext).use { it.replace(data) }
            prefs.edit().putLong("last_epg_update", System.currentTimeMillis()).apply()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
