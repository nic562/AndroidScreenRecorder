package io.github.nic562.screen.recorder

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nic562.screen.recorder.db.ApiInfo
import io.github.nic562.screen.recorder.db.VideoInfo
import io.github.nic562.screen.recorder.db.dao.DaoSession
import io.github.nic562.screen.recorder.tools.Http
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


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

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannel).apply {
            setSmallIcon(R.drawable.ic_baseline_arrow_circle_up_24)
            setContentTitle(getString(R.string.app_name))
            priority = NotificationCompat.PRIORITY_LOW
            setOnlyAlertOnce(true)
        }
    }

    interface Callback {
        fun onUploadStart(videoID: Long)
        fun onUploadError(videoID: Long, e: Throwable)
        fun onUploadFinish(videoID: Long, result: String)
    }

    private class MyThread(
        val taskList: ArrayList<Pair<ApiInfo, VideoInfo>> = arrayListOf(),
        val progressListener: Http.ReadFilesProgress? = null,
        val callback: Callback? = null
    ) :
        Thread("ApiUploadThread") {
        override fun run() {
            while (taskList.size > 0) {
                val p = taskList.removeAt(0)
                val api = p.first
                val video = p.second
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
                        progressListener = progressListener
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
        }
    }

    private val thread: MyThread by lazy {
        MyThread()
    }

    private var callback: Callback? = null

    class SvBinder(private val service: UploadService) : Binder() {
        fun getService(): UploadService {
            return service
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return SvBinder(this)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannelCompat.Builder(
            notificationChannel,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName(getString(R.string.uploading_notification))
            .setDescription(getString(R.string.uploading_notification_description))
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .build()
            .apply {
                notificationManager.createNotificationChannel(this)
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
            thread.taskList.add(api to video)
            if (!thread.isAlive) {
                thread.start()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        thread.taskList.clear()
        super.onDestroy()
    }

    private fun getDB(): DaoSession {
        return (application as App).getDB()
    }
}