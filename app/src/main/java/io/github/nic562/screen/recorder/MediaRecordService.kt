package io.github.nic562.screen.recorder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nic562.screen.recorder.db.VideoInfo
import io.github.nic562.screen.recorder.tools.Video
import java.io.File
import java.util.*

class MediaRecordService : Service() {
    private val notificationChannel = "recordService"
    private val notificationID = 100
    private val tag = "MediaRecordService"

    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingDuration = 0
    private var currentDstFilePath: String? = null
    private val handler by lazy { Handler(mainLooper) }

    private val stopRecordBroadcastIntent by lazy {
        Intent(this, RecordNotificationReceiver::class.java)
    }
    private val notificationOpenIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            999,
            stopRecordBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannel).apply {
            setSmallIcon(R.drawable.ic_stat_record)
            setContentTitle(getString(R.string.app_name))
            setContentIntent(notificationOpenIntent)
            priority = NotificationCompat.PRIORITY_HIGH
            setOnlyAlertOnce(true)
        }
    }

    private val broadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_media_record_service)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                broadcastAction -> {
                    when (intent.getStringExtra("action")) {
                        "stopRecording" -> {
                            stop()
                        }
                    }
                }
                else -> {
                    Log.w(tag, "unKnow broadcastReceiver action: ${intent?.action}")
                }
            }
        }
    }

    interface Callback {
        fun onRecordStart()
        fun onRecordError(e: Throwable)
        fun onRecordStop(videoID: Long?)
    }

    private var callback: Callback? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannelCompat.Builder(
            notificationChannel,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(getString(R.string.recording_notification))
            .setDescription(getString(R.string.recording_notification_description))
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .build()
            .apply {
                notificationManager.createNotificationChannel(this)
            }
        IntentFilter().apply {
            addAction(broadcastAction)
            registerReceiver(broadcastReceiver, this)
        }
    }

    class SvBinder(private val service: MediaRecordService) : Binder() {
        fun getService(): MediaRecordService {
            return service
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return SvBinder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.apply {
            val width = getIntExtra("width", -1)
            val height = getIntExtra("height", -1)
            val dpi = getIntExtra("dpi", -1)
            val dstPath = getStringExtra("dstPath")
            val resultCode = getIntExtra("resultCode", -1)
            if (dstPath == null) {
                Log.w(tag, "dstPath must be assign!")
                return@apply
            }
            if (width < 1) {
                Log.w(tag, "width must > 0")
                return@apply
            }
            if (height < 1) {
                Log.w(tag, "height must > 0")
                return@apply
            }
            if (dpi < 1) {
                Log.w(tag, "dpi must > 0")
                return@apply
            }
            Log.i(tag, "willing to record screen[$width x $height] with Dpi#$dpi into [${dstPath}]")

            val data = getParcelableExtra<Intent>("data")
            if (data == null) {
                Log.w(tag, "intent-data is Null!")
                return@apply
            }
            try {
                recorder = createRecorder(width, height, dstPath).apply {
                    currentDstFilePath = dstPath
                    prepare()
                    startNotification()
                    mediaProjectionManager.getMediaProjection(resultCode, data)?.let {
                        display = it.createVirtualDisplay(
                            "MediaRecordService-Display",
                            width,
                            height,
                            dpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            surface,
                            null, null
                        )
                    }
                    start()
                    isRecording = true
                    recordingDuration = 0
                    updateNotification()
                    callback?.onRecordStart()
                }
            } catch (e: Exception) {
                callback?.onRecordError(e)
                Log.e(tag, "MediaRecorder start error: ", e)
                e.printStackTrace()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        stop()
        super.onDestroy()
    }

    private fun sendBroadcastRecordDuration(duration: Int) {
        sendBroadcast(Intent(broadcastAction).apply {
            putExtra("action", "duration")
            putExtra("duration", duration)
        })
    }

    private fun startNotification() {
        startForeground(
            notificationID,
            notificationBuilder
                .setContentText(getString(R.string.recording_num, 0))
                .build()
        )
        sendBroadcastRecordDuration(0)
    }

    private fun updateNotification() {
        if (isRecording) {
            handler.postDelayed({
                if (!isRecording) {
                    return@postDelayed
                }
                recordingDuration += 1
                notify(getString(R.string.recording_num, recordingDuration))
                sendBroadcastRecordDuration(recordingDuration)
                if (Config.getAutoStopRecord() && recordingDuration >= Config.getRecordCountDownSeconds()) {
                    when {
                        (application as App).isActivityVisible -> {
                            stop()
                        }
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                            startActivity(
                                RecordNotificationReceiver.makeCallUpAppAndStopRecordIntent(
                                    this
                                )
                            )
                        }
                        else -> {
                            notify(getString(R.string.record_finished_need_to_upload))
                        }
                    }
                    return@postDelayed
                }
                updateNotification()
            }, 1000)
        }
    }

    private fun notify(msg: String) {
        notify(notificationBuilder.setContentText(msg).build())
    }

    private fun notify(n: Notification) {
        notificationManager.notify(notificationID, n)
    }

    private fun closeNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createRecorder(
        width: Int,
        height: Int,
        dstPath: String,
        fps: Int = 60,
        bitrate: Int = 6000000
    ): MediaRecorder {
        return MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
//            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(dstPath)
            setVideoSize(width, height)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(bitrate)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun removeCallback() {
        this.callback = null
    }

    fun isNotificationEnable(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    fun stop() {
        isRecording = false
        display?.release()
        display = null
        recorder?.let {
            try {
                it.setOnErrorListener(null)
                it.stop()
                it.reset()
                it.release()
            } catch (e: Exception) {
                Log.w(tag, "stop recorder error:", e)
            }
        }
        closeNotification()
        currentDstFilePath?.let {
            val img = File(
                getExternalFilesDir("img"),
                "${System.currentTimeMillis()}.jpg"
            )
            val pv: File = try {
                Video.getThumb2File(it, img.absolutePath)
                    ?: throw java.lang.Exception("[${it}] is an invalid video file? Get thumb Error")
            } catch (e: java.lang.Exception) {
                Log.w(this.javaClass.simpleName, e)
                callback?.onRecordError(e)
                return@let
            }
            VideoInfo().apply {
                createTime = Date()
                filePath = it
                previewPath = pv.absolutePath
                getApp().getDB().videoInfoDao.insert(this)
                getApp().getDB().videoInfoDao.detachAll()
                callback?.onRecordStop(this.id)
            }
            currentDstFilePath = null
        }
        sendBroadcast(Intent(broadcastAction).apply {
            putExtra("action", "finish")
        })
        callback?.onRecordStop(null)
    }

    private fun getApp(): App {
        return (application as App)
    }
}