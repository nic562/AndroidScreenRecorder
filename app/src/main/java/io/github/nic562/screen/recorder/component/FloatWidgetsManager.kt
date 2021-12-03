package io.github.nic562.screen.recorder.component

import android.content.Context
import android.graphics.Point
import android.util.Log
import android.view.WindowManager
import io.github.nic562.screen.recorder.Config

/**
 * Created by Nic on 2020/1/19.
 */
class FloatWidgetsManager(private val context: Context) {
    private val tag = this.javaClass.name

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    var mainView: FloatWidget? = null
    private set

    fun show(): Boolean {
        val po = Config.getWidgetPosition()
        try {
            initMainView(Point(po.first, po.second))
        } catch (e: Exception) {
            Log.e(tag, "show Float Widget Views error, maybe accessibility service is disable:", e)
            return false
        }
        return true
    }

    private fun hide() {
        if (mainView != null) {
            try {
                windowManager.removeView(mainView)
            } catch (e: Exception) {
                Log.e(tag, "remove Float Widget views from windowManager error: ", e)
            }
            mainView = null
        }
    }

    fun destroy() {
        hide()
    }

    private fun initMainView(po: Point?): FloatWidget? {
        if (mainView == null) {
            val mv = FloatWidget(context)
            val pp = mv.windowManagerLayoutParams
            if (po != null) {
                pp.x = po.x
                pp.y = po.y
            }
            windowManager.addView(mv, pp)
            mv.setOnAfterMoveListener(object : FloatWidget.OnMovingEventListener {
                override fun onEvent() {
                    val pm = mv.windowManagerLayoutParams
                    val x = if (pm.x > 0) pm.x else 0
                    val y = if (pm.y > 0) pm.y else 0
                    Config.setWidgetPosition(x, y)
                }
            })
            mainView = mv
        }
        return mainView
    }
}