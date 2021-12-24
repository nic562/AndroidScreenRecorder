package io.github.nic562.screen.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.nic562.screen.recorder.base.SomethingWithContext
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.set

interface SomethingWithNetTrafficStatistics : SomethingWithContext {
    companion object {
        private val dataMap by lazy { HashMap<Int, Bundle>() }
    }

    fun getNetTrafficStatisticsVpnReqCode(): Int {
        return 9999
    }

    fun getNetTrafficStatisticsBroadcastAction(): String {
        return getContext().getString(R.string.broadcast_receiver_action_net_traffic_statistics)
    }

    fun createNetTrafficStatisticsBroadcastReceiver(actionName: String): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    actionName -> {
                        when (intent.getStringExtra("action")) {
                            "create" -> {
                                onNetStatisticsCreate()
                            }
                            "destroy" -> {
                                onNetStatisticsDestroy()
                            }
                            "working" -> {
                                onNetStatisticsWorking(
                                    intent.getStringExtra("network") ?: "",
                                    intent.getLongExtra("downByteSize", 0L),
                                    intent.getLongExtra("upByteSize", 0L)
                                )
                            }
                            "error" -> {
                                onNetStatisticsError(intent.getStringExtra("error") ?: "UnKnown error!")
                            }
                        }
                    }
                }
            }
        }
    }

    fun onNetStatisticsCreate() {}
    fun onNetStatisticsDestroy() {}
    fun onNetStatisticsWorking(network: String, downByteSize: Long, uploadByteSize: Long) {}
    fun onNetStatisticsError(error: String) {
        Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show()
    }

    fun startStatisticsService(apps: List<String>? = null, bundle: Bundle? = null) {
        val bu = Bundle().apply {
            putString("action", "start")
            if (bundle != null) {
                putAll(bundle)
            }
        }
        if (apps == null || apps.isEmpty()) {
            openGlobalStatisticsService(bu)
        } else {
            openVpnStatisticsService(apps, bu)
        }
    }

    fun stopStatisticsService() {
        NetTrafficStatisticsServiceHelper.sendBroadcastToStop(getContext())
    }

    fun stopStatisticsService(network: String, bundle: Bundle? = null) {
        val bu = Bundle().apply {
            putString("action", "stop")
            if (bundle != null) {
                putAll(bundle)
            }
        }
        when (network) {
            "vpn" -> {
                openVpnStatisticsService(null, bu)
            }
            else -> {
                openGlobalStatisticsService(bu)
            }
        }
    }

    fun openGlobalStatisticsService(bundle: Bundle? = null) {
        NetTrafficStatisticsService.startForegroundService(getContext(), bundle)
    }

    fun openVpnStatisticsService(apps: List<String>?, bundle: Bundle? = null) {
        NetTrafficStatisticsVpnService.getActivityIntentForPrepareVpn(getContext()).apply {
            val bu = Bundle()
            if (apps != null)
                bu.putStringArrayList("apps", ArrayList(apps))
            if (bundle != null)
                bu.putAll(bundle)
            when (this) {
                null -> {
                    openVpnStatisticsService(bu)
                }
                else -> {
                    val q = getNetTrafficStatisticsVpnReqCode()
                    dataMap[q] = bu
                    startActivityForResult(this, q)
                }
            }
        }
    }

    fun openVpnStatisticsService(bundle: Bundle) {
        getContext().startService(
            Intent(
                getContext(),
                NetTrafficStatisticsVpnService::class.java
            ).putExtras(bundle)
        )
    }

    /***
     * 请在 [AppCompatActivity.onActivityResult] 中调用
     * @return Boolean 是否消费掉事件
     */
    fun onNetTrafficStatisticsActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        when (requestCode) {
            getNetTrafficStatisticsVpnReqCode() -> {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    (data?.extras ?: dataMap[requestCode])?.apply {
                        openVpnStatisticsService(this)
                        return true
                    }
                }
            }
        }
        return false
    }

    fun startActivityForResult(intent: Intent, reqCode: Int)
}