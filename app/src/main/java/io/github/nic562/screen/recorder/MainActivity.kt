package io.github.nic562.screen.recorder

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.databinding.ActivityMainBinding
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val dm by lazy {
        resources.displayMetrics
    }
    private val navController by lazy {
        findNavController(R.id.nav_host_fragment_content_main)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        bindService(svIntent, svConn, Context.BIND_AUTO_CREATE)
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent?.extras?.getBoolean("stopRecording") == true) {
            sendBroadcast(Intent(getString(R.string.broadcast_receiver_action_media_record_service)).apply {
                putExtra("action", "stopRecording")
            })
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
        unbindService(svConn)
        super.onDestroy()
    }

    private fun getApp(): App {
        return application as App
    }

    fun startRecordService(recordData: Intent) {
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

    fun getRecordService(): MediaRecordService? {
        return sv
    }
}