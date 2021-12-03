package io.github.nic562.screen.recorder.component

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Created by Nic on 2019/12/15.
 */
interface FloatAble {

    companion object {
        const val DEFAULT_LAYOUT_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    val windowManagerLayoutParams: WindowManager.LayoutParams

    val windowManager: WindowManager

    val floatAbleView: View

    fun initWindowManagerLayoutParams(width: Int, height: Int) {
        windowManagerLayoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        windowManagerLayoutParams.format = PixelFormat.RGBA_8888
        windowManagerLayoutParams.flags = DEFAULT_LAYOUT_FLAGS
        windowManagerLayoutParams.gravity = Gravity.START or Gravity.TOP
        windowManagerLayoutParams.width = width
        windowManagerLayoutParams.height = height
    }

    fun disableTouch() {
        windowManagerLayoutParams.flags =
            DEFAULT_LAYOUT_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(floatAbleView, windowManagerLayoutParams)
    }

    fun enableTouch() {
        windowManagerLayoutParams.flags = DEFAULT_LAYOUT_FLAGS
        windowManager.updateViewLayout(floatAbleView, windowManagerLayoutParams)
    }

    fun updateWindowManagerLayout() {
        windowManager.updateViewLayout(floatAbleView, windowManagerLayoutParams)
    }
}