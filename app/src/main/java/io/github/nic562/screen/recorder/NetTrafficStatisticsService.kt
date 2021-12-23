package io.github.nic562.screen.recorder

import android.content.ContextWrapper
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import io.github.nic562.screen.recorder.base.BaseForegroundService

/**
 * 基于[TrafficStats] 的网络请求统计服务，仅可以统计全局流量
 */
class NetTrafficStatisticsService : BaseForegroundService() {

    companion object {
        fun startForegroundService(ctx: ContextWrapper, extras: Bundle? = null) {
            startForegroundService(
                ctx,
                NetTrafficStatisticsService::class.java,
                extras
            )
        }
    }

    override val notificationChannel: String = "NetTrafficStatisticsService"

    override val notificationID = 8888

    private var trafficThread: TrafficThread? = null

    private val broadcastIntent by lazy {
        Intent(getString(R.string.broadcast_receiver_action_net_traffic_statistics))
    }

    override fun getNotificationTitle(): String {
        return getString(R.string.net_traffic_statistics)
    }

    override fun getNotificationDescription(): String {
        return getString(R.string.net_traffic_statistics_description)
    }

    override fun getNotificationSmallIconId(): Int {
        return R.drawable.ic_baseline_import_export_24
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        sendBroadcast(broadcastIntent.putExtra("action", "create"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "start" -> {
                startTrafficThread()
            }
            "stop" -> {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        if (trafficThread != null || trafficThread?.isAlive == true) {
            trafficThread?.interrupt()
            trafficThread = null
        }
        sendBroadcast(broadcastIntent.putExtra("action", "destroy"))
        super.onDestroy()
    }

    private fun startTrafficThread() {
        if (trafficThread == null || trafficThread?.isAlive == false) {
            trafficThread = TrafficThread(object : TrafficThread.Callback {
                override fun onData(downByteSize: Long, upByteSize: Long) {
                    val msg = "${
                        getString(R.string.download_speed, downByteSize / 1024.0)
                    } - ${getString(R.string.upload_speed, upByteSize / 1024.0)}"
//                    Log.w(notificationChannel, msg)
                    notify(msg)
                    sendBroadcast(broadcastIntent.apply {
                        putExtra("action", "working")
                        putExtra("downByteSize", downByteSize)
                        putExtra("upByteSize", upByteSize)
                    })
                }
            }).apply {
                start()
            }
        }
    }

    private class TrafficThread(
        private val callback: Callback,
        private val intervalSecond: Int = 1
    ) : Thread("TrafficThread") {

        interface Callback {
            fun onData(downByteSize: Long, upByteSize: Long)
        }

        /**
         * 统计所有系统流量
         * 注意：获取到的数据为 从<系统启动>到<当前时间>所有的流量, 可通过获取一次后再间隔n秒再获取一次，两值之差为某个时间段内的流量总和
         * 由于[TrafficStats.getUidRxBytes] 从Android [Build.VERSION_CODES.N] 开始不再支持获取其他应用的流量，而只能返回0
         * 可见：https://developer.android.com/reference/android/net/TrafficStats#getUidTxBytes(int)
         * @return 接收流量 bytes 长度, 上传流量 bytes 长度
         */
        private fun getAllAppsTraffics(): Pair<Long, Long> {
            return TrafficStats.getTotalRxBytes() to TrafficStats.getTotalTxBytes()
        }

        private fun loopTrafficsForAll() {
            var f0 = getAllAppsTraffics()
            var tmp: Pair<Long, Long>
            while (!isInterrupted) {
                tmp = getAllAppsTraffics()
                callback.onData(tmp.first - f0.first, tmp.second - f0.second)
                f0 = tmp
                sleep(intervalSecond * 1000L)
            }
        }

        override fun run() {
            try {
                loopTrafficsForAll()
            } catch (e: InterruptedException) {
                Log.w(name, "Thread is Interrupted!")
            } finally {
                Log.i(name, "Loop end!")
            }
        }
    }
}