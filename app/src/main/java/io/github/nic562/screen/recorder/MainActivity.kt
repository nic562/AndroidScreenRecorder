package io.github.nic562.screen.recorder

import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.base.SomethingWithBackPressed
import io.github.nic562.screen.recorder.databinding.ActivityMainBinding
import io.github.nic562.screen.recorder.db.ApiInfo
import io.github.nic562.screen.recorder.db.dao.ApiInfoDao
import io.github.nic562.screen.recorder.db.dao.VideoInfoDao
import java.io.File
import java.net.URLDecoder
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG = MainActivity::javaClass.name
    }

    private val reqCodeScreenRecord = 101

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val dm by lazy {
        resources.displayMetrics
    }
    private val navController by lazy {
        findNavController(R.id.nav_host_fragment_content_main)
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var recordCustomKey: String? = null

    val recordStatusViewModel: RecordStatusViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(RecordStatusViewModel::class.java)
    }

    private var sv: MediaRecordService? = null
    private val svCallback = object : MediaRecordService.Callback {
        override fun onRecordStart() {
            recordStatusViewModel.recordingEvent.value = true
        }

        override fun onRecordError(e: Throwable) {
            Snackbar.make(
                binding.root,
                e.localizedMessage ?: e.message ?: e.toString(),
                Snackbar.LENGTH_LONG
            ).show()
            recordStatusViewModel.recordingEvent.value = false
        }

        override fun onRecordStop(videoID: Long?) {
            if (Config.getAutoUpload()) {
                startService(
                    Intent(
                        this@MainActivity,
                        UploadService::class.java
                    ).let {
                        it.putExtra("apiID", Config.getDefaultApiID())
                        it.putExtra("videoID", videoID)
                    })
            }
            recordStatusViewModel.recordingEvent.value = false
        }
    }

    private val svIntent by lazy {
        Intent(this, MediaRecordService::class.java)
    }

    private val svConn by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                sv = (service as MediaRecordService.SvBinder).getService().apply {
                    setCallback(svCallback)
                    recordStatusViewModel.recordingEvent.value = isRecording()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                sv?.stop()
                sv = null
            }
        }
    }

    private val accessBroadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_record_accessibility_service)
    }
    private var isAccessibilityOpen = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                accessBroadcastAction -> {
                    when (val act = intent.getStringExtra("action")) {
                        "create" -> {
                            isAccessibilityOpen = true
                        }
                        "error" -> {
                            isAccessibilityOpen = false
                            Snackbar.make(
                                binding.root,
                                R.string.failed_to_create_widgets,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            Log.w(
                                TAG,
                                "unKnow broadcastReceiver action for [${accessBroadcastAction}]: $act"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        bindService(svIntent, svConn, Context.BIND_AUTO_CREATE)

        IntentFilter().apply {
            addAction(accessBroadcastAction)
            registerReceiver(broadcastReceiver, this)
        }

        handingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent?.extras?.getBoolean("stopRecording") == true) {
            sendBroadcast(Intent(getString(R.string.broadcast_receiver_action_media_record_service)).apply {
                putExtra("action", "stopRecording")
            })
        } else if (intent?.extras?.getBoolean("startRecord") == true) {
            if (sv?.isRecording() == true) {
                return
            }
            requestRecording()
        }
        handingIntent(intent)
        super.onNewIntent(intent)
    }

    private fun formatUrlArgs(s: String?): String {
        if (s == null) {
            return ""
        }
        val fs = URLDecoder.decode(s, "utf8").split("&")
        return fs.joinToString("\n")
    }

    private val handler by lazy {
        Handler(mainLooper)
    }

    private fun handingIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (intent.hasExtra("setting")) {
            Config.setAutoToBack(intent.getBooleanExtra("auto_2back", false))
            Config.setAutoStopRecord(intent.getBooleanExtra("auto_stop_record", false))
            Config.setRecordCountDownSeconds(intent.getIntExtra("record_count_down_second", 3))
            Config.setAutoDelete(intent.getBooleanExtra("record_auto_delete", false))
        } else if (intent.hasExtra("ui")) {
            when (val action = intent.getStringExtra("ui")) {
                "startRecord" -> {
                    recordCustomKey = intent.getStringExtra("key")
                    handler.postDelayed({
                        sendBroadcast(Intent(getString(R.string.broadcast_receiver_action_remote_calling_ui)).apply {
                            putExtra("action", action)
                        })
                    }, 300)
                }
            }
        } else if (intent.hasExtra("data")) {
            when (val action = intent.getStringExtra("data")) {
                "api" -> {
                    val title = intent.getStringExtra("title")
                    val url = intent.getStringExtra("url")
                    val method = intent.getStringExtra("method")
                    val uploadFileArgName = intent.getStringExtra("uploadFileArgName")
                    var header = intent.getStringExtra("header")
                    var body = intent.getStringExtra("body")
                    val isBodyEncoding = intent.getBooleanExtra("isBodyEncoding", false)

                    if (title == null || url == null || method == null || uploadFileArgName == null) {
                        Log.w(TAG, "api args can not be null!")
                        return
                    }
                    header = formatUrlArgs(header)
                    body = formatUrlArgs(body)
                    val apiDao = getApp().getDB().apiInfoDao
                    val q =
                        apiDao.queryBuilder().where(ApiInfoDao.Properties.Title.eq(title)).limit(1)
                            .list()
                    if (q.size > 0) {
                        val api = q[0]
                        api.method = ApiInfo.Method.valueOf(method)
                        api.url = url
                        api.header = header
                        api.body = body
                        api.isBodyEncoding = isBodyEncoding
                        api.uploadFileArgName = uploadFileArgName
                        apiDao.update(api)
                    } else {
                        val api = ApiInfo(
                            null,
                            title,
                            url,
                            ApiInfo.Method.valueOf(method),
                            header,
                            body,
                            isBodyEncoding,
                            uploadFileArgName
                        )
                        apiDao.insert(api)
                    }
                }

                "upload" -> {
                    val apiTitle = intent.getStringExtra("apiTitle")
                    val videoKeys = intent.getStringExtra("videoKeys")
                    if (apiTitle == null || videoKeys == null) {
                        Log.w(TAG, "upload args can not be null!")
                        return
                    }
                    val apis = getApp().getDB().apiInfoDao.queryBuilder().where(ApiInfoDao.Properties.Title.eq(apiTitle)).limit(1).list()
                    if (apis.size == 0) {
                        Log.w(TAG, "upload api matching `${apiTitle}` failed!")
                        return
                    }
                    val videos = getApp().getDB().videoInfoDao.queryBuilder().where(VideoInfoDao.Properties.CustomKey.`in`(videoKeys.split(","))).list()
                    if (videos.size == 0) {
                        Log.w(TAG, "find not videos for `${videoKeys}`!")
                        return
                    }
                    val api = apis[0]
                    for (v in videos) {
                        Log.i(TAG, "Willing to upload VideoInfo[${v.id} - ${v.customKey}] to ${api.title}")
                        UploadService.startUpload(this, v, api)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        getApp().onResume()
    }

    override fun onPause() {
        super.onPause()
        getApp().onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        unbindService(svConn)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (!isTopFragmentConsumeBackPress()) {
            if (sv?.isRecording() == true || isAccessibilityOpen) {
                moveTaskToBack(true)
                return
            }
        }
        super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            reqCodeScreenRecord -> {
                if (resultCode == RESULT_OK) {
                    data?.let {
                        startRecordService(it)
                        if (Config.getAutoToBack())
                            moveTaskToBack(true)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getApp(): App {
        return application as App
    }

    private fun startRecordService(recordData: Intent) {
        svIntent.apply {
            putExtra("width", dm.widthPixels)
            putExtra("height", dm.heightPixels)
            putExtra("dpi", dm.densityDpi)
            putExtra(
                "dstPath", File(
                    getExternalFilesDir("screen"),
                    "${System.currentTimeMillis()}.mp4"
                ).absolutePath
            )
            putExtra("resultCode", RESULT_OK)
            putExtra("data", recordData)
            putExtra("customKey", recordCustomKey)
            recordCustomKey = null // 消费后则清掉，以保证不会重复
            startForegroundService(this)
        }
    }

    fun requestRecording() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            reqCodeScreenRecord
        )
    }

    fun getRecordService(): MediaRecordService? {
        return sv
    }

    fun openAccessibilityService() {
        startService(Intent(this, RecordAccessibilityService::class.java))
    }

    private fun isTopFragmentConsumeBackPress() =
        getTopFragment<SomethingWithBackPressed>()?.onBackPressed() == true

    private inline fun <reified T> getTopFragment(): T? =
        supportFragmentManager.fragments.firstOrNull()?.let {
            it.childFragmentManager.fragments[0] as? T
        }
}