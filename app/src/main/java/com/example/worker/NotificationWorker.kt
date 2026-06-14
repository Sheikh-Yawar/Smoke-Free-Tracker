package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SmokeFreeApp
import kotlinx.coroutines.flow.first

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repository = (context.applicationContext as SmokeFreeApp).repository
        val settings = repository.userSettings.first()

        val quitTime = settings.quitDateMillis
        val diffMillis = if (quitTime > 0L && System.currentTimeMillis() > quitTime) System.currentTimeMillis() - quitTime else 0L
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        val message = if (diffDays <= 0) {
            "You are starting out on your smoke-free journey. Please don't break your streak today. If you don't break the pattern of smoking, the loop will continue."
        } else {
            "You're $diffDays days smoke-free, please don't break the streak. If you don't break the pattern of smoking, the loop will continue."
        }

        val channelId = "smokefree_daily"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Streak Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sends daily reminders about your quit progress and encouragement to stay smoke-free"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Stay Strong!")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.star_on) // robust system icon
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(99, notification)
        return Result.success()
    }
}
