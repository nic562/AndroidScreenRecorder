package io.github.nic562.screen.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val REQ_CODE_SCREEN_RECORDER = 101
    private val REQ_CODE_NOTIFICATION_SETTING = 201

    private val dm by lazy {
        resources.displayMetrics
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var sv: MediaRecordService? = null
    private val svCallback = object : MediaRecordService.Callback {
        override fun onRecordStart() {
            binding.fabStart.visibility = View.GONE
            binding.fabStop.visibility = View.VISIBLE
        }

        override fun onRecordError(e: Throwable) {
            showMsg(e.localizedMessage ?: e.message ?: e.toString())
        }

        override fun onRecordStop() {
            binding.fabStop.visibility = View.GONE
            binding.fabStart.visibility = View.VISIBLE
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

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fabStart.setOnClickListener {
            if (sv?.isNotificationEnable() == false) {
                Snackbar.make(
                    binding.toolbar,
                    R.string.confirm2enable_notification,
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.sure) {
                        Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, applicationInfo.uid)
                            startActivityForResult(this, REQ_CODE_NOTIFICATION_SETTING)
                        }
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (event != 1) {
                                // 设置了1个Action的话，该Action占用的event为1. 则2为超时自动关闭
                                requestRecording()
                            }
                            super.onDismissed(transientBottomBar, event)
                        }
                    })
                    .show()
            } else {
                requestRecording()
            }
        }

        binding.fabStop.setOnClickListener {
            sv?.stop()
        }

        bindService(svIntent, svConn, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        unbindService(svConn)
        super.onDestroy()
    }

    private fun showMsg(msg: String) {
        Snackbar.make(binding.toolbar, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun requestRecording() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQ_CODE_SCREEN_RECORDER)
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent?.extras?.getBoolean("stopRecording") == true) {
            sv?.stop()
        }
        super.onNewIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_CODE_NOTIFICATION_SETTING) {
            requestRecording()
        } else if (requestCode == REQ_CODE_SCREEN_RECORDER && resultCode == RESULT_OK)
            data?.let {
                val file = File(getExternalFilesDir("screen"), "${System.currentTimeMillis()}.mp4")
                svIntent.apply {
                    putExtra("width", dm.widthPixels)
                    putExtra("height", dm.heightPixels)
                    putExtra("dpi", dm.densityDpi)
                    putExtra("dstPath", file.absolutePath)
                    putExtra("resultCode", resultCode)
                    putExtra("data", it)
                }
                startForegroundService(svIntent)
            }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}