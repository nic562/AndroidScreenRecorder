package io.github.nic562.screen.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.logging.Logger

class MediaRecordService : Service() {
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

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "recordService",
                "${getString(R.string.app_name)}录屏说明",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    class MyBinder(private val service: MediaRecordService) : Binder() {
        fun getService(): MediaRecordService {
            return service
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return MyBinder(this)
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
            logger.warning("willing to record screen into [${dstPath}]")
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
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startNotification() {
        startForeground(
            100,
            NotificationCompat.Builder(this, "recordService").apply {
                setSmallIcon(R.mipmap.ic_launcher)
                setContentTitle("录屏")
                setContentText("${getString(R.string.app_name)}录屏中...")
            }.build()
        )
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

    fun pause() {
        recorder?.let {
            it.pause()
        }
    }

    fun resume() {
        recorder?.let {
            it.resume()
        }
    }

    fun close() {
        display?.let {
            it.release()
        }
        display = null
        recorder?.let {
            it.setOnErrorListener(null)
            it.stop()
            it.reset()
            it.release()
        }
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

}