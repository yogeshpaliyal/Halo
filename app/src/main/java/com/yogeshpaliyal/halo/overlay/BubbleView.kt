package com.yogeshpaliyal.halo.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * The small floating circle. It only interprets the gesture and reports it, in screen
 * coordinates, to [listener]:
 *
 * - tap: jump back to the previous app
 * - drag: move the bubble (it snaps to the nearest side when released)
 * - long-press, then slide and release: pick an app from the radial menu
 *
 * Android keeps sending the whole gesture to the window that received ACTION_DOWN, so
 * the finger can travel far outside this small window and we still get every move.
 */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onTap()
        fun onDragMove(rawX: Float, rawY: Float)
        fun onDragEnd(rawX: Float, rawY: Float)
        fun onMenuOpen(rawX: Float, rawY: Float)
        fun onMenuMove(rawX: Float, rawY: Float)
        fun onMenuRelease(rawX: Float, rawY: Float)
        fun onMenuCancel()
    }

    private enum class State { IDLE, PRESSED, DRAGGING, MENU }

    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressMs = 280L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFB9A8FF")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val longPress = Runnable {
        if (state == State.PRESSED) {
            state = State.MENU
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate().scaleX(0.85f).scaleY(0.85f).setDuration(120).start()
            listener.onMenuOpen(lastX, lastY)
        }
    }

    init {
        alpha = IDLE_ALPHA
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = minOf(w, h) / 2f
        fill.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.parseColor("#FF3A3358"), Color.parseColor("#FF14121F")),
            null, Shader.TileMode.CLAMP,
        )
        ring.strokeWidth = r * 0.12f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        canvas.drawCircle(cx, cy, r * 0.94f, fill)
        canvas.drawCircle(cx, cy, r * 0.58f, ring)
        canvas.drawCircle(cx, cy, r * 0.18f, dot)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastX = event.rawX
        lastY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state = State.PRESSED
                downX = event.rawX
                downY = event.rawY
                alpha = 1f
                postDelayed(longPress, longPressMs)
            }

            MotionEvent.ACTION_MOVE -> when (state) {
                State.PRESSED -> if (hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                    removeCallbacks(longPress)
                    state = State.DRAGGING
                    listener.onDragMove(event.rawX, event.rawY)
                }
                State.DRAGGING -> listener.onDragMove(event.rawX, event.rawY)
                State.MENU -> listener.onMenuMove(event.rawX, event.rawY)
                State.IDLE -> Unit
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                when (state) {
                    State.PRESSED -> {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        listener.onTap()
                    }
                    State.DRAGGING -> listener.onDragEnd(event.rawX, event.rawY)
                    State.MENU -> listener.onMenuRelease(event.rawX, event.rawY)
                    State.IDLE -> Unit
                }
                reset()
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                when (state) {
                    State.DRAGGING -> listener.onDragEnd(event.rawX, event.rawY)
                    State.MENU -> listener.onMenuCancel()
                    else -> Unit
                }
                reset()
            }
        }
        return true
    }

    /** Light tick each time the finger moves onto a different app in the menu. */
    fun tick() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun reset() {
        state = State.IDLE
        animate().scaleX(1f).scaleY(1f).alpha(IDLE_ALPHA).setDuration(160).start()
    }

    private companion object {
        const val IDLE_ALPHA = 0.8f
    }
}
