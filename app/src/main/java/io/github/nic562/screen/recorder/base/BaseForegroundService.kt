package io.github.nic562.screen.recorder.base

import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nic562.screen.recorder.R


abstract class BaseForegroundService : Service(), SomethingWithNotification {

    companion object {
        fun startForegroundService(
            ctx: ContextWrapper,
            serviceClass: Class<out BaseForegroundService>,
            extras: Bundle? = null
        ) {
            ctx.startForegroundService(Intent(ctx, serviceClass).apply {
                if (extras != null) {
                    putExtras(extras)
                }
            })
        }
    }

    protected abstract val notificationID: Int

    protected open var autoStartForeground = true

    override val notificationBuilder: NotificationCompat.Builder by lazy {
        initNotificationBuilder()
    }

    override val notificationManager: NotificationManagerCompat by lazy {
        initNotificationManager()
    }

    override fun getContext(): Context {
        return this
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (autoStartForeground)
            nowStartForeground()
    }

    override fun onDestroy() {
        closeNotification()
        // 前台服务必须用这个方法关闭通知，cancel是无效的
        super.onDestroy()
    }

    /***
     * 注意，[startForegroundService] 调用后5秒内必须调用 [startForeground]
     */
    protected fun nowStartForeground() {
        startForeground(
            notificationID,
            notificationBuilder.setContentText(getString(R.string.service_preparing)).build()
        )
    }

    protected fun closeNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    protected fun notify(msg: String) {
        sendNotification(notificationBuilder, msg, notificationID)
    }
}