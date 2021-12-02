package io.github.nic562.screen.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecordNotificationReceiver : BroadcastReceiver() {
    companion object {
        fun makeCallUpAppAndStopRecordIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra("stopRecording", true)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.apply {
            startActivity(makeCallUpAppAndStopRecordIntent(this))
        }
    }
}