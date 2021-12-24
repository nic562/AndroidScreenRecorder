package io.github.nic562.screen.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes

class RemoteCallingReceiver : BroadcastReceiver(), SomethingWithNetTrafficStatistics {
    private val tag = this.javaClass.simpleName
    private lateinit var ctx: Context
    override fun onReceive(context: Context, intent: Intent?) {
        ctx = context
        Log.i(tag, "Broadcast receive action: ${intent?.action} ---> ${intent?.extras}")
        when (intent?.action) {
            getStr(R.string.broadcast_receiver_action_remote_calling) -> {
                when (intent.getStringExtra("action")) {
                    "startNetTrafficStatistics" -> {
                        val app = intent.getStringExtra("app")
                        val bu = intent.extras?.apply {
                            remove("action")
                            remove("app")
                        }
                        if (app == null || app.isBlank()) {
                            startStatisticsService(null, bu)
                        } else {
                            startStatisticsService(arrayListOf(app), bu)
                        }
                    }
                    "stopNetTrafficStatistics" -> {
                        stopStatisticsService()
                    }
                }
            }
        }
    }

    override fun startActivityForResult(intent: Intent, reqCode: Int) {
        ctx.startActivity(intent)
    }

    override fun getContext(): Context {
        return ctx
    }

    private fun getStr(@StringRes id: Int): String {
        return ctx.getString(id)
    }
}