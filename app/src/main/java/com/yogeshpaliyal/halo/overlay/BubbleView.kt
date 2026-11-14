package com.yogeshpaliyal.halo.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.yogeshpaliyal.halo.data.CustomAvatar
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.data.TapAction
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The small floating circle. It only interprets the gesture and reports it, in screen
 * coordinates, to [listener]:
 *
 * - tap / double tap: whatever the user picked in settings
 * - drag: move the bubble (it snaps to the nearest side when released)
 * - long-press, then slide and release: pick an app from the radial menu
 *
 * Android keeps sending the whole gesture to the window that received ACTION_DOWN, so
 * the finger can travel far outside this small window and we still get every move.
 *
 * It's also a little character: it squishes when pressed, shows a ring filling up while
 * you hold, hops on tap, spins on double tap, pops when the dial opens, leans while
 * dragged, wobbles when it lands and shakes its head on cancel. The art is drawn at
 * [ART_SCALE] of the window so these moves have room without being clipped.
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
    private var prevX = 0f
    private var prevY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val renderer = BubbleRenderer().apply {
        BubblePalette.refresh(context)
        palette = Prefs.bubblePalette(context)
        character = Prefs.bubbleCharacter(context)
        customImage = CustomAvatar.load(context)
        accessory = Prefs.bubbleAccessory(context)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF5A5F") }
    private val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF14121F") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = android.graphics.RectF()

    /** Small dot when a favourite has notifications waiting. A new one makes it hop. */
    var badge = false
        set(value) {
            if (field != value) {
                val arrived = value && !field
                field = value
                invalidate()
                if (arrived && reactionsOn() && state == State.IDLE && !hidden) celebrate()
            }
        }

    // --- Motion: everything the interaction animations move ---------------------------------

    private var scaleXf = 1f
    private var scaleYf = 1f
    /** Lean while dragging, in degrees. */
    private var tilt = 0f
    /** Double-tap spin, in degrees. */
    private var spin = 0f
    /** Offsets as a fraction of the radius (hop, head shake). */
    private var offX = 0f
    private var offY = 0f
    /** Hold progress 0..1 shown as a ring; [ringBurst] 0..1 when it completes. */
    private var ring = 0f
    private var ringBurst = -1f

    private var motion: ValueAnimator? = null
    private var holdAnim: ValueAnimator? = null

    private val longPress = Runnable {
        if (state == State.PRESSED) {
            state = State.MENU
            setMood(BubbleRenderer.Mood.HAPPY)
            haptic(HapticFeedbackConstants.LONG_PRESS)
            popOpen()
            listener.onMenuOpen(lastX, lastY)
        }
    }

    init {
        alpha = Prefs.bubbleOpacity(context)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        val art = r * ART_SCALE

        // Hold ring around the bubble, then a quick burst as the dial opens.
        if (ring > 0f || ringBurst >= 0f) {
            ringPaint.color = ringColor()
            ringPaint.strokeWidth = r * 0.07f
            if (ringBurst >= 0f) {
                val rr = r * 0.9f * (1f + 0.1f * ringBurst)
                ringPaint.alpha = ((1f - ringBurst) * 255).toInt()
                canvas.drawCircle(cx, cy, rr, ringPaint)
            } else {
                val rr = r * 0.9f
                arc.set(cx - rr, cy - rr, cx + rr, cy + rr)
                ringPaint.alpha = 255
                canvas.drawArc(arc, -90f, 360f * ring, false, ringPaint)
            }
        }

        canvas.save()
        canvas.translate(offX * r, offY * r)
        canvas.rotate(tilt + spin, cx, cy)
        canvas.scale(scaleXf, scaleYf, cx, cy)
        renderer.draw(canvas, cx, cy, art)
        if (badge) {
            val bx = cx + art * 0.62f
            val by = cy - art * 0.62f
            canvas.drawCircle(bx, by, art * 0.26f, badgeRim)
            canvas.drawCircle(bx, by, art * 0.19f, badgePaint)
        }
        canvas.restore()
    }

    private fun ringColor(): Int = when (renderer.palette) {
        BubblePalette.MIDNIGHT -> renderer.palette.accent
        BubblePalette.MONO -> 0xFF8A8A8A.toInt()
        else -> renderer.palette.bottom
    }

    /** A first tap waiting to see if a second one follows. */
    private val singleTap = Runnable {
        tapPending = false
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        hop()
        listener.onTap()
    }
    private var tapPending = false
    /** This touch began while a first tap was waiting, so lifting it makes a double tap. */
    private var secondTap = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        prevX = lastX
        prevY = lastY
        lastX = event.rawX
        lastY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                setMood(BubbleRenderer.Mood.PRESSED)
                secondTap = tapPending
                if (tapPending) {
                    removeCallbacks(singleTap)
                    tapPending = false
                }
                state = State.PRESSED
                downX = event.rawX
                downY = event.rawY
                alpha = 1f
                squish()
                // Read on every touch so changes in the app apply immediately.
                val delay = Prefs.holdDelay(context).millis
                startHoldRing(delay)
                postDelayed(longPress, delay)
            }

            MotionEvent.ACTION_MOVE -> when (state) {
                State.PRESSED -> if (hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                    removeCallbacks(longPress)
                    stopHoldRing()
                    state = State.DRAGGING
                    setMood(BubbleRenderer.Mood.NORMAL)
                    motion?.cancel()
                    listener.onDragMove(event.rawX, event.rawY)
                }
                State.DRAGGING -> {
                    // Eyes follow the way you're dragging and the body leans into it.
                    val dx = event.rawX - prevX
                    lookAt(dx, event.rawY - prevY)
                    tilt = tilt * 0.7f + (dx * 0.9f).coerceIn(-16f, 16f) * 0.3f
                    scaleXf = 1.04f
                    scaleYf = 0.97f
                    invalidate()
                    listener.onDragMove(event.rawX, event.rawY)
                }
                State.MENU -> listener.onMenuMove(event.rawX, event.rawY)
                State.IDLE -> Unit
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                stopHoldRing()
                when (state) {
                    State.PRESSED -> when {
                        secondTap -> {
                            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            spinAround()
                            listener.onDoubleTap()
                        }
                        // Only wait for a possible second tap when double tap does something,
                        // so single taps stay instant otherwise.
                        Prefs.doubleTapAction(context) != TapAction.NOTHING -> {
                            tapPending = true
                            settle()
                            postDelayed(singleTap, DOUBLE_TAP_MS)
                        }
                        else -> {
                            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            hop()
                            listener.onTap()
                        }
                    }
                    State.DRAGGING -> {
                        listener.onDragEnd(event.rawX, event.rawY)
                        land()
                    }
                    State.MENU -> {
                        listener.onMenuRelease(event.rawX, event.rawY)
                        settle()
                    }
                    State.IDLE -> Unit
                }
                reset()
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                stopHoldRing()
                when (state) {
                    State.DRAGGING -> {
                        listener.onDragEnd(event.rawX, event.rawY)
                        land()
                    }
                    State.MENU -> {
                        listener.onMenuCancel()
                        shakeHead()
                    }
                    else -> settle()
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
        stopHoldRing()
        motion?.cancel()
        clearMotion()
        animate().cancel()
        this.hidden = hidden
        if (hidden) stopIdle() else scheduleIdle()
        animate().alpha(if (hidden) 0f else Prefs.bubbleOpacity(context)).setDuration(180).start()
    }

    private fun reset() {
        state = State.IDLE
        // Touched at night: stays awake for a moment before dozing off again.
        awakeUntil = android.os.SystemClock.uptimeMillis() + AWAKE_AFTER_TOUCH_MS
        renderer.sleepy = false
        setMood(BubbleRenderer.Mood.NORMAL)
        renderer.lookX = 0f
        renderer.lookY = 0f
        invalidate()
        animate().alpha(Prefs.bubbleOpacity(context)).setDuration(160).start()
    }

    // --- Interaction animations ------------------------------------------------------------

    /** Springy decay used for bounces: starts at 1, wobbles, ends at 0. */
    private fun spring(t: Float, wobbles: Float = 1.5f): Float =
        exp(-5f * t) * cos(wobbles * 2f * PI.toFloat() * t)

    private fun runMotion(duration: Long, update: (Float) -> Unit, end: () -> Unit = {}) {
        motion?.cancel()
        motion = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener {
                update(it.animatedValue as Float)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        clearMotion()
                        end()
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun clearMotion() {
        scaleXf = 1f
        scaleYf = 1f
        tilt = 0f
        spin = 0f
        offX = 0f
        offY = 0f
    }

    /** Finger down: squash a little, wider and shorter. */
    private fun squish() {
        val fromX = scaleXf
        val fromY = scaleYf
        runMotion(110, { t ->
            val e = 1f - (1f - t) * (1f - t)
            scaleXf = fromX + (1.08f - fromX) * e
            scaleYf = fromY + (0.9f - fromY) * e
        }, end = {
            // Stay squished while the finger is still down.
            if (state == State.PRESSED) {
                scaleXf = 1.08f
                scaleYf = 0.9f
            }
        })
    }

    /** Bounce back to normal from wherever it is. */
    private fun settle() {
        val fromX = scaleXf - 1f
        val fromY = scaleYf - 1f
        val fromTilt = tilt
        runMotion(420, { t ->
            val s = spring(t)
            scaleXf = 1f + fromX * s
            scaleYf = 1f + fromY * s
            tilt = fromTilt * s
        })
    }

    /** Tap: a little hop, stretching up as it leaves the ground. */
    private fun hop() {
        runMotion(320, { t ->
            val h = sin(PI.toFloat() * t)
            offY = -0.11f * h
            scaleXf = 1f - 0.06f * h
            scaleYf = 1f + 0.08f * h
        })
    }

    /** Double tap: a happy spin with a small hop. */
    private fun spinAround() {
        setMood(BubbleRenderer.Mood.HAPPY)
        runMotion(520, { t ->
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            spin = 360f * e
            offY = -0.08f * sin(PI.toFloat() * t)
            scaleXf = 1f
            scaleYf = 1f
        }, end = { if (state == State.IDLE) setMood(BubbleRenderer.Mood.NORMAL) })
    }

    /** Dial opens: pop up, then sink a little while you pick. */
    private fun popOpen() {
        val fromX = scaleXf
        val fromY = scaleYf
        holdAnim?.cancel()
        ring = 0f
        ringBurst = 0f
        runMotion(280, { t ->
            ringBurst = t
            val s = if (t < 0.4f) {
                val k = t / 0.4f
                1f + 0.14f * k
            } else {
                val k = (t - 0.4f) / 0.6f
                1.14f - 0.24f * (1f - (1f - k) * (1f - k))
            }
            scaleXf = fromX + (s - fromX) * (t * 2f).coerceAtMost(1f)
            scaleYf = fromY + (s - fromY) * (t * 2f).coerceAtMost(1f)
        }, end = {
            ringBurst = -1f
            if (state == State.MENU) {
                scaleXf = 0.9f
                scaleYf = 0.9f
            }
        })
    }

    /** After a drag: lean and stretch spring back once it has snapped to the edge. */
    private fun land() {
        val fromTilt = tilt
        runMotion(560, { t ->
            // The service slides it to the edge in 200 ms; the wobble lands with it.
            val s = spring(t, wobbles = 2f)
            tilt = fromTilt * s
            val squash = if (t < 0.3f) 0f else 0.1f * spring((t - 0.3f) / 0.7f, wobbles = 1.5f)
            scaleXf = 1f + squash
            scaleYf = 1f - squash
        })
    }

    /** Let go without picking: a quick "nope" shake. */
    private fun shakeHead() {
        runMotion(460, { t ->
            offX = 0.09f * sin(4f * 2f * PI.toFloat() * t) * (1f - t)
            val s = spring(t)
            scaleXf = 1f - 0.1f * s
            scaleYf = 1f - 0.1f * s
        })
    }

    private fun startHoldRing(delay: Long) {
        holdAnim?.cancel()
        ringBurst = -1f
        holdAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = delay
            // Don't flash a ring on quick taps: it only shows after the first stretch.
            startDelay = (delay / 3).coerceAtMost(120)
            addUpdateListener {
                ring = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopHoldRing() {
        holdAnim?.cancel()
        holdAnim = null
        if (ring != 0f) {
            ring = 0f
            invalidate()
        }
    }

    // --- Companion personality --------------------------------------------------------------

    /** Applies a new look from settings. */
    fun refreshStyle() {
        BubblePalette.refresh(context)
        renderer.palette = Prefs.bubblePalette(context)
        renderer.character = Prefs.bubbleCharacter(context)
        renderer.customImage = CustomAvatar.load(context)
        renderer.accessory = Prefs.bubbleAccessory(context)
        updateDay()
        stopIdle()
        scheduleIdle()
        invalidate()
    }

    private fun setMood(mood: BubbleRenderer.Mood) {
        if (renderer.mood != mood) {
            renderer.mood = mood
            invalidate()
        }
    }

    private fun lookAt(dx: Float, dy: Float) {
        val len = hypot(dx, dy)
        if (len < 1f) return
        renderer.lookX = renderer.lookX * 0.6f + (dx / len) * 0.4f
        renderer.lookY = renderer.lookY * 0.6f + (dy / len) * 0.4f
        invalidate()
    }

    private var idleAnim: ValueAnimator? = null
    private val random = java.util.Random()
    private val power = context.getSystemService(PowerManager::class.java)

    /** Every few seconds the character does one small thing; between them nothing is redrawn. */
    private val idleMoment = Runnable {
        // Skip while the screen is off; the timer itself never wakes a sleeping phone.
        val screenOn = power?.isInteractive != false
        if (screenOn) {
            updateDay()
            // Wallpaper colours may have changed since the last look.
            if (renderer.palette == BubblePalette.WALLPAPER) {
                val before = BubblePalette.WALLPAPER.top
                BubblePalette.refresh(context)
                if (BubblePalette.WALLPAPER.top != before) invalidate()
            }
        }
        if (screenOn && state == State.IDLE && !hidden && renderer.animated && motion?.isRunning != true) playIdle()
        scheduleIdle()
    }

    // --- Reactions to your day --------------------------------------------------------------

    private fun reactionsOn() = renderer.animated && Prefs.bubbleReactions(context)

    /** Stays awake for a bit after you touch it, even at night. */
    private var awakeUntil = 0L

    /** Re-reads the clock and battery; cheap, done at idle moments and on battery changes. */
    private fun updateDay() {
        val on = reactionsOn()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val night = hour >= 23 || hour < 6
        val sleepy = on && night && android.os.SystemClock.uptimeMillis() > awakeUntil
        val low = on && batteryLow && !batteryCharging
        val plugged = on && batteryCharging
        if (sleepy != renderer.sleepy || low != renderer.lowBattery || plugged != renderer.charging) {
            renderer.sleepy = sleepy
            renderer.lowBattery = low
            renderer.charging = plugged
            invalidate()
        }
    }

    private var batteryLow = false
    private var batteryCharging = false
    private var wasAttachedLongEnough = false

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            batteryLow = level >= 0 && scale > 0 && level * 100 / scale < LOW_BATTERY_PERCENT
            val wasCharging = batteryCharging
            batteryCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            updateDay()
            // Plugging in gets a little celebration.
            if (batteryCharging && !wasCharging && wasAttachedLongEnough && state == State.IDLE && !hidden && reactionsOn()) {
                celebrate()
            }
        }
    }

    /** A happy double hop: new notification, or plugged in. */
    private fun celebrate() {
        setMood(BubbleRenderer.Mood.HAPPY)
        runMotion(620, { t ->
            val h = abs(sin(2f * PI.toFloat() * t))
            offY = -0.09f * h * (1f - t * 0.4f)
            scaleXf = 1f - 0.04f * h
            scaleYf = 1f + 0.06f * h
        }, end = { if (state == State.IDLE) setMood(BubbleRenderer.Mood.NORMAL) })
    }

    /** Asleep, sweating or charging: those get their own idle moments. */
    private fun playDayMoment(): Boolean {
        val r = renderer
        if (!r.sleepy && !r.lowBattery && !r.charging) return false
        idleAnim?.cancel()
        idleAnim = idleTween(if (r.sleepy) 1600L else 900L) { t -> r.extraPhase = t }
        return true
    }

    private fun scheduleIdle() {
        removeCallbacks(idleMoment)
        if (!isAttachedToWindow || hidden || !renderer.animated || !Prefs.bubbleAnimations(context)) return
        postDelayed(idleMoment, 2500L + random.nextInt(4000))
    }

    private fun stopIdle() {
        removeCallbacks(idleMoment)
        idleAnim?.cancel()
        renderer.blink = 0f
        renderer.wiggle = 0f
    }

    private fun playIdle() {
        // Mostly the day reaction when there is one (asleep characters don't look around).
        if ((renderer.sleepy || random.nextInt(3) == 0) && playDayMoment()) return
        idleAnim?.cancel()
        val roll = random.nextInt(10)
        // A photo has no eyes to blink or move: it just rocks now and then.
        val photo = renderer.character == BubbleCharacter.CUSTOM
        idleAnim = when {
            photo -> idleTween(700L) { t -> renderer.wiggle = sin(t * PI.toFloat() * 3f) * (1f - t) }
            // Mostly blinks, sometimes twice.
            roll < 5 -> idleTween(if (roll == 0) 420L else 180L) { t ->
                val phase = if (roll == 0) (t * 2f) % 1f else t
                renderer.blink = 1f - abs(phase * 2f - 1f)
            }
            // A look to one side and back.
            roll < 8 -> {
                val side = if (random.nextBoolean()) 1f else -1f
                val up = (random.nextFloat() - 0.5f) * 0.6f
                idleTween(1100L) { t ->
                    val k = when {
                        t < 0.2f -> t / 0.2f
                        t > 0.8f -> (1f - t) / 0.2f
                        else -> 1f
                    }
                    renderer.lookX = side * 0.7f * k
                    renderer.lookY = up * k
                }
            }
            // Character move: ear flick, leaf sway, ghost bob, star tilt, antenna blink.
            else -> idleTween(700L) { t ->
                renderer.wiggle = sin(t * PI.toFloat() * 3f) * (1f - t)
            }
        }
    }

    private fun idleTween(duration: Long, update: (Float) -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener {
                update(it.animatedValue as Float)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    renderer.blink = 0f
                    renderer.wiggle = 0f
                    renderer.extraPhase = 0f
                    if (state == State.IDLE) {
                        renderer.lookX = 0f
                        renderer.lookY = 0f
                    }
                    invalidate()
                }
            })
            start()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Sticky broadcast: also delivers the current battery state right away.
        androidx.core.content.ContextCompat.registerReceiver(
            context, batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        postDelayed({ wasAttachedLongEnough = true }, 1500)
        updateDay()
        scheduleIdle()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        wasAttachedLongEnough = false
        stopIdle()
        motion?.cancel()
        holdAnim?.cancel()
        removeCallbacks(singleTap)
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    companion object {
        /** Gap allowed between the two taps of a double tap. */
        private const val DOUBLE_TAP_MS = 250L
        private const val LOW_BATTERY_PERCENT = 15
        private const val AWAKE_AFTER_TOUCH_MS = 8000L

        /**
         * The character fills this share of the window; the rest is room for hops, pops
         * and the hold ring. The window is enlarged to match, so the bubble looks the
         * same size as the setting says.
         */
        const val ART_SCALE = 0.84f
    }
}
