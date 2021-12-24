package io.github.nic562.screen.recorder.base

import android.app.Notification
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.R

interface SomethingWithNotification : SomethingWithContext {
    val notificationChannel: String

    /**
     * @see [initNotificationManager]
     */
    val notificationManager: NotificationManagerCompat

    /**
     * @see [initNotificationBuilder]
     */
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

    fun isNotificationEnable(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    fun checkNotificationEnable(
        snackBaseView: View,
        onOKAction: View.OnClickListener,
        callback: Snackbar.Callback? = null
    ): Boolean {
        if (isNotificationEnable())
            return true
        Snackbar.make(
            snackBaseView,
            R.string.confirm2enable_notification,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.sure, onOKAction).apply {
            if (callback != null) {
                addCallback(callback)
            }
        }.show()
        return false
    }

    fun getNotificationSettingsIntent(): Intent {
        return Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, getContext().packageName)
            putExtra(
                Settings.EXTRA_CHANNEL_ID,
                getContext().applicationInfo.uid
            )
        }
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

    /**
     * @see [notify]
     */
    fun notify(msg: String)

    fun cancelNotification(notificationID: Int) {
        notificationManager.cancel(notificationID)
    }
}