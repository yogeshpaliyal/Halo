package com.yogeshpaliyal.halo.overlay

import android.graphics.Canvas
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

/** Colour themes for the bubble. [face] is the colour of eyes and features on that body. */
enum class BubblePalette(
    @StringRes val labelRes: Int,
    val top: Int,
    val bottom: Int,
    val face: Int,
    val accent: Int,
) {
    MIDNIGHT(R.string.palette_midnight, 0xFF3A3358.toInt(), 0xFF14121F.toInt(), Color.WHITE, 0xFFB9A8FF.toInt()),
    SUNSET(R.string.palette_sunset, 0xFFFFB36B.toInt(), 0xFFFF5E7E.toInt(), 0xFF2B1430.toInt(), Color.WHITE),
    OCEAN(R.string.palette_ocean, 0xFF7FDBFF.toInt(), 0xFF2D7FF9.toInt(), 0xFF0E1B3D.toInt(), Color.WHITE),
    MINT(R.string.palette_mint, 0xFFA8F7D0.toInt(), 0xFF22C08A.toInt(), 0xFF0F2E22.toInt(), Color.WHITE),
    BUBBLEGUM(R.string.palette_bubblegum, 0xFFFFC6E5.toInt(), 0xFFFF6FB5.toInt(), 0xFF3A1030.toInt(), Color.WHITE),
    LEMON(R.string.palette_lemon, 0xFFFFF7A8.toInt(), 0xFFFFC93C.toInt(), 0xFF3A2A00.toInt(), 0xFF3A2A00.toInt()),
    GRAPE(R.string.palette_grape, 0xFFD2B0FF.toInt(), 0xFF7A3DF0.toInt(), 0xFF1F0B3D.toInt(), Color.WHITE),
    MONO(R.string.palette_mono, 0xFFFFFFFF.toInt(), 0xFFCFCFCF.toInt(), 0xFF1A1A1A.toInt(), 0xFF1A1A1A.toInt()),
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

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FF7A9A }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var shaderKey = ""

    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        face.color = palette.face
        stroke.color = palette.face
        val key = "${palette.name}|$cx|$cy|$r"
        if (key != shaderKey) {
            body.shader = LinearGradient(cx, cy - r, cx, cy + r, palette.top, palette.bottom, Shader.TileMode.CLAMP)
            shaderKey = key
        }
        when (character) {
            BubbleCharacter.CLASSIC -> classic(canvas, cx, cy, r)
            BubbleCharacter.BLOB -> blob(canvas, cx, cy, r)
            BubbleCharacter.KITTY -> kitty(canvas, cx, cy, r)
            BubbleCharacter.GHOST -> ghost(canvas, cx, cy, r)
            BubbleCharacter.SPROUT -> sprout(canvas, cx, cy, r)
            BubbleCharacter.STAR -> star(canvas, cx, cy, r)
            BubbleCharacter.BOT -> bot(canvas, cx, cy, r)
        }
    }

    /** True when the character has a face to animate. */
    val animated: Boolean get() = character != BubbleCharacter.CLASSIC

    // --- characters ------------------------------------------------------------------------

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
        // Stem and leaf, swaying from the top of the head.
        val baseY = hy - hr
        c.save()
        c.rotate(wiggle * 16f, cx, baseY)
        stroke.color = 0xFF3FA34D.toInt()
        stroke.strokeWidth = r * 0.08f
        c.drawLine(cx, baseY + r * 0.02f, cx, baseY - r * 0.16f, stroke)
        leaf.color = 0xFF5BD06A.toInt()
        path.reset()
        path.moveTo(cx, baseY - r * 0.14f)
        path.quadTo(cx + r * 0.18f, baseY - r * 0.42f, cx + r * 0.46f, baseY - r * 0.24f)
        path.quadTo(cx + r * 0.2f, baseY - r * 0.04f, cx, baseY - r * 0.14f)
        c.drawPath(path, leaf)
        c.restore()
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
        // Antenna with a light that glows on wiggle.
        stroke.color = palette.face
        stroke.strokeWidth = r * 0.07f
        c.drawLine(cx, hy - hw * 0.84f, cx, hy - hw * 1.08f, stroke)
        leaf.color = if (wiggle != 0f) 0xFFFF5A5F.toInt() else palette.face
        c.drawCircle(cx, hy - hw * 1.12f, r * 0.1f + kotlin.math.abs(wiggle) * r * 0.04f, leaf)
        rect.set(cx - hw, hy - hw * 0.84f, cx + hw, hy + hw * 0.84f)
        c.drawRoundRect(rect, hw * 0.42f, hw * 0.42f, body)
        // Screen-like eyes.
        val ex = hw * 0.36f
        val ew = hw * 0.18f
        val eh = (hw * 0.3f) * (1f - blink).coerceAtLeast(0.15f) * if (mood == Mood.PRESSED) 0.5f else 1f
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
}
