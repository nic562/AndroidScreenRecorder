package io.github.nic562.screen.recorder

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.nic562.screen.recorder.component.FloatWidgetsManager
import java.lang.Exception

/**
 * Created by Nic on 2021/12/3.
 */
class RecordAccessibilityService : AccessibilityService() {
    private val tag = this.javaClass.name
    private var viewsManager: FloatWidgetsManager? = null

    private var viewsValid = false

    private val broadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_record_accessibility_service)
    }

    private val recordStatusBroadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_media_record_service)
    }

    private var isRecording = false

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                recordStatusBroadcastAction -> {
                    when (intent.getStringExtra("action")) {
                        "finish" -> {
                            isRecording = false
                            viewsManager?.mainView?.apply {
                                setText("")
                                setRecordColor(Color.WHITE)
                            }
                        }
                        "duration" -> {
                            isRecording = true
                            viewsManager?.mainView?.apply {
                                setText(intent.getIntExtra("duration", 0).toString())
                                setRecordColor(Color.RED)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        IntentFilter().apply {
//            addAction(broadcastAction)
            addAction(recordStatusBroadcastAction)
            registerReceiver(broadcastReceiver, this)
        }
        try {
            if (viewsManager == null) {
                viewsManager = FloatWidgetsManager(this)
            }
        } catch (e: Exception) {
            Log.e(tag, "init widgets error:", e)
            sendBroadcast(Intent(broadcastAction).apply {
                putExtra("action", "error")
            })
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(broadcastReceiver)
            viewsManager?.destroy()
        } catch (e: Exception) {
            Log.e(tag, "destroy viewsManager error:", e)
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (viewsManager?.show()) {
            false ->
                sendBroadcast(Intent(broadcastAction).apply {
                    putExtra("action", "error")
                })
            true ->
                sendBroadcast(Intent(broadcastAction).apply {
                    putExtra("action", "create")
                })
        }
        validViews()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    private fun validViews() {
        viewsManager?.mainView?.apply {
            if (viewsValid) {
                return@apply
            }
            setOnRecordClickListener {
                if (isRecording) {
                    openMainActivityForCalling("stopRecording")
                } else {
                    openMainActivityForCalling("startRecord")
                }
            }
            setOnCloseClickListener {
                viewsManager?.destroy()
                disableSelf()
                viewsValid = false
            }

            viewsValid = true
        }
    }

    private fun openMainActivityForCalling(cmd: String) {
        startActivity(
            Intent(
                this@RecordAccessibilityService,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(
                    cmd,
                    true
                )
            })
    }
}