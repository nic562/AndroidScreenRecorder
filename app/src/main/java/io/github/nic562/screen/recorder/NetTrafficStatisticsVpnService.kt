package io.github.nic562.screen.recorder

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nic562.screen.recorder.base.SomethingWithNotification
import io.github.nic562.screen.recorder.tools.bio.BioUdpHandler
import io.github.nic562.screen.recorder.tools.bio.NioSingleThreadTcpHandler
import io.github.nic562.screen.recorder.tools.protocol.Packet
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors

/**
 * 基于VPN网关转发的网络流量统计服务，可统计指定应用
 */
class NetTrafficStatisticsVpnService : VpnService(), SomethingWithNotification {
    companion object {
        /**
         * 判断是否VPN是否授权，如果是，返回 Null，
         * 否则返回Intent 对象请调用 startActivityForResult
         */
        fun getActivityIntentForPrepareVpn(ctx: Context): Intent? {
            return prepare(ctx)
        }
    }

    private val tag: String by lazy {
        this::class.java.simpleName
    }
    private val notificationID: Int = 99999
    override val notificationChannel = "NetTrafficStatisticsService"
    override val notificationManager: NotificationManagerCompat by lazy {
        initNotificationManager()
    }
    override val notificationBuilder: NotificationCompat.Builder by lazy {
        initNotificationBuilder()
    }

    private val broadcastIntent by lazy {
        Intent(getString(R.string.broadcast_receiver_action_net_traffic_statistics))
    }

    private val executorService by lazy {
        Executors.newFixedThreadPool(10)
    }

    private val deviceToNetworkUDPQueue: BlockingQueue<Packet> by lazy {
        ArrayBlockingQueue(1000)
    }

    private val deviceToNetworkTCPQueue: BlockingQueue<Packet> by lazy {
        ArrayBlockingQueue(1000)
    }

    private val networkToDeviceQueue: BlockingQueue<ByteBuffer> by lazy {
        ArrayBlockingQueue(1000)
    }

    private var tunnelThread: TunnelThread? = null
    private val handler by lazy {
        Handler(mainLooper)
    }
    private val updateNotificationRunnable = Runnable {
        tunnelThread?.apply {
            val downByteSize = this.getAndResetDownloadSize()
            val upByteSize = this.getAndResetUploadSize()
            sendNotification(
                "${
                    getString(
                        R.string.upload_speed,
                        upByteSize / 100.0
                    )
                }: ${
                    getString(
                        R.string.download_speed,
                        downByteSize / 100.0
                    )
                }"
            )
            sendBroadcast(broadcastIntent.apply {
                putExtra("action", "working")
                putExtra("downByteSize", downByteSize)
                putExtra("upByteSize", upByteSize)
            })
            updateNotification()
        }
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

    override fun getContext(): Context {
        return this
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        executorService.submit(BioUdpHandler(deviceToNetworkUDPQueue, networkToDeviceQueue, this))
        executorService.submit(
            NioSingleThreadTcpHandler(
                deviceToNetworkTCPQueue,
                networkToDeviceQueue,
                this
            )
        )
        sendBroadcast(broadcastIntent.putExtra("action", "create"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "start" -> {
                if (tunnelThread == null || tunnelThread?.isAlive == false) {
                    val apps = intent.getStringArrayListExtra("apps")
                    Log.w(tag, "Willing to listening with: [${apps?.joinToString(",")}]")
                    if (apps != null) {
                        startListener(*apps.toTypedArray())
                    } else {
                        startListener()
                    }
                }
            }
            "stop" -> {
                tunnelThread?.interrupt()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        tunnelThread?.apply {
            if (isAlive)
                this.interrupt()
            tunnelThread = null
        }
        executorService.shutdown()
        sendBroadcast(broadcastIntent.putExtra("action", "destroy"))
        cancelNotification(notificationID)
        super.onDestroy()
    }

    private fun startListener(vararg appPkgNames: String) {
        createTunnel(*appPkgNames)?.apply {
            tunnelThread = TunnelThread(
                this,
                deviceToNetworkUDPQueue,
                deviceToNetworkTCPQueue,
                networkToDeviceQueue
            ).apply {
                start()
            }
            handler.removeCallbacks(updateNotificationRunnable)
            updateNotification()
        }
    }

    private fun createTunnel(vararg appPkgNames: String): ParcelFileDescriptor? {
        val bd = Builder()
            .addAddress("10.0.1.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("114.114.114.114")
            .addDnsServer("8.8.8.8")
            .setSession(getString(R.string.net_traffic_statistics))
        for (x in appPkgNames) {
            bd.addAllowedApplication(x)
        }
        return bd.establish()
    }

    private fun updateNotification() {
        handler.postDelayed(updateNotificationRunnable, 1000)
    }

    private fun sendNotification(msg: String) {
        notificationBuilder
            .setContentText(msg).build().apply {
                notificationManager.notify(notificationID, this)
            }
    }

    private class TunnelOutputThread(
        private val outputChannel: FileChannel,
        private val networkToDeviceQueue: BlockingQueue<ByteBuffer>
    ) : Thread("TunnelOutputThread") {
        private var downloadSize = 0L  // 累计下载byte数

        fun getAndResetDownloadSize(): Long {
            val v = downloadSize
            downloadSize = 0
            return v
        }

        override fun run() {
            try {
                looping()
            } catch (e: InterruptedException) {
                Log.w(name, "Vpn isInterrupted!")
            } catch (e: ClosedByInterruptException) {
                Log.w(name, "Vpn isInterrupted!")
            } catch (e: Exception) {
                Log.e(name, "running error:", e)
            }
        }

        private fun looping() {
            var len: Int
            while (!isInterrupted) {
                val bf = networkToDeviceQueue.take()  // 这里会阻塞
                bf.flip()
                while (bf.hasRemaining()) {
                    len = outputChannel.write(bf)
                    if (len > 0) {
                        downloadSize += len
                    }
                }
            }
        }
    }

    /**
     * 套接字处理线程
     * 调用 interrupt 时会关闭传入的所有套接字
     */
    private class TunnelThread(
        private val tun: ParcelFileDescriptor,
        private val deviceToNetworkUdpQueue: BlockingQueue<Packet>,
        private val deviceToNetworkTcpQueue: BlockingQueue<Packet>,
        private val networkToDeviceQueue: BlockingQueue<ByteBuffer>
    ) : Thread("TunnelThread") {
        var idle: Boolean = true // 是否空闲
            private set
        private var uploadSize = 0L  // 累计上传byte数
        private val fd by lazy {
            tun.fileDescriptor
        }
        private val inputChannel: FileChannel by lazy {
            FileInputStream(fd).channel
        }
        private val outputChannel: FileChannel by lazy {
            FileOutputStream(fd).channel
        }
        private val outThread by lazy { TunnelOutputThread(outputChannel, networkToDeviceQueue) }

        override fun run() {
            try {
                outThread.start()
                looping()
            } catch (e: InterruptedException) {
                Log.w(name, "Vpn isInterrupted!")
            } catch (e: ClosedByInterruptException) {
                Log.w(name, "Vpn isInterrupted!")
            } catch (e: Exception) {
                Log.e(name, "running error:", e)
            } finally {
                outThread.interrupt()
                closeIt(inputChannel, outputChannel, tun)
            }
        }

        fun getAndResetUploadSize(): Long {
            val v = uploadSize
            uploadSize = 0L
            return v
        }

        fun getAndResetDownloadSize(): Long {
            return outThread.getAndResetDownloadSize()
        }

        private fun looping() {
            var lastIOTime = 0L
            var nextSleep = 0L
            var len: Int
            var buffer: ByteBuffer
            while (!isInterrupted) {
                idle = true
                buffer = ByteBuffer.allocate(Packet.DEF_BUFFER_SIZE)
                len = inputChannel.read(buffer)
                if (len > 0) {
                    uploadSize += len
                    buffer.flip()
                    val pk = Packet(buffer)
                    if (pk.isUDP) {
                        deviceToNetworkUdpQueue.offer(pk)
                    } else if (pk.isTCP) {
                        deviceToNetworkTcpQueue.offer(pk)
                    } else {
                        Log.w(name, "UnHandle packet protocol type: ${pk.ip4Header.protocolNum}")
                    }
                    idle = false
                    lastIOTime = System.currentTimeMillis()
                }

                if (idle) {
                    if (System.currentTimeMillis() - lastIOTime > 100) {
                        // 对比上一次IO时间，空闲超过100毫秒, 每次循环额外延迟休眠时间，最高500ms
                        if (nextSleep < 500) {
                            nextSleep += 50
                        }
                    } else {
                        // 不足的，则恢复休眠时间
                        nextSleep = 50
                    }
                    sleep(nextSleep)
                }
            }
        }

        private fun closeIt(vararg cc: Closeable) {
            for (c in cc) {
                try {
                    c.close()
                } catch (e: Exception) {
                    Log.w(name, "Close ${c.javaClass.simpleName} error:", e)
                }
            }
        }
    }
}