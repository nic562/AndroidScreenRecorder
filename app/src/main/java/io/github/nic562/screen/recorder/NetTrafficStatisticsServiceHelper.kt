package io.github.nic562.screen.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import io.github.nic562.screen.recorder.base.SomethingWithNotification
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.time.LocalDateTime

interface NetTrafficStatisticsServiceHelper : SomethingWithNotification {

    companion object {
        const val RECEIVE_BROADCAST_ACTION_NAME_ID =
            R.string.broadcast_receiver_action_net_traffic_statistics_receive

        fun sendBroadcastToStop(ctx: Context) {
            ctx.sendBroadcast(
                Intent(ctx.getString(RECEIVE_BROADCAST_ACTION_NAME_ID)).putExtra(
                    "action",
                    "stop"
                )
            )
        }
    }

    /**
     * @see [createSendNetTrafficBroadcastIntent]
     */
    val sendNetTrafficBroadcastIntent: Intent

    /**
     * @see [createReceiveNetTrafficBroadcastAction]
     */
    val receiveNetTrafficBroadcastAction: String

    /**
     * @see [createReceiveNetTrafficBroadcastReceiver]
     */
    val receiveNetTrafficBroadcastReceiver: BroadcastReceiver

    /**
     * 保存的文件，自动赋值
     * 初始化时置空null 即可
     */
    var saveFile: NetTrafficStatisticsLogHandler?
    var logDateTime: Boolean

    fun onNetTrafficReceiveActionToStop()

    fun onNetTrafficReceiveActionToStart(intent: Intent)

    /**
     * @see [sendNetTrafficBroadcastWorking(network, downByteSize, upByteSize)]
     */
    fun sendNetTrafficBroadcastWorking(downByteSize: Long, upByteSize: Long)

    private fun registerReceiveNetTrafficBroadcastReceiver() {
        IntentFilter().apply {
            addAction(receiveNetTrafficBroadcastAction)
            getContext().registerReceiver(receiveNetTrafficBroadcastReceiver, this)
        }
    }

    private fun unregisterReceiveNetTrafficBroadcastReceiver() {
        getContext().unregisterReceiver(receiveNetTrafficBroadcastReceiver)
    }

    fun createReceiveNetTrafficBroadcastAction(): String {
        return getContext().getString(RECEIVE_BROADCAST_ACTION_NAME_ID)
    }

    fun createSendNetTrafficBroadcastIntent(): Intent {
        return Intent(getContext().getString(R.string.broadcast_receiver_action_net_traffic_statistics))
    }

    fun createReceiveNetTrafficBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    receiveNetTrafficBroadcastAction -> {
                        when (intent.getStringExtra("action")) {
                            "stop" -> {
                                onNetTrafficReceiveActionToStop()
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendNetTrafficBroadcast(action: String, bundle: Bundle? = null) {
        getContext().sendBroadcast(sendNetTrafficBroadcastIntent.apply {
            putExtra("action", action)
            if (bundle != null) {
                putExtras(bundle)
            }
        })
    }

    fun sendNetTrafficBroadcastCreate() {
        sendNetTrafficBroadcast("create")
    }

    fun sendNetTrafficBroadcastDestroy() {
        sendNetTrafficBroadcast("destroy")
    }

    fun sendNetTrafficBroadcastError(error: String) {
        sendNetTrafficBroadcast("error", Bundle().apply {
            putString("error", error)
        })
    }

    fun sendNetTrafficBroadcastWorking(network: String, downByteSize: Long, upByteSize: Long) {
        sendNetTrafficBroadcast("working", Bundle().apply {
            putString("network", network)
            putLong("downByteSize", downByteSize)
            putLong("upByteSize", upByteSize)
        })
    }

    fun onNetTrafficStatistics(idx: Int, downByteSize: Long, upByteSize: Long) {
        val msg = "${
            getContext().getString(R.string.download_speed, downByteSize / 1024.0)
        } - ${getContext().getString(R.string.upload_speed, upByteSize / 1024.0)}"
        notify(msg)
        sendNetTrafficBroadcastWorking(downByteSize, upByteSize)
        if (logDateTime) {
            val current = LocalDateTime.now()
            saveFile?.write("$current\t$downByteSize\t$upByteSize")
        } else {
            saveFile?.write("$idx\t$downByteSize\t$upByteSize")
        }
    }

    fun onNetTrafficStatisticsCreate() {
        registerReceiveNetTrafficBroadcastReceiver()
        sendNetTrafficBroadcastCreate()
    }

    fun onNetTrafficStatisticsDestroy() {
        unregisterReceiveNetTrafficBroadcastReceiver()
        saveFile?.close()
        sendNetTrafficBroadcastDestroy()
    }

    fun onNetTrafficStartCommand(intent: Intent?, flags: Int, startId: Int): Boolean {
        when (intent?.getStringExtra("action")) {
            "start" -> {
                logDateTime = intent.getBooleanExtra("logDateTime", false)
                val path = intent.getStringExtra("save2File")
                if (path != null && path.isNotBlank()) {
                    val onError = object : NetTrafficStatisticsLogHandler.OnError {
                        override fun error(e: String) {
                            sendNetTrafficBroadcastError(e)
                        }
                    }
                    try {
                        saveFile = NetTrafficStatisticsLogHandler(path, onError)
                        Log.w(javaClass.simpleName, "Willing to save log to [$path]")
                    } catch (e: Exception) {
                        Log.w(javaClass.simpleName, "create NetTrafficStatisticsLogHandler($path) error:", e)
                        onError.error(e.toString())
                    }
                }
                onNetTrafficReceiveActionToStart(intent)
                return true
            }
            "stop" -> {
                onNetTrafficReceiveActionToStop()
                return true
            }
        }
        return false
    }

    class NetTrafficStatisticsLogHandler(val path: String, val onError: OnError) : Closeable {
        interface OnError {
            fun error(e: String)
        }

        private val tag = javaClass.simpleName
        private val file = File(path)
        private val fs: FileWriter by lazy {
            FileWriter(file, true)
        }

        init {
            if (!file.exists()) {
                val dirPath = File(file.parent!!)
                if (!dirPath.exists()) {
                    dirPath.mkdirs()
                }
                file.createNewFile()
            }
        }

        fun write(m: String) {
            try {
                fs.write(m)
                fs.write("\n")
                fs.flush()
            } catch (e: Exception) {
                Log.e(tag, "file write error:", e)
            }
        }

        override fun close() {
            try {
                fs.flush()
                fs.close()
            } catch (e: Exception) {
                Log.e(tag, "file close error:", e)
            }
        }
    }

}