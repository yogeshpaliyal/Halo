package com.yogeshpaliyal.halo.overlay

import com.yogeshpaliyal.halo.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.view.animation.AccelerateInterpolator
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
 * Folders sit on the inner ring like any favourite. Resting on one for a moment (or tapping
 * it in tap mode) swaps the outer ring for the folder's apps, fanned out next to it. Moving
 * to another favourite closes it again.
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

    /** [angles] is set for an open folder: its apps sit on a short arc instead of evenly spread. */
    private class Ring(val items: List<AppEntry>, val radius: Float, val angles: FloatArray? = null)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val iconSize = dp(ICON_DP)
    private val deadZone = dp(52f)
    private val span = 160f
    /** Direction the half-rings open towards: left when docked right, right when docked left. */
    private val facing = if (onRightEdge) 180f else 0f
    /** +1 / -1 so that slot 0 is always the top item, whichever side the bubble is on. */
    private val dir = if (onRightEdge) -1f else 1f

    init {
        // Icons load lazily; fetch them now so the opening animation never stalls on one.
        (favorites + recents).forEach { app -> app.icon; app.children.forEach { it.icon } }
    }

    private val favRing = favorites.takeIf { it.isNotEmpty() }?.let { Ring(it, dp(INNER_DP)) }
    private val recentRing = recents.takeIf { it.isNotEmpty() }
        ?.let { Ring(it, if (favRing == null) dp(INNER_DP) else dp(OUTER_DP)) }
    private var rings: List<Ring> = listOfNotNull(favRing, recentRing)

    /** Furthest ring the menu can show, open folders included; keeps its position fixed. */
    private val extent = when {
        favRing != null && (recentRing != null || favorites.any { it.isFolder }) -> dp(OUTER_DP)
        favRing != null || recentRing != null -> dp(INNER_DP)
        else -> 0f
    }

    /** The folder whose apps are on the outer ring right now, and its inner-ring slot. */
    private var openFolder: AppEntry? = null
    private var folderSlot = -1
    private var folderProgress = 1f
    private var folderAnim: ValueAnimator? = null
    private val openFolderLater = Runnable { if (selRing == 0 && selSlot >= 0) openFolderAt(selSlot) }

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

    /** Colours from the chosen dial theme (and the bubble's palette for "Match bubble"). */
    private val theme = run {
        BubblePalette.refresh(context)
        DialColors.of(Prefs.dialTheme(context), Prefs.bubblePalette(context))
    }
    private val bandAlpha = Color.alpha(theme.band)
    private val scrim = Paint().apply { color = theme.scrim }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = iconSize + dp(18f)
        color = theme.band
    }
    /** Thin lines along the band's outer and inner edges (glass, neon, daylight). */
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(if (theme.edgeGlow) 2f else 1.5f)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFF5A5F") }
    private val badgeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.glow }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.pill }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.pillText
        textSize = dp(14f)
        isFakeBoldText = true
    }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.hintText
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()
    private val pillRect = RectF()

    private var anim: ValueAnimator? = null

    fun show() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Reverse of [show]: rings shrink back toward the bubble and fade, then [onEnd] runs. */
    fun dismiss(onEnd: () -> Unit) {
        onPick = null
        anim?.cancel()
        var done = false
        val finish = { if (!done) { done = true; onEnd() } }
        anim = ValueAnimator.ofFloat(progress.coerceIn(0f, 1f), 0f).apply {
            duration = 160
            interpolator = AccelerateInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()
            })
            start()
        }
    }

    /** Returns true when the highlighted app changed (used for a haptic tick). */
    fun updateFinger(rawX: Float, rawY: Float): Boolean {
        fingerX = rawX
        fingerY = rawY
        var (ring, slot) = hitTest(rawX, rawY)
        // Back on the inner ring on a different favourite: fold the open folder away.
        if (openFolder != null && ring == 0 && slot != folderSlot) {
            closeFolder()
            val again = hitTest(rawX, rawY)
            ring = again.first
            slot = again.second
        }
        val changed = ring != selRing || slot != selSlot
        selRing = ring
        selSlot = slot
        selected = if (ring >= 0) rings[ring].items[slot] else null
        if (changed) {
            removeCallbacks(openFolderLater)
            val sel = selected
            if (ring == 0 && sel != null && sel.isFolder && openFolder?.key != sel.key) {
                postDelayed(openFolderLater, FOLDER_DWELL_MS)
            }
            invalidate()
        }
        return changed && selected != null
    }

    /** Opens the highlighted folder right away (finger lifted on it). */
    fun openSelectedFolder() {
        if (selRing == 0 && selSlot >= 0) openFolderAt(selSlot)
    }

    /** Opens the folder on inner-ring [slot] now (release or tap on it). No-op if not a folder. */
    fun openFolderAt(slot: Int) {
        val inner = favRing ?: return
        val folder = inner.items.getOrNull(slot)?.takeIf { it.isFolder && it.children.isNotEmpty() } ?: return
        removeCallbacks(openFolderLater)
        if (openFolder?.key == folder.key) return
        val outerR = dp(OUTER_DP)
        val n = folder.children.size
        val step = Math.toDegrees(((iconSize + dp(16f)) / outerR).toDouble()).toFloat()
        val total = (n - 1) * step
        val base = slotAngle(slot, inner.items.size)
        // Centre the fan on the folder, but keep it within the half-ring.
        val lo = facing - span / 2f + total / 2f
        val hi = facing + span / 2f - total / 2f
        val c = if (lo <= hi) base.coerceIn(lo, hi) else facing
        val angles = FloatArray(n) { i -> c + dir * (-total / 2f + i * step) }
        openFolder = folder
        folderSlot = slot
        rings = listOf(inner, Ring(folder.children, outerR, angles))
        if (Prefs.haptics(context)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        folderAnim?.cancel()
        folderAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                folderProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    private fun closeFolder() {
        removeCallbacks(openFolderLater)
        folderAnim?.cancel()
        openFolder = null
        folderSlot = -1
        folderProgress = 1f
        rings = listOfNotNull(favRing, recentRing)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(openFolderLater)
        folderAnim?.cancel()
        super.onDetachedFromWindow()
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
                val sel = selected
                if (sel != null && sel.isFolder) {
                    // Tapping a folder opens it; the next tap picks one of its apps.
                    openFolderAt(selSlot)
                    return true
                }
                onPick = null
                pick(sel)
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
        val outer = extent + iconSize
        val y = anchorScreenY - screenLoc[1]
        return if (height <= 2 * outer) height / 2f else y.coerceIn(outer, height - outer)
    }

    private fun angleOf(ring: Ring, slot: Int): Float =
        ring.angles?.get(slot) ?: slotAngle(slot, ring.items.size)

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

        val slot = ring.angles?.let { angles ->
            // Open folder: nearest of its apps, or nothing if the finger is off the fan.
            val a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            var best = -1
            var bestDiff = Float.MAX_VALUE
            angles.forEachIndexed { i, ang ->
                var d = a - ang
                while (d < -180f) d += 360f
                while (d >= 180f) d -= 360f
                if (kotlin.math.abs(d) < bestDiff) {
                    bestDiff = kotlin.math.abs(d)
                    best = i
                }
            }
            val half = Math.toDegrees(((iconSize + dp(16f)) / ring.radius).toDouble()).toFloat() / 2f
            if (bestDiff > half + 6f) return -1 to -1
            best
        } ?: run {
            val step = span / ring.items.size
            floor((dir * rel + span / 2f) / step).toInt().coerceIn(0, ring.items.lastIndex)
        }

        // Tap mode: only a tap on (or right next to) an icon picks it. A tap on the dimmed
        // background closes the menu. Drag mode stays forgiving so a slide never misses.
        if (onPick != null) {
            val a = Math.toRadians(angleOf(ring, slot).toDouble())
            val ix = ring.radius * kotlin.math.cos(a).toFloat()
            val iy = ring.radius * kotlin.math.sin(a).toFloat()
            if (hypot(dx - ix, dy - iy) > iconSize * 0.9f) return -1 to -1
        }
        return ringIndex to slot
    }

    // --- drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(screenLoc)
        val p = progress.coerceIn(0f, 1.2f)
        val fade = progress.coerceIn(0f, 1f)
        scrim.alpha = (fade * theme.scrimAlpha).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)

        val cx = centerX()
        val cy = centerY()

        if (rings.isEmpty()) {
            hint.alpha = (fade * 255).toInt()
            canvas.drawText(context.getString(R.string.menu_empty_hint), width / 2f, cy, hint)
            return
        }

        rings.forEach { ring ->
            val angles = ring.angles
            if (angles == null) {
                val r = ring.radius * (0.55f + 0.45f * p)
                arcRect.set(cx - r, cy - r, cx + r, cy + r)
                band.alpha = (fade * bandAlpha).toInt()
                canvas.drawArc(arcRect, facing - span / 2f - 4f, span + 8f, false, band)
                drawEdges(canvas, cx, cy, r, facing - span / 2f - 4f, span + 8f, fade)
            } else {
                // Open folder: a short band under just its apps, growing out from the folder.
                val r = folderRadius(ring) * (0.55f + 0.45f * p)
                val half = Math.toDegrees(((iconSize + dp(16f)) / ring.radius).toDouble()).toFloat() / 2f
                val start = angles.min() - half + 4f
                arcRect.set(cx - r, cy - r, cx + r, cy + r)
                val folderFade = fade * folderProgress.coerceIn(0f, 1f)
                band.alpha = (folderFade * bandAlpha).toInt()
                canvas.drawArc(arcRect, start, angles.max() - angles.min() + 2 * half - 8f, false, band)
                drawEdges(canvas, cx, cy, r, start, angles.max() - angles.min() + 2 * half - 8f, folderFade)
            }
        }

        var selCenterX = 0f
        var selCenterY = 0f
        rings.forEachIndexed { ri, ring ->
            val folderRing = ring.angles != null
            val r = (if (folderRing) folderRadius(ring) else ring.radius) * (0.55f + 0.45f * p)
            val ringFade = if (folderRing) fade * folderProgress.coerceIn(0f, 1f) else fade
            ring.items.forEachIndexed { si, app ->
                val a = Math.toRadians(angleOf(ring, si).toDouble())
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                val isSelected = ri == selRing && si == selSlot
                val size = iconSize * (if (isSelected) 1.3f else 1f) * ringFade
                if (ri == 0 && si == folderSlot && !isSelected) {
                    // Keep the open folder lit while you pick from it.
                    canvas.drawCircle(x, y, size * 0.66f, glow)
                }
                if (isSelected) {
                    canvas.drawCircle(x, y, size * 0.72f, glow)
                    selCenterX = x
                    selCenterY = y
                }
                val half = (size / 2f).toInt()
                app.icon.setBounds(x.toInt() - half, y.toInt() - half, x.toInt() + half, y.toInt() + half)
                // Paused work apps are dimmed; opening one shows Android's "Unpause?" prompt.
                app.icon.alpha = (ringFade * if (app.available) 255 else 100).toInt()
                app.icon.draw(canvas)
                val dot = app.key in badged || app.children.any { it.key in badged }
                if (dot && ringFade > 0.5f) {
                    val bx = x + size * 0.36f
                    val by = y - size * 0.36f
                    canvas.drawCircle(bx, by, size * 0.15f, badgeRim)
                    canvas.drawCircle(bx, by, size * 0.11f, badgePaint)
                }
            }
        }

        selected?.let { drawLabel(canvas, it.label, selCenterX, selCenterY) }
    }

    /** Outline along both edges of a band, following its rounded ends' arc. */
    private fun drawEdges(canvas: Canvas, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, fade: Float) {
        val half = band.strokeWidth / 2f
        for ((color, rr) in listOf(theme.edgeOuter to r + half, theme.edgeInner to r - half)) {
            if (color == 0 || rr <= 0f) continue
            edge.color = color
            edge.alpha = (fade * Color.alpha(color)).toInt()
            // Neon edges glow (drawn by the GPU on Android 9+; plain lines before that).
            if (theme.edgeGlow) edge.setShadowLayer(dp(7f), 0f, 0f, color) else edge.clearShadowLayer()
            arcRect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            canvas.drawArc(arcRect, start, sweep, false, edge)
        }
    }

    /** Open-folder apps glide out from the inner ring to the outer one. */
    private fun folderRadius(ring: Ring): Float {
        val inner = favRing?.radius ?: ring.radius
        return inner + (ring.radius - inner) * folderProgress
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
        private const val FOLDER_DWELL_MS = 280L

        /** How far the menu reaches above and below its centre, in px. */
        /** Pass hasRecents = true when favourites include a folder: open folders use the outer ring. */
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
