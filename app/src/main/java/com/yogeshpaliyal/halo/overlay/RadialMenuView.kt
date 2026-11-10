package com.yogeshpaliyal.halo.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.Prefs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen overlay that draws two half-rings of app icons around the bubble:
 * favourites on the inner ring, recent apps on the outer ring.
 *
 * Two modes:
 * - Hold mode (default): the window is non-touchable; the bubble owns the gesture and
 *   forwards finger positions through [updateFinger].
 * - Tap mode: set [onPick] and make the window touchable. The next touch is handled
 *   here; lifting on an app picks it, lifting anywhere else dismisses (picks null).
 *
 * Angles use the Canvas convention: 0° points right and angles grow clockwise (y is down).
 */
@SuppressLint("ViewConstructor")
class RadialMenuView(
    context: Context,
    private val anchorScreenX: Float,
    private val anchorScreenY: Float,
    private val onRightEdge: Boolean,
    favorites: List<AppEntry>,
    recents: List<AppEntry>,
    /** Keys of apps that have notifications waiting; each gets a small dot. */
    private val badged: Set<String> = emptySet(),
) : View(context) {

    private class Ring(val items: List<AppEntry>, val radius: Float)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val iconSize = dp(ICON_DP)
    private val deadZone = dp(52f)
    private val span = 160f
    /** Direction the half-rings open towards: left when docked right, right when docked left. */
    private val facing = if (onRightEdge) 180f else 0f
    /** +1 / -1 so that slot 0 is always the top item, whichever side the bubble is on. */
    private val dir = if (onRightEdge) -1f else 1f

    private val rings: List<Ring> = buildList {
        if (favorites.isNotEmpty()) add(Ring(favorites, dp(INNER_DP)))
        if (recents.isNotEmpty()) add(Ring(recents, if (favorites.isEmpty()) dp(INNER_DP) else dp(OUTER_DP)))
    }

    /** Currently highlighted app, or null if the finger is in the cancel zone. */
    var selected: AppEntry? = null
        private set
    private var selRing = -1
    private var selSlot = -1

    /** Non-null in tap mode. Called once with the chosen app, or null to dismiss. */
    var onPick: ((AppEntry?) -> Unit)? = null

    private var progress = 0f
    private val screenLoc = IntArray(2)
    private var fingerX = anchorScreenX
    private var fingerY = anchorScreenY

    private val scrim = Paint().apply { color = Color.BLACK }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = iconSize + dp(18f)
        color = Color.parseColor("#E61E1B2E")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF5A5F") }
    private val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59B9A8FF") }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2F1EDFF") }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF14121F")
        textSize = dp(14f)
        isFakeBoldText = true
    }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()
    private val pillRect = RectF()

    fun show() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Returns true when the highlighted app changed (used for a haptic tick). */
    fun updateFinger(rawX: Float, rawY: Float): Boolean {
        fingerX = rawX
        fingerY = rawY
        val (ring, slot) = hitTest(rawX, rawY)
        val changed = ring != selRing || slot != selSlot
        selRing = ring
        selSlot = slot
        selected = if (ring >= 0) rings[ring].items[slot] else null
        if (changed) invalidate()
        return changed && selected != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pick = onPick ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                if (updateFinger(event.rawX, event.rawY) && Prefs.haptics(context)) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            MotionEvent.ACTION_UP -> {
                updateFinger(event.rawX, event.rawY)
                onPick = null
                pick(selected)
            }
            MotionEvent.ACTION_CANCEL -> {
                onPick = null
                pick(null)
            }
        }
        return true
    }

    // --- geometry -------------------------------------------------------------------------

    private fun centerX(): Float = anchorScreenX - screenLoc[0]

    /** Keep the whole menu on screen when the bubble sits near the top or bottom. */
    private fun centerY(): Float {
        val outer = (rings.maxOfOrNull { it.radius } ?: 0f) + iconSize
        val y = anchorScreenY - screenLoc[1]
        return if (height <= 2 * outer) height / 2f else y.coerceIn(outer, height - outer)
    }

    private fun slotAngle(slot: Int, count: Int): Float {
        val step = span / count
        return facing + dir * (-span / 2f + (slot + 0.5f) * step)
    }

    private fun hitTest(rawX: Float, rawY: Float): Pair<Int, Int> {
        if (rings.isEmpty()) return -1 to -1
        getLocationOnScreen(screenLoc)
        val dx = rawX - screenLoc[0] - centerX()
        val dy = rawY - screenLoc[1] - centerY()
        val dist = hypot(dx, dy)
        if (dist < deadZone) return -1 to -1

        val ringIndex = if (rings.size == 1) 0 else {
            val boundary = (rings[0].radius + rings[1].radius) / 2f
            if (dist < boundary) 0 else 1
        }
        val ring = rings[ringIndex]

        var rel = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - facing
        while (rel < -180f) rel += 360f
        while (rel >= 180f) rel -= 360f
        if (kotlin.math.abs(rel) > span / 2f + 20f) return -1 to -1

        val step = span / ring.items.size
        val slot = floor((dir * rel + span / 2f) / step).toInt().coerceIn(0, ring.items.lastIndex)
        return ringIndex to slot
    }

    // --- drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(screenLoc)
        val p = progress.coerceIn(0f, 1.2f)
        val fade = progress.coerceIn(0f, 1f)
        scrim.alpha = (fade * 175).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)

        val cx = centerX()
        val cy = centerY()

        if (rings.isEmpty()) {
            hint.alpha = (fade * 255).toInt()
            canvas.drawText("Pick favourites in the Halo app", width / 2f, cy, hint)
            return
        }

        rings.forEach { ring ->
            val r = ring.radius * (0.55f + 0.45f * p)
            arcRect.set(cx - r, cy - r, cx + r, cy + r)
            band.alpha = (fade * 235).toInt()
            canvas.drawArc(arcRect, facing - span / 2f - 4f, span + 8f, false, band)
        }

        var selCenterX = 0f
        var selCenterY = 0f
        rings.forEachIndexed { ri, ring ->
            val r = ring.radius * (0.55f + 0.45f * p)
            ring.items.forEachIndexed { si, app ->
                val a = Math.toRadians(slotAngle(si, ring.items.size).toDouble())
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                val isSelected = ri == selRing && si == selSlot
                val size = iconSize * (if (isSelected) 1.3f else 1f) * fade
                if (isSelected) {
                    canvas.drawCircle(x, y, size * 0.72f, glow)
                    selCenterX = x
                    selCenterY = y
                }
                val half = (size / 2f).toInt()
                app.icon.setBounds(x.toInt() - half, y.toInt() - half, x.toInt() + half, y.toInt() + half)
                // Paused work apps are dimmed; opening one shows Android's "Unpause?" prompt.
                app.icon.alpha = (fade * if (app.available) 255 else 100).toInt()
                app.icon.draw(canvas)
                if (app.key in badged && fade > 0.5f) {
                    val bx = x + size * 0.36f
                    val by = y - size * 0.36f
                    canvas.drawCircle(bx, by, size * 0.15f, badgeRim)
                    canvas.drawCircle(bx, by, size * 0.11f, badgePaint)
                }
            }
        }

        selected?.let { drawLabel(canvas, it.label, selCenterX, selCenterY) }
    }

    /** App name in a pill just inside the highlighted icon, kept on screen. */
    private fun drawLabel(canvas: Canvas, text: String, iconX: Float, iconY: Float) {
        val pad = dp(10f)
        val name = TextUtils.ellipsize(text, label, width * 0.5f, TextUtils.TruncateAt.END).toString()
        val w = label.measureText(name) + pad * 2
        val h = label.textSize + pad * 1.4f
        val gap = iconSize * 0.75f + dp(8f)
        val left = if (onRightEdge) iconX - gap - w else iconX + gap
        val x = left.coerceIn(dp(8f), width - w - dp(8f))
        val y = iconY - h / 2f
        pillRect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(pillRect, h / 2f, h / 2f, pill)
        canvas.drawText(name, x + pad, y + h / 2f - (label.descent() + label.ascent()) / 2f, label)
    }

    companion object {
        private const val INNER_DP = 122f
        private const val OUTER_DP = 208f
        private const val ICON_DP = 46f

        /** How far the menu reaches above and below its centre, in px. */
        fun halfExtentPx(context: Context, hasFavorites: Boolean, hasRecents: Boolean): Float {
            val outer = when {
                hasFavorites && hasRecents -> OUTER_DP
                hasFavorites || hasRecents -> INNER_DP
                else -> 0f
            }
            return (outer + ICON_DP) * context.resources.displayMetrics.density
        }
    }
}
