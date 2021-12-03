package io.github.nic562.screen.recorder.component

import android.app.Service
import android.content.Context
import android.os.Vibrator
import android.view.MotionEvent

/**
 * Created by Nic on 2019/12/12.
 */
interface PositionMovable {

    class Movable(context: Context) {
        var delay = 600 // 长按移动延迟, 毫秒
        var withVibrate = true // 触发移动时，震动反馈

        val vibrator: Vibrator =
            context.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator

        var downX = 0f
        var downY = 0f
        var downTime = 0L

        var isMoving = false
        var isLongClick = false
    }

    val movable: Movable

    /**
     * 长按触发可移动时间，毫秒
     */
    fun setMovingDelay(d: Int) {
        movable.delay = d
    }

    /**
     * 设置是否触发移动时的震动反馈
     */
    fun enableVibrate(v: Boolean) {
        movable.withVibrate = v
    }

    fun dispatchTouch(event: MotionEvent): Boolean? {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                movable.downTime = System.currentTimeMillis()
                movable.isLongClick = false
                movable.downX = event.rawX
                movable.downY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                if (movable.isMoving) {
                    onTouch(event)
                    return true
                }
                if (movable.downTime != 0L && System.currentTimeMillis() - movable.downTime >= movable.delay) {
                    movable.isLongClick = true
                    movable.downTime = 0L
                    onTouch(event)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (movable.isLongClick) {
                    onTouch(event)
                    return true
                }
            }
        }
        return null
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (movable.isMoving) {
                    val dX = event.rawX - movable.downX
                    val dY = event.rawY - movable.downY
                    movable.downX = event.rawX
                    movable.downY = event.rawY
                    onMoving(dX, dY)
                    return true
                } else if (movable.isLongClick) {
                    beforeMoving()
                    movable.isMoving = true
                    if (movable.withVibrate) {
                        movable.vibrator.vibrate(100)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                movable.isMoving = false
                afterMoving()
            }
        }
        return false
    }

    fun onMoving(distanceX: Float, distanceY: Float)

    fun beforeMoving() {}
    fun afterMoving() {}
}