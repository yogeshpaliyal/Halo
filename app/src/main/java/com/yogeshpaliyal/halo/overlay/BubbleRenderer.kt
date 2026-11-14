package com.yogeshpaliyal.halo.overlay

import android.graphics.Canvas
import android.os.Build
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Colour themes for the bubble. [face] is the colour of eyes and features on that body.
 * [WALLPAPER] follows Material You: its colours are filled in from the system by [refresh].
 */
enum class BubblePalette(
    @StringRes val labelRes: Int,
    var top: Int,
    var bottom: Int,
    var face: Int,
    var accent: Int,
) {
    WALLPAPER(R.string.palette_wallpaper, 0xFFB9C3FF.toInt(), 0xFF4A5BB3.toInt(), 0xFF101A47.toInt(), Color.WHITE),
    MIDNIGHT(R.string.palette_midnight, 0xFF3A3358.toInt(), 0xFF14121F.toInt(), Color.WHITE, 0xFFB9A8FF.toInt()),
    SUNSET(R.string.palette_sunset, 0xFFFFB36B.toInt(), 0xFFFF5E7E.toInt(), 0xFF2B1430.toInt(), Color.WHITE),
    OCEAN(R.string.palette_ocean, 0xFF7FDBFF.toInt(), 0xFF2D7FF9.toInt(), 0xFF0E1B3D.toInt(), Color.WHITE),
    MINT(R.string.palette_mint, 0xFFA8F7D0.toInt(), 0xFF22C08A.toInt(), 0xFF0F2E22.toInt(), Color.WHITE),
    BUBBLEGUM(R.string.palette_bubblegum, 0xFFFFC6E5.toInt(), 0xFFFF6FB5.toInt(), 0xFF3A1030.toInt(), Color.WHITE),
    LEMON(R.string.palette_lemon, 0xFFFFF7A8.toInt(), 0xFFFFC93C.toInt(), 0xFF3A2A00.toInt(), 0xFF3A2A00.toInt()),
    GRAPE(R.string.palette_grape, 0xFFD2B0FF.toInt(), 0xFF7A3DF0.toInt(), 0xFF1F0B3D.toInt(), Color.WHITE),
    MONO(R.string.palette_mono, 0xFFFFFFFF.toInt(), 0xFFCFCFCF.toInt(), 0xFF1A1A1A.toInt(), 0xFF1A1A1A.toInt()),
    ;

    companion object {
        /** Material You colours exist from Android 12. */
        val wallpaperSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** Palettes to offer in settings. */
        fun available(): List<BubblePalette> = entries.filter { it != WALLPAPER || wallpaperSupported }

        /**
         * Reads the current wallpaper colours into [WALLPAPER]. Cheap; called whenever the
         * bubble, dial or settings draw, so a new wallpaper shows up on its own.
         */
        fun refresh(context: android.content.Context) {
            if (!wallpaperSupported) return
            WALLPAPER.top = context.getColor(android.R.color.system_accent1_200)
            WALLPAPER.bottom = context.getColor(android.R.color.system_accent1_600)
            WALLPAPER.face = context.getColor(android.R.color.system_accent1_900)
            WALLPAPER.accent = context.getColor(android.R.color.system_accent1_50)
        }
    }
}

/**
 * Something for the character to wear. Each unlocks after [unlockAt] app switches made
 * with Halo. [onTop] ones sit on the head, so the character shrinks a little to fit.
 */
enum class Accessory(@StringRes val labelRes: Int, val unlockAt: Int, val onTop: Boolean) {
    NONE(R.string.accessory_none, 0, false),
    SUNGLASSES(R.string.accessory_sunglasses, 0, false),
    CAP(R.string.accessory_cap, 10, true),
    HEADPHONES(R.string.accessory_headphones, 25, true),
    BOW(R.string.accessory_bow, 50, true),
    PARTY_HAT(R.string.accessory_party_hat, 100, true),
    CROWN(R.string.accessory_crown, 200, true),
}

/** What the bubble looks like. All original drawings, made from simple shapes. */
enum class BubbleCharacter(@StringRes val labelRes: Int) {
    CLASSIC(R.string.character_classic),
    BLOB(R.string.character_blob),
    KITTY(R.string.character_kitty),
    GHOST(R.string.character_ghost),
    SPROUT(R.string.character_sprout),
    STAR(R.string.character_star),
    BOT(R.string.character_bot),
    /** The user's own photo, in a ring of the chosen colour. */
    CUSTOM(R.string.character_custom),
}

/**
 * Draws the bubble: one of the characters in one palette, with its current expression.
 * Shared by the floating bubble and the previews in settings, so they always match.
 *
 * Everything is sized from the radius, so it scales with the bubble size setting.
 */
class BubbleRenderer {
    var palette = BubblePalette.MIDNIGHT
    var character = BubbleCharacter.CLASSIC
    /** Photo for [BubbleCharacter.CUSTOM]; falls back to Classic when missing. */
    var customImage: android.graphics.Bitmap? = null
    /** Worn by every character except Classic. */
    var accessory = Accessory.NONE

    enum class Mood { NORMAL, PRESSED, HAPPY }

    // Expression, all animated by the bubble view.
    var mood = Mood.NORMAL
    /** 0 = eyes open, 1 = closed. */
    var blink = 0f
    /** Where the eyes look, each -1..1. */
    var lookX = 0f
    var lookY = 0f
    /** -1..1: ear flick, leaf sway, ghost bob, star tilt, antenna glow. */
    var wiggle = 0f

    // Reactions to your day.
    /** Night time: eyes closed and a couple of floating z's (only while untouched). */
    var sleepy = false
    /** Battery under 15% and not charging: a sweat drop. */
    var lowBattery = false
    /** Plugged in: a lightning bolt. */
    var charging = false
    /** 0..1 animation phase for the z's, sweat drop and bolt, driven by idle moments. */
    var extraPhase = 0f

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FF7A9A }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG)
    private val extraFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val extraStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFF1B1530.toInt()
    }
    private val zText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val zOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFF1B1530.toInt()
    }
    private val path = Path()
    private val rect = RectF()
    private var shaderKey = ""

    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        face.color = palette.face
        stroke.color = palette.face
        val key = "${palette.top}|${palette.bottom}|$cx|$cy|$r"
        if (key != shaderKey) {
            body.shader = LinearGradient(cx, cy - r, cx, cy + r, palette.top, palette.bottom, Shader.TileMode.CLAMP)
            shaderKey = key
        }
        val wearing = if (character == BubbleCharacter.CLASSIC) Accessory.NONE else accessory
        if (wearing.onTop) {
            // Shrink toward the bottom so the hat has room inside the bubble.
            canvas.save()
            canvas.scale(HAT_SCALE, HAT_SCALE, cx, cy + r * 0.9f)
        }
        drawCharacter(canvas, cx, cy, r)
        if (wearing != Accessory.NONE) wear(canvas, wearing, anchor(cx, cy, r))
        if (wearing.onTop) canvas.restore()
        if (animated) extras(canvas, cx, cy, r)
    }

    private fun drawCharacter(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        when (character) {
            BubbleCharacter.CLASSIC -> classic(canvas, cx, cy, r)
            BubbleCharacter.BLOB -> blob(canvas, cx, cy, r)
            BubbleCharacter.KITTY -> kitty(canvas, cx, cy, r)
            BubbleCharacter.GHOST -> ghost(canvas, cx, cy, r)
            BubbleCharacter.SPROUT -> sprout(canvas, cx, cy, r)
            BubbleCharacter.STAR -> star(canvas, cx, cy, r)
            BubbleCharacter.BOT -> bot(canvas, cx, cy, r)
            BubbleCharacter.CUSTOM -> customImage?.let { photo(canvas, cx, cy, r, it) } ?: classic(canvas, cx, cy, r)
        }
    }

    // --- accessories -----------------------------------------------------------------------

    /** Where a character's head and eyes are, for fitting accessories. */
    private class Anchor(val cx: Float, val top: Float, val half: Float, val eyeY: Float, val eyeDx: Float, val s: Float)

    private fun anchor(cx: Float, cy: Float, r: Float): Anchor = when (character) {
        BubbleCharacter.KITTY -> { val hr = r * 0.8f; val hy = cy + r * 0.12f; Anchor(cx, hy - hr, hr, hy - hr * 0.05f, hr * 0.36f, hr) }
        BubbleCharacter.GHOST -> { val bob = wiggle * r * 0.06f; Anchor(cx, cy - r * 0.86f + bob, r * 0.8f, cy - r * 0.12f + bob, r * 0.28f, r) }
        BubbleCharacter.SPROUT -> { val hr = r * 0.78f; val hy = cy + r * 0.18f; Anchor(cx, hy - hr, hr, hy - hr * 0.08f, hr * 0.34f, hr) }
        BubbleCharacter.STAR -> Anchor(cx, cy - r * 0.84f, r * 0.55f, cy + r * 0.04f, r * 0.82f * 0.26f, r * 0.82f)
        BubbleCharacter.BOT -> { val hw = r * 0.78f; val hy = cy + r * 0.14f; Anchor(cx, hy - hw * 0.84f, hw, hy - hw * 0.1f, hw * 0.36f, hw) }
        BubbleCharacter.CUSTOM -> Anchor(cx, cy - r * 0.86f, r * 0.86f, cy - r * 0.1f, r * 0.3f, r)
        else -> Anchor(cx, cy - r * 0.86f, r * 0.94f, cy - r * 0.08f, r * 0.3f, r) // Blob
    }

    private val acc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun wear(c: Canvas, item: Accessory, a: Anchor) {
        when (item) {
            Accessory.SUNGLASSES -> {
                val w = a.s * 0.32f
                val h = a.s * 0.22f
                acc.color = 0xFF151515.toInt()
                for (side in listOf(-1f, 1f)) {
                    val x = a.cx + side * a.eyeDx
                    rect.set(x - w / 2, a.eyeY - h / 2, x + w / 2, a.eyeY + h / 2)
                    c.drawRoundRect(rect, h * 0.45f, h * 0.45f, acc)
                    accLine.color = 0x66FFFFFF
                    accLine.strokeWidth = a.s * 0.03f
                    c.drawLine(x - w * 0.25f, a.eyeY - h * 0.2f, x - w * 0.05f, a.eyeY - h * 0.2f, accLine)
                }
                accLine.color = 0xFF151515.toInt()
                accLine.strokeWidth = a.s * 0.05f
                c.drawLine(a.cx - a.eyeDx + w / 2, a.eyeY - h * 0.15f, a.cx + a.eyeDx - w / 2, a.eyeY - h * 0.15f, accLine)
            }
            Accessory.CAP -> {
                val w = a.half * 0.8f
                val h = a.half * 0.42f
                acc.color = 0xFFE53935.toInt()
                rect.set(a.cx - w, a.top - h, a.cx + w, a.top + h)
                c.drawArc(rect, 180f, 180f, true, acc)
                // Brim to the side, then the button on top.
                rect.set(a.cx + w * 0.2f, a.top - h * 0.08f, a.cx + w * 1.35f, a.top + h * 0.22f)
                c.drawOval(rect, acc)
                acc.color = 0xFFFFFFFF.toInt()
                c.drawCircle(a.cx, a.top - h * 0.95f, a.half * 0.06f, acc)
            }
            Accessory.HEADPHONES -> {
                val rr = (a.eyeY - a.top) + a.half * 0.14f
                accLine.color = 0xFF263238.toInt()
                accLine.strokeWidth = a.s * 0.1f
                rect.set(a.cx - rr, a.eyeY - rr, a.cx + rr, a.eyeY + rr)
                c.drawArc(rect, 205f, 130f, false, accLine)
                for (side in listOf(-1f, 1f)) {
                    val x = a.cx + side * a.half * 0.96f
                    acc.color = 0xFF263238.toInt()
                    rect.set(x - a.half * 0.15f, a.eyeY - a.half * 0.26f, x + a.half * 0.15f, a.eyeY + a.half * 0.24f)
                    c.drawRoundRect(rect, a.half * 0.12f, a.half * 0.12f, acc)
                    acc.color = 0xFFFF6F61.toInt()
                    rect.inset(a.half * 0.05f, a.half * 0.07f)
                    c.drawRoundRect(rect, a.half * 0.08f, a.half * 0.08f, acc)
                }
            }
            Accessory.BOW -> {
                val x = a.cx + a.half * 0.5f
                val y = a.top + a.half * 0.1f
                val s = a.half * 0.3f
                acc.color = 0xFFFF4FA3.toInt()
                for (side in listOf(-1f, 1f)) {
                    path.reset()
                    path.moveTo(x, y)
                    path.lineTo(x + side * s, y - s * 0.7f)
                    path.lineTo(x + side * s, y + s * 0.7f)
                    path.close()
                    c.drawPath(path, acc)
                }
                acc.color = 0xFFD81B7A.toInt()
                c.drawCircle(x, y, s * 0.3f, acc)
            }
            Accessory.PARTY_HAT -> {
                val baseY = a.top + a.half * 0.06f
                val w = a.half * 0.42f
                val h = a.half * 0.62f
                c.save()
                c.rotate(-14f, a.cx, baseY)
                path.reset()
                path.moveTo(a.cx - w, baseY)
                path.lineTo(a.cx, baseY - h)
                path.lineTo(a.cx + w, baseY)
                path.close()
                acc.color = 0xFF7C4DFF.toInt()
                c.drawPath(path, acc)
                c.save()
                c.clipPath(path)
                accLine.color = 0xFFFFD54F.toInt()
                accLine.strokeWidth = a.half * 0.08f
                for (k in 1..3) {
                    val yy = baseY - h * k / 4f
                    c.drawLine(a.cx - w, yy + h * 0.12f, a.cx + w, yy - h * 0.12f, accLine)
                }
                c.restore()
                acc.color = 0xFFFFFFFF.toInt()
                c.drawCircle(a.cx, baseY - h, a.half * 0.1f, acc)
                c.restore()
            }
            Accessory.CROWN -> {
                val w = a.half * 0.5f
                val h = a.half * 0.48f
                val baseY = a.top + a.half * 0.1f
                path.reset()
                path.moveTo(a.cx - w, baseY)
                path.lineTo(a.cx - w, baseY - h * 0.75f)
                path.lineTo(a.cx - w * 0.5f, baseY - h * 0.35f)
                path.lineTo(a.cx, baseY - h)
                path.lineTo(a.cx + w * 0.5f, baseY - h * 0.35f)
                path.lineTo(a.cx + w, baseY - h * 0.75f)
                path.lineTo(a.cx + w, baseY)
                path.close()
                acc.color = 0xFFFFC83D.toInt()
                c.drawPath(path, acc)
                accLine.color = 0xFFB8860B.toInt()
                accLine.strokeWidth = a.half * 0.035f
                c.drawPath(path, accLine)
                acc.color = 0xFFE53935.toInt()
                c.drawCircle(a.cx, baseY - h * 0.3f, a.half * 0.07f, acc)
                acc.color = 0xFF1E88E5.toInt()
                c.drawCircle(a.cx - w * 0.55f, baseY - h * 0.2f, a.half * 0.05f, acc)
                c.drawCircle(a.cx + w * 0.55f, baseY - h * 0.2f, a.half * 0.05f, acc)
            }
            Accessory.NONE -> Unit
        }
    }

    /** Asleep only while nobody's touching it. */
    private val dozing: Boolean get() = sleepy && mood == Mood.NORMAL

    private fun extras(c: Canvas, cx: Float, cy: Float, r: Float) {
        if (dozing) {
            // Two z's drifting up from the top left.
            val drift = extraPhase * r * 0.12f
            zz(c, cx - r * 0.6f, cy - r * 0.48f - drift, r * 0.44f, 1f - extraPhase * 0.3f)
            zz(c, cx - r * 0.88f, cy - r * 0.86f - drift, r * 0.32f, 0.75f - extraPhase * 0.3f)
        }
        if (lowBattery) {
            // A sweat drop at the right temple, sliding down a little when it drips.
            val x = cx + r * 0.72f
            val y = cy - r * 0.22f + extraPhase * r * 0.18f
            val s = r * 0.17f
            path.reset()
            path.moveTo(x, y - s * 1.3f)
            path.cubicTo(x + s * 0.2f, y - s * 0.6f, x + s, y - s * 0.1f, x + s * 0.9f, y + s * 0.4f)
            path.cubicTo(x + s * 0.8f, y + s * 1.05f, x - s * 0.8f, y + s * 1.05f, x - s * 0.9f, y + s * 0.4f)
            path.cubicTo(x - s, y - s * 0.1f, x - s * 0.2f, y - s * 0.6f, x, y - s * 1.3f)
            path.close()
            extraFill.color = 0xFF7FD3FF.toInt()
            extraStroke.strokeWidth = r * 0.04f
            c.drawPath(path, extraFill)
            c.drawPath(path, extraStroke)
            extraFill.color = 0xCCFFFFFF.toInt()
            c.drawCircle(x - s * 0.35f, y + s * 0.25f, s * 0.22f, extraFill)
        }
        if (charging) {
            // A lightning bolt at the bottom right that flashes bigger now and then.
            val k = 1f + 0.25f * kotlin.math.sin(extraPhase * Math.PI.toFloat())
            val x = cx + r * 0.66f
            val y = cy + r * 0.6f
            val s = r * 0.3f * k
            path.reset()
            path.moveTo(x + s * 0.15f, y - s)
            path.lineTo(x - s * 0.55f, y + s * 0.12f)
            path.lineTo(x - s * 0.02f, y + s * 0.12f)
            path.lineTo(x - s * 0.2f, y + s)
            path.lineTo(x + s * 0.55f, y - s * 0.18f)
            path.lineTo(x + s * 0.02f, y - s * 0.18f)
            path.close()
            extraFill.color = 0xFFFFD54F.toInt()
            extraStroke.strokeWidth = r * 0.05f
            c.drawPath(path, extraStroke)
            c.drawPath(path, extraFill)
        }
    }

    private fun zz(c: Canvas, x: Float, y: Float, size: Float, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        zText.textSize = size
        zOutline.textSize = size
        zOutline.strokeWidth = size * 0.18f
        zOutline.alpha = a
        zText.color = Color.WHITE
        zText.alpha = a
        c.drawText("z", x, y, zOutline)
        c.drawText("z", x, y, zText)
    }

    /** True when the character has a face to animate. */
    val animated: Boolean get() = character != BubbleCharacter.CLASSIC

    // --- characters ------------------------------------------------------------------------

    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photoClip = Path()
    private val photoRect = RectF()

    private fun photo(c: Canvas, cx: Float, cy: Float, r: Float, bmp: android.graphics.Bitmap) {
        val inner = r * 0.86f
        c.save()
        // A gentle rock on wiggle, since photos have no face to animate.
        c.rotate(wiggle * 10f, cx, cy)
        photoClip.reset()
        photoClip.addCircle(cx, cy, inner, Path.Direction.CW)
        c.clipPath(photoClip)
        photoRect.set(cx - inner, cy - inner, cx + inner, cy + inner)
        c.drawBitmap(bmp, null, photoRect, photoPaint)
        c.restore()
        // Ring in the chosen colour, so the palette still matters.
        stroke.color = if (palette == BubblePalette.MIDNIGHT) palette.accent else palette.bottom
        stroke.strokeWidth = r * 0.12f
        c.drawCircle(cx, cy, r * 0.88f, stroke)
        stroke.color = palette.face
    }

    private fun classic(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r * 0.94f, body)
        stroke.color = palette.accent
        stroke.strokeWidth = r * 0.12f
        c.drawCircle(cx, cy, r * 0.58f, stroke)
        face.color = if (palette == BubblePalette.MIDNIGHT) Color.WHITE else palette.accent
        c.drawCircle(cx, cy, r * 0.18f, face)
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float) {
        // A soft jelly: a touch wider than tall, squashing a bit more when pressed.
        val squash = if (mood == Mood.PRESSED) 0.9f else 0.97f
        rect.set(cx - r * 0.94f, cy - r * 0.94f * squash + r * 0.06f, cx + r * 0.94f, cy + r * 0.94f)
        c.drawOval(rect, body)
        eyes(c, cx, cy - r * 0.08f, r, spacing = 0.3f)
        c.drawCircle(cx - r * 0.5f, cy + r * 0.2f, r * 0.13f, blush)
        c.drawCircle(cx + r * 0.5f, cy + r * 0.2f, r * 0.13f, blush)
        smile(c, cx, cy + r * 0.2f, r * 0.16f, r)
    }

    private fun kitty(c: Canvas, cx: Float, cy: Float, r: Float) {
        val hr = r * 0.8f
        val hy = cy + r * 0.12f
        // Ears behind the head; the right one flicks on wiggle.
        ear(c, cx - hr * 0.58f, hy - hr * 0.62f, hr, -1f, 0f)
        ear(c, cx + hr * 0.58f, hy - hr * 0.62f, hr, 1f, wiggle * 14f)
        c.drawCircle(cx, hy, hr, body)
        eyes(c, cx, hy - hr * 0.05f, hr, spacing = 0.36f)
        // Nose and whiskers.
        path.reset()
        path.moveTo(cx - hr * 0.08f, hy + hr * 0.2f)
        path.lineTo(cx + hr * 0.08f, hy + hr * 0.2f)
        path.lineTo(cx, hy + hr * 0.3f)
        path.close()
        c.drawPath(path, face)
        stroke.strokeWidth = hr * 0.045f
        for (s in listOf(-1f, 1f)) {
            c.drawLine(cx + s * hr * 0.32f, hy + hr * 0.3f, cx + s * hr * 0.78f, hy + hr * 0.24f, stroke)
            c.drawLine(cx + s * hr * 0.32f, hy + hr * 0.38f, cx + s * hr * 0.76f, hy + hr * 0.44f, stroke)
        }
    }

    private fun ear(c: Canvas, x: Float, y: Float, hr: Float, side: Float, rotate: Float) {
        c.save()
        c.rotate(rotate, x, y + hr * 0.3f)
        path.reset()
        path.moveTo(x - side * hr * 0.34f, y + hr * 0.4f)
        path.lineTo(x + side * hr * 0.12f, y - hr * 0.6f)
        path.lineTo(x + side * hr * 0.46f, y + hr * 0.22f)
        path.close()
        c.drawPath(path, body)
        c.restore()
    }

    private fun ghost(c: Canvas, cx: Float, cy: Float, r: Float) {
        val bob = wiggle * r * 0.06f
        val w = r * 0.8f
        val top = cy - r * 0.86f + bob
        val bottom = cy + r * 0.82f + bob
        path.reset()
        path.moveTo(cx - w, bottom)
        path.lineTo(cx - w, top + w)
        rect.set(cx - w, top, cx + w, top + 2 * w)
        path.arcTo(rect, 180f, 180f)
        path.lineTo(cx + w, bottom)
        // Wavy hem.
        val waves = 3
        val step = 2 * w / waves
        for (i in 0 until waves) {
            val x0 = cx + w - i * step
            path.quadTo(x0 - step / 4f, bottom - r * 0.16f, x0 - step / 2f, bottom)
            path.quadTo(x0 - step * 3f / 4f, bottom + r * 0.12f, x0 - step, bottom)
        }
        path.close()
        c.drawPath(path, body)
        eyes(c, cx, cy - r * 0.12f + bob, r, spacing = 0.28f, tall = true)
        if (mood == Mood.HAPPY) {
            smile(c, cx, cy + r * 0.22f + bob, r * 0.12f, r)
        } else {
            c.drawCircle(cx, cy + r * 0.26f + bob, r * 0.08f, face)
        }
    }

    private fun sprout(c: Canvas, cx: Float, cy: Float, r: Float) {
        val hr = r * 0.78f
        val hy = cy + r * 0.18f
        // Stem and leaf, swaying from the top of the head (tucked away under a hat).
        val baseY = hy - hr
        if (!accessory.onTop) {
        c.save()
        c.rotate(wiggle * 16f, cx, baseY)
        stroke.color = 0xFF3FA34D.toInt()
        stroke.strokeWidth = r * 0.08f
        c.drawLine(cx, baseY + r * 0.02f, cx, baseY - r * 0.16f, stroke)
        leaf.color = 0xFF5BD06A.toInt()
        path.reset()
        path.moveTo(cx, baseY - r * 0.14f)
        path.quadTo(cx + r * 0.18f, baseY - r * 0.36f, cx + r * 0.44f, baseY - r * 0.2f)
        path.quadTo(cx + r * 0.2f, baseY - r * 0.02f, cx, baseY - r * 0.14f)
        c.drawPath(path, leaf)
        c.restore()
        }
        stroke.color = palette.face
        c.drawCircle(cx, hy, hr, body)
        eyes(c, cx, hy - hr * 0.08f, hr, spacing = 0.34f)
        c.drawCircle(cx - hr * 0.56f, hy + hr * 0.24f, hr * 0.13f, blush)
        c.drawCircle(cx + hr * 0.56f, hy + hr * 0.24f, hr * 0.13f, blush)
        smile(c, cx, hy + hr * 0.24f, hr * 0.16f, hr)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.save()
        c.rotate(wiggle * 12f, cx, cy)
        path.reset()
        val outer = r * 0.98f
        val inner = r * 0.56f
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) outer else inner
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + rad * cos(a).toFloat()
            val y = cy + r * 0.06f + rad * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val round = body.pathEffect
        body.pathEffect = CornerPathEffect(r * 0.18f)
        c.drawPath(path, body)
        body.pathEffect = round
        c.restore()
        eyes(c, cx, cy + r * 0.04f, r * 0.82f, spacing = 0.26f)
        smile(c, cx, cy + r * 0.26f, r * 0.1f, r * 0.82f)
    }

    private fun bot(c: Canvas, cx: Float, cy: Float, r: Float) {
        val hw = r * 0.78f
        val hy = cy + r * 0.14f
        // Antenna with a light that glows on wiggle (folded away under a hat).
        if (!accessory.onTop) {
            stroke.color = palette.face
            stroke.strokeWidth = r * 0.07f
            c.drawLine(cx, hy - hw * 0.84f, cx, hy - hw * 1.08f, stroke)
            leaf.color = if (wiggle != 0f) 0xFFFF5A5F.toInt() else palette.face
            c.drawCircle(cx, hy - hw * 1.12f, r * 0.1f + kotlin.math.abs(wiggle) * r * 0.04f, leaf)
        }
        rect.set(cx - hw, hy - hw * 0.84f, cx + hw, hy + hw * 0.84f)
        c.drawRoundRect(rect, hw * 0.42f, hw * 0.42f, body)
        // Screen-like eyes.
        val ex = hw * 0.36f
        val ew = hw * 0.18f
        val closed = if (dozing) 1f else blink
        val eh = (hw * 0.3f) * (1f - closed).coerceAtLeast(0.15f) * if (mood == Mood.PRESSED) 0.5f else 1f
        val ox = lookX * hw * 0.08f
        val oy = hy - hw * 0.1f + lookY * hw * 0.06f
        for (s in listOf(-1f, 1f)) {
            rect.set(cx + s * ex - ew / 2 + ox, oy - eh / 2, cx + s * ex + ew / 2 + ox, oy + eh / 2)
            c.drawRoundRect(rect, ew / 2, ew / 2, face)
        }
        stroke.strokeWidth = hw * 0.07f
        if (mood == Mood.HAPPY) {
            smile(c, cx, hy + hw * 0.32f, hw * 0.16f, hw)
        } else {
            c.drawLine(cx - hw * 0.18f, hy + hw * 0.4f, cx + hw * 0.18f, hy + hw * 0.4f, stroke)
        }
    }

    // --- features --------------------------------------------------------------------------

    /** Two eyes that blink, look around, squint when pressed and turn into ^ ^ when happy. */
    private fun eyes(c: Canvas, cx: Float, cy: Float, r: Float, spacing: Float, tall: Boolean = false) {
        val dx = r * spacing
        val ox = lookX * r * 0.1f
        val oy = lookY * r * 0.08f
        if (mood == Mood.HAPPY) {
            stroke.strokeWidth = r * 0.08f
            for (s in listOf(-1f, 1f)) {
                val x = cx + s * dx + ox
                rect.set(x - r * 0.12f, cy - r * 0.04f + oy, x + r * 0.12f, cy + r * 0.16f + oy)
                c.drawArc(rect, 200f, 140f, false, stroke)
            }
            return
        }
        if (dozing) {
            // Peaceful closed eyes.
            stroke.strokeWidth = r * 0.07f
            for (s in listOf(-1f, 1f)) {
                val x = cx + s * dx
                rect.set(x - r * 0.1f, cy - r * 0.1f, x + r * 0.1f, cy + r * 0.06f)
                c.drawArc(rect, 20f, 140f, false, stroke)
            }
            return
        }
        val w = r * 0.13f
        var h = r * (if (tall) 0.24f else 0.2f)
        if (mood == Mood.PRESSED) h *= 0.45f
        h *= (1f - blink).coerceAtLeast(0.12f)
        for (s in listOf(-1f, 1f)) {
            val x = cx + s * dx + ox
            rect.set(x - w / 2, cy - h / 2 + oy, x + w / 2, cy + h / 2 + oy)
            c.drawOval(rect, face)
        }
    }

    private fun smile(c: Canvas, cx: Float, cy: Float, w: Float, r: Float) {
        stroke.strokeWidth = r * 0.07f
        val big = if (mood == Mood.HAPPY) 1.4f else 1f
        rect.set(cx - w * big, cy - w * big, cx + w * big, cy + w * big)
        c.drawArc(rect, 20f, 140f, false, stroke)
    }

    private companion object {
        /** How much a character shrinks while wearing something on its head. */
        const val HAT_SCALE = 0.78f
    }
}
