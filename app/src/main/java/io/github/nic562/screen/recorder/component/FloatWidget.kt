package io.github.nic562.screen.recorder.component

import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.nic562.screen.recorder.R

/**
 * Created by Nic on 2020/1/19.
 */
class FloatWidget(context: Context) : LinearLayout(context), FloatAble, PositionMovable {
    override val windowManagerLayoutParams: WindowManager.LayoutParams =
        WindowManager.LayoutParams()
    override val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    override val floatAbleView: View = this

    override val movable: PositionMovable.Movable = PositionMovable.Movable(context)

    private var canMove = true

    private var onAfterMoveListener: OnMovingEventListener? = null
    private var onBeforeMoveListener: OnMovingEventListener? = null

    private val ivRecord: ImageView by lazy {
        findViewById(R.id.iv_record)
    }

    private val tvRecord: TextView by lazy {
        findViewById(R.id.tv_record)
    }

    private val ivClose: ImageView by lazy {
        findViewById(R.id.iv_close)
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_float_widget, this)
        val root = view.findViewById<View>(R.id.root)
        initWindowManagerLayoutParams(
            root.layoutParams.width,
            root.layoutParams.height
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (canMove) {
            dispatchTouch(ev) ?: super.dispatchTouchEvent(ev)
        } else {
            super.dispatchTouchEvent(ev)
        }
    }

    override fun onMoving(distanceX: Float, distanceY: Float) {
        windowManagerLayoutParams.x = (windowManagerLayoutParams.x + distanceX).toInt()
        windowManagerLayoutParams.y = (windowManagerLayoutParams.y + distanceY).toInt()
        updateWindowManagerLayout()
    }

    override fun beforeMoving() {
        onBeforeMoveListener?.onEvent()
    }

    override fun afterMoving() {
        onAfterMoveListener?.onEvent()
    }

    fun disableMove() {
        canMove = false
    }

    fun enableMove() {
        canMove = true
    }

    fun setOnRecordClickListener(onClickListener: OnClickListener) {
        ivRecord.setOnClickListener(onClickListener)
    }

    fun setOnCloseClickListener(onClickListener: OnClickListener) {
        ivClose.setOnClickListener(onClickListener)
    }

    fun setOnBeforeMoveListener(listener: OnMovingEventListener?) {
        onBeforeMoveListener = listener
    }

    fun setOnAfterMoveListener(listener: OnMovingEventListener?) {
        onAfterMoveListener = listener
    }

    fun setRecordColor(color: Int) {
        ivRecord.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun setText(s: String) {
        tvRecord.text = s
    }

    interface OnMovingEventListener {
        fun onEvent()
    }
}