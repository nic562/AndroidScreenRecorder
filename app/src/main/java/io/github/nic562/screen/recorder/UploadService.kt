package io.github.nic562.screen.recorder

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nic562.screen.recorder.db.ApiInfo
import io.github.nic562.screen.recorder.db.VideoInfo
import io.github.nic562.screen.recorder.db.dao.DaoSession
import io.github.nic562.screen.recorder.tools.Http
import java.io.File
import java.util.*
import java.util.logging.Logger
import kotlin.collections.HashMap
import kotlin.math.roundToInt


/**
 * Created by Nic on 2021/11/28.
 */
class UploadService : Service() {
    private val notificationChannel = "uploadService"

    private val logger by lazy {
        Logger.getLogger("MediaRecordService")
    }
    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }

    private var thread: MyThread? = null
    private val taskList: ArrayList<Pair<ApiInfo, VideoInfo>> = arrayListOf()

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (val act = intent?.getStringExtra("action")) {
                "stop" -> {
                    thread?.let {
                        if (it.isAlive) it.interrupt()
                    }
                }
                else -> {
                    logger.warning("unKnow broadcastReceiver action: $act")
                }
            }
        }
    }

    private val notificationCancelIntent by lazy {
        PendingIntent.getBroadcast(
            this, 999,
            Intent(getString(R.string.broadcast_receiver_action_upload_service)).apply {
                putExtra("action", "stop")
            },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannel).apply {
            setSmallIcon(R.drawable.ic_baseline_arrow_circle_up_24)
            setContentTitle(getString(R.string.app_name))
            priority = NotificationCompat.PRIORITY_LOW
            setOnlyAlertOnce(true)
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_baseline_close_24,
                    getString(R.string.cancel),
                    notificationCancelIntent
                )
            )
        }
    }

    interface Callback {
        fun onUploadStart(videoID: Long)
        fun onUploadError(videoID: Long, e: Throwable)
        fun onUploadFinish(videoID: Long, result: String)
    }

    interface ReadFilesProgress {
        fun progress(
            videoID: Long,
            fileCount: Int,
            currentFileIdx: Int,
            currentFileTotal: Long,
            currentFileP: Long
        )
    }

    private class MyThread(
        val taskList: ArrayList<Pair<ApiInfo, VideoInfo>>,
        val progressListener: ReadFilesProgress? = null,
        val callback: Callback? = null
    ) : Thread("ApiUploadThread") {
        private var currentVideoID: Long? = null
        private val progressL by lazy {
            object : Http.ReadFilesProgress {
                override fun progress(
                    fileCount: Int,
                    currentFileIdx: Int,
                    currentFileTotal: Long,
                    currentFileP: Long
                ) {
                    currentVideoID?.let {
                        progressListener?.progress(
                            it,
                            fileCount,
                            currentFileIdx,
                            currentFileTotal,
                            currentFileP
                        )
                    }
                }
            }
        }

        private fun loopTasks() {
            while (taskList.size > 0) {
                val p = taskList.removeAt(0)
                val api = p.first
                val video = p.second
                currentVideoID = video.id
                callback?.onUploadStart(video.id)
                val rs: String? = try {
                    Http.upload(
                        api.url, api.uploadFileArgName, "video/mp4", File(video.filePath),
                        args = TreeMap<String, String>().apply {
                            val rows = api.body.split("\n")
                            for (r in rows) {
                                val rs = r.split("=")
                                if (rs.size != 2) {
                                    continue
                                }
                                put(rs[0], rs[1])
                            }
                        },
                        headers = HashMap<String, String>().apply {
                            val rows = api.header.split("\n")
                            for (r in rows) {
                                val rs = r.split("=")
                                if (rs.size != 2) {
                                    continue
                                }
                                put(rs[0], rs[1])
                            }
                        },
                        progressListener = progressL
                    )
                } catch (e: Exception) {
                    if (callback == null) {
                        throw e
                    } else {
                        callback.onUploadError(video.id, e)
                        null
                    }
                }
                rs?.let { callback?.onUploadFinish(video.id, it) }
            }
            currentVideoID = null
        }

        override fun run() {
            try {
                loopTasks()
            } catch (e: InterruptedException) {
                Log.w("UploadThread", "User interrupted uploadThread", e)
                currentVideoID?.let {
                    callback?.onUploadError(it, e)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannelCompat.Builder(
            notificationChannel,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName(getString(R.string.uploading_notification))
            .setDescription(getString(R.string.uploading_notification_description))
            .setLightsEnabled(true)
            .build()
            .apply {
                notificationManager.createNotificationChannel(this)
            }
        IntentFilter().apply {
            addAction(getString(R.string.broadcast_receiver_action_upload_service))
            registerReceiver(broadcastReceiver, this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.apply {
            val apiID = getLongExtra("apiID", -1L)
            val videoID = getLongExtra("videoID", -1L)
            if (apiID <= 0 || videoID <= 0) {
                logger.warning("apiID or videoID must > 0")
                return@apply
            }
            val api = getDB().apiInfoDao.load(apiID)
            if (api == null) {
                logger.warning("not found ApiInfo for: $apiID")
                return@apply
            }
            val video = getDB().videoInfoDao.load(videoID)
            if (video == null) {
                logger.warning("not found VideoInfo for: $videoID")
                return@apply
            }
            taskList.add(api to video)
            if (thread == null || !thread!!.isAlive || thread!!.isInterrupted) {
                thread = initThread().apply {
                    start()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        taskList.clear()
        thread?.let {
            if (it.isAlive) it.interrupt()
        }
        super.onDestroy()
    }

    private fun getDB(): DaoSession {
        return (application as App).getDB()
    }

    private fun sendNotification(videoID: Long, msg: String, uploadPercent: Int) {
        notificationBuilder.setProgress(100, uploadPercent, false)
            .setContentText(msg).build().apply {
                notificationManager.notify(videoID.toInt(), this)
            }
    }

    private fun closeNotification(videoID: Long) {
        notificationManager.cancel(videoID.toInt())
    }

    private fun notifyUi(action: String, id: Long, bundle: Bundle? = null) {
        Intent(getString(R.string.broadcast_receiver_action_upload_manager)).apply {
            bundle?.let {
                putExtras(it)
            }
            putExtra("id", id)
            putExtra("action", action)
            sendBroadcast(this)
        }
    }

    private fun initThread(): MyThread {
        return MyThread(
            taskList,
            progressListener = object : ReadFilesProgress {
                override fun progress(
                    videoID: Long,
                    fileCount: Int,
                    currentFileIdx: Int,
                    currentFileTotal: Long,
                    currentFileP: Long
                ) {
                    val ps = (currentFileP.toDouble() / currentFileTotal * 100).roundToInt()
                    sendNotification(
                        videoID,
                        "正在上传文件：${currentFileIdx}/${fileCount} [${currentFileP}/${currentFileTotal}]",
                        ps
                    )
                    notifyUi("progress", videoID, Bundle().apply {
                        putInt("progress", ps)
                    })
                }
            }, callback = object : Callback {
                override fun onUploadStart(videoID: Long) {
                    notifyUi("start", videoID)
                }

                override fun onUploadError(videoID: Long, e: Throwable) {
                    closeNotification(videoID)
                    notifyUi("error", videoID, Bundle().apply {
                        putString("error", e.message ?: e.localizedMessage ?: e.toString())
                    })
                }

                override fun onUploadFinish(videoID: Long, result: String) {
                    closeNotification(videoID)
                    notifyUi("finish", videoID, Bundle().apply {
                        putString("result", result)
                    })
                }
            })
    }
}