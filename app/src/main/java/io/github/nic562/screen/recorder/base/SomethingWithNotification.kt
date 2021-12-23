package io.github.nic562.screen.recorder.base

import android.app.Notification
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

interface SomethingWithNotification : SomethingWithContext {
    val notificationChannel: String

    val notificationManager: NotificationManagerCompat

    val notificationBuilder: NotificationCompat.Builder

    fun initNotificationManager(): NotificationManagerCompat {
        return NotificationManagerCompat.from(getContext())
    }

    fun getNotificationTitle(): String {
        throw NotImplementedError()
    }

    fun getNotificationDescription(): String {
        throw NotImplementedError()
    }

    @DrawableRes
    fun getNotificationSmallIconId(): Int {
        throw NotImplementedError()
    }

    fun createNotificationChannel() {
        createNotificationChannel(getNotificationTitle(), getNotificationDescription())
    }

    fun createNotificationChannel(name: String, description: String) {
        NotificationChannelCompat.Builder(
            notificationChannel,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(name)
            .setDescription(description)
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .build()
            .apply {
                notificationManager.createNotificationChannel(this)
            }
    }

    fun initNotificationBuilder(alertOnce: Boolean = true): NotificationCompat.Builder {
        return initNotificationBuilder(getNotificationSmallIconId(), getNotificationTitle())
    }

    fun initNotificationBuilder(
        @DrawableRes smallIconId: Int,
        title: String,
        alertOnce: Boolean = true
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(getContext(), notificationChannel).apply {
            setSmallIcon(smallIconId)
            setContentTitle(title)
            priority = NotificationCompat.PRIORITY_HIGH
            if (alertOnce)
                setOnlyAlertOnce(true)
        }
    }

    fun sendNotification(
        notificationBuilder: NotificationCompat.Builder,
        msg: String,
        notificationID: Int
    ) {
        notify(notificationID, notificationBuilder.setContentText(msg).build())
    }

    fun notify(notificationID: Int, n: Notification) {
        notificationManager.notify(notificationID, n)
    }

    fun cancelNotification(notificationID: Int) {
        notificationManager.cancel(notificationID)
    }
}