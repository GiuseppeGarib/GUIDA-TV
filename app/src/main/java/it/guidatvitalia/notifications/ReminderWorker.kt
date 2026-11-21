package it.guidatvitalia.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.guidatvitalia.R

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString("program_title") ?: "Programma TV"
        val channel = inputData.getString("channel_name") ?: "Guida TV"
        val id = inputData.getInt("notification_id", title.hashCode())
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Promemoria programmi", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sta per iniziare: $title")
            .setContentText(channel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
            Result.success()
        } catch (_: SecurityException) { Result.failure() }
    }
    companion object { const val CHANNEL_ID = "tv_program_reminders" }
}
