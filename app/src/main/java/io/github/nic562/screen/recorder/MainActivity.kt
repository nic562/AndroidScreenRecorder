package io.github.nic562.screen.recorder

import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    private val tag by lazy {
        this.javaClass.name
    }
    private val reqCodeScreenRecord = 101
    private val reqCodeNetTrafficStatisticsVpn = 102
    private val activityResultDataMap by lazy { HashMap<Int, Bundle>() }

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
                                tag,
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
        super.onNewIntent(intent)
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
            reqCodeNetTrafficStatisticsVpn -> {
                val bu: Bundle? = data?.extras ?: activityResultDataMap[requestCode]
                if (resultCode == RESULT_OK) {
                    bu?.let {
                        val apps = it.getStringArrayList("apps")
                        startService(
                            Intent(
                                this,
                                NetTrafficStatisticsVpnService::class.java
                            ).putExtras(it)
                        )
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

    fun openNetTrafficStatisticsVpnService(apps: List<String>?, bundle: Bundle? = null) {
        NetTrafficStatisticsVpnService.getActivityIntentForPrepareVpn(this).apply {
            val bu = Bundle()
            if (apps != null)
                bu.putStringArrayList("apps", ArrayList(apps))
            if (bundle != null)
                bu.putAll(bundle)
            when (this) {
                null -> {
                    onActivityResult(
                        reqCodeNetTrafficStatisticsVpn,
                        RESULT_OK,
                        Intent().putExtras(bu)
                    )
                }
                else -> {
                    activityResultDataMap[reqCodeNetTrafficStatisticsVpn] = bu
                    startActivityForResult(this, reqCodeNetTrafficStatisticsVpn)
                }
            }
        }
    }
}