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
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.data.TapAction
import kotlin.math.hypot

/**
 * The small floating circle. It only interprets the gesture and reports it, in screen
 * coordinates, to [listener]:
 *
 * - tap: whatever the user picked in settings (previous app by default)
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
        fun onDoubleTap()
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

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFB9A8FF")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF5A5F") }
    private val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF14121F") }

    /** Small dot when a favourite has notifications waiting. */
    var badge = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val longPress = Runnable {
        if (state == State.PRESSED) {
            state = State.MENU
            haptic(HapticFeedbackConstants.LONG_PRESS)
            animate().scaleX(0.85f).scaleY(0.85f).setDuration(120).start()
            listener.onMenuOpen(lastX, lastY)
        }
    }

    init {
        alpha = Prefs.bubbleOpacity(context)
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
        if (badge) {
            val bx = cx + r * 0.62f
            val by = cy - r * 0.62f
            canvas.drawCircle(bx, by, r * 0.26f, badgeRim)
            canvas.drawCircle(bx, by, r * 0.19f, badgePaint)
        }
    }

    /** A first tap waiting to see if a second one follows. */
    private val singleTap = Runnable {
        tapPending = false
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        listener.onTap()
    }
    private var tapPending = false
    /** This touch began while a first tap was waiting, so lifting it makes a double tap. */
    private var secondTap = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastX = event.rawX
        lastY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                secondTap = tapPending
                if (tapPending) {
                    removeCallbacks(singleTap)
                    tapPending = false
                }
                state = State.PRESSED
                downX = event.rawX
                downY = event.rawY
                alpha = 1f
                // Read on every touch so changes in the app apply immediately.
                postDelayed(longPress, Prefs.holdDelay(context).millis)
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
                    State.PRESSED -> when {
                        secondTap -> {
                            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            listener.onDoubleTap()
                        }
                        // Only wait for a possible second tap when double tap does something,
                        // so single taps stay instant otherwise.
                        Prefs.doubleTapAction(context) != TapAction.NOTHING -> {
                            tapPending = true
                            postDelayed(singleTap, DOUBLE_TAP_MS)
                        }
                        else -> {
                            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            listener.onTap()
                        }
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
    fun tick() = haptic(HapticFeedbackConstants.CLOCK_TICK)

    private fun haptic(type: Int) {
        if (Prefs.haptics(context)) performHapticFeedback(type)
    }

    private var hidden = false

    /** Re-applies the resting opacity after the user changes it (skipped while hidden or held). */
    fun refreshOpacity() {
        if (hidden || state != State.IDLE) return
        animate().cancel()
        alpha = Prefs.bubbleOpacity(context)
    }

    /** Fades the bubble out while another app is full screen, and back in afterwards. */
    fun setHidden(hidden: Boolean) {
        removeCallbacks(longPress)
        removeCallbacks(singleTap)
        tapPending = false
        state = State.IDLE
        animate().cancel()
        this.hidden = hidden
        animate().alpha(if (hidden) 0f else Prefs.bubbleOpacity(context)).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun reset() {
        state = State.IDLE
        animate().scaleX(1f).scaleY(1f).alpha(Prefs.bubbleOpacity(context)).setDuration(160).start()
    }

    private companion object {
        /** Gap allowed between the two taps of a double tap. */
        const val DOUBLE_TAP_MS = 250L
    }
}
