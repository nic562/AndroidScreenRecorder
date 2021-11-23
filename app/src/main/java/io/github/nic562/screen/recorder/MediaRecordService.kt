package io.github.nic562.screen.recorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.lang.Exception
import java.util.logging.Logger

class MediaRecordService : Service() {
    private val NOTIFICATION_CHANNEL = "recordService"
    private val NOTIFICATION_ID = 100

    private val logger by lazy {
        Logger.getLogger("MediaRecordService")
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingDuration = 0
    private val handler by lazy { Handler(mainLooper) }
    private val openBroadcastIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            999,
            Intent(this, RecordNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL).apply {
            setSmallIcon(R.drawable.ic_baseline_radio_button_checked_24)
            setContentTitle(getString(R.string.app_name))
            setDefaults(Notification.DEFAULT_ALL)
            setContentIntent(openBroadcastIntent)
        }
    }

    interface Callback {
        fun onRecordStart()
        fun onRecordError(e: Throwable)
        fun onRecordStop()
    }

    private var callback: Callback? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.recording_notification_description),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
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
                logger.warning("dstPath must be assign!")
                return@apply
            }
            logger.info("willing to record screen into [${dstPath}]")
            if (width < 1) {
                logger.warning("width must > 0")
                return@apply
            }
            if (height < 1) {
                logger.warning("height must > 0")
                return@apply
            }
            if (dpi < 1) {
                logger.warning("dpi must > 0")
                return@apply
            }

            val data = getParcelableExtra<Intent>("data")
            if (data == null) {
                logger.warning("intent-data is Null!")
                return@apply
            }
            try {
                recorder = createRecorder(width, height, dstPath).apply {
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
                if (callback == null) {
                    logger.severe(e.message)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startNotification() {
        startForeground(
            NOTIFICATION_ID,
            notificationBuilder
                .setContentText(getString(R.string.recording_num, 0))
                .build()
        )
    }

    private fun updateNotification() {
        if (isRecording) {
            handler.postDelayed({
                if (!isRecording) {
                    return@postDelayed
                }
                recordingDuration += 1
                notificationManager.notify(
                    NOTIFICATION_ID,
                    notificationBuilder.setContentText(
                        getString(R.string.recording_num, recordingDuration)
                    ).build()
                )
                updateNotification()
            }, 1000)
        }
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

    fun isNotificationEnable(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    fun isRecording(): Boolean {
        return isRecording
    }

//    fun pause() {
//        recorder?.let {
//            it.pause()
//        }
//    }
//
//    fun resume() {
//        recorder?.let {
//            it.resume()
//        }
//    }

    fun stop() {
        isRecording = false
        display?.release()
        display = null
        recorder?.let {
            it.setOnErrorListener(null)
            it.stop()
            it.reset()
            it.release()
        }
        closeNotification()
        callback?.onRecordStop()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

}