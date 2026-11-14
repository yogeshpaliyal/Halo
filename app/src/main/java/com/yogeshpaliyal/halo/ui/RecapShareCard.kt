package com.yogeshpaliyal.halo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.Stats
import com.yogeshpaliyal.halo.overlay.Accessory
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import com.yogeshpaliyal.halo.overlay.BubbleRenderer

/** What the share card shows. */
enum class ShareLayout(@StringRes val labelRes: Int) {
    SUMMARY(R.string.share_layout_summary),
    TOP_APPS(R.string.share_layout_top),
    DUO(R.string.share_layout_duo),
    RHYTHM(R.string.share_layout_rhythm),
}

/** Background for the share card. [BUBBLE] uses the bubble's own colours. */
enum class ShareTheme(val from: Long, val to: Long) {
    AURORA(0xFF6A3DE8, 0xFFE0459B),
    SUNSET(0xFFFF7A45, 0xFFFF3D7F),
    OCEAN(0xFF00B4D8, 0xFF3A0CA3),
    NEON(0xFF0B0B14, 0xFF1A1446),
    MINT(0xFF0F9B8E, 0xFF1B5E20),
    BUBBLE(0, 0),
    ;

    fun colors(palette: BubblePalette): Pair<Int, Int> =
        if (this == BUBBLE) palette.top to palette.bottom else from.toInt() to to.toInt()
}

/**
 * The shareable recap image (1080 × 1350, the 4:5 size most apps show in full). Four
 * layouts, six backgrounds, always with your character and a small Halo credit.
 */
object RecapShareCard {
    private const val W = 1080
    private const val H = 1350
    private const val LEFT = 80f

    fun render(
        context: Context,
        s: Stats.Summary,
        entries: Map<String, AppEntry>,
        character: BubbleCharacter,
        palette: BubblePalette,
        customImage: Bitmap?,
        accessory: Accessory,
        layout: ShareLayout = ShareLayout.SUMMARY,
        theme: ShareTheme = ShareTheme.AURORA,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (g1, g2) = theme.colors(palette)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), g1, g2, Shader.TileMode.CLAMP)
        })
        // Light backgrounds (e.g. a Lemon bubble) get dark text.
        val light = ColorUtils.calculateLuminance(ColorUtils.blendARGB(g1, g2, 0.5f)) > 0.55
        val ink = if (light) 0xFF1A1530.toInt() else 0xFFFFFFFF.toInt()
        val inkSoft = ColorUtils.setAlphaComponent(ink, 0xD0)

        // Depth: soft circles, glowing ones for Neon.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        if (theme == ShareTheme.NEON) {
            glow.color = 0x5500F0FF
            glow.setShadowLayer(120f, 0f, 0f, 0xFF00F0FF.toInt())
            c.drawCircle(W * 0.95f, H * 0.06f, 200f, glow)
            glow.color = 0x55FF2BD6
            glow.setShadowLayer(140f, 0f, 0f, 0xFFFF2BD6.toInt())
            c.drawCircle(W * 0.02f, H * 0.97f, 260f, glow)
            glow.clearShadowLayer()
        } else {
            glow.color = ColorUtils.setAlphaComponent(ink, 0x22)
            c.drawCircle(W * 0.92f, H * 0.08f, 260f, glow)
            c.drawCircle(W * 0.05f, H * 0.95f, 320f, glow)
        }

        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun text(t: String, x: Float, y: Float, size: Float, color: Int = ink, bold: Boolean = false, max: Float = W - 2 * LEFT) {
            p.color = color
            p.textSize = size
            p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            c.drawText(TextUtils.ellipsize(t, p, max, TextUtils.TruncateAt.END).toString(), x, y, p)
        }
        fun icon(app: AppEntry, x: Float, y: Float, size: Int) {
            app.icon.setBounds(x.toInt(), y.toInt(), x.toInt() + size, y.toInt() + size)
            app.icon.alpha = 255
            app.icon.draw(c)
        }

        // Header: title, and the character top right.
        BubbleRenderer().apply {
            this.character = character
            this.palette = palette
            this.customImage = customImage
            this.accessory = accessory
            mood = BubbleRenderer.Mood.HAPPY
        }.draw(c, W - 170f, 175f, 95f)
        text(context.getString(if (s.days == 7) R.string.recap_card_week else R.string.recap_card_month), LEFT, 170f, 52f, bold = true, max = W - 340f)
        text("Halo", LEFT, 230f, 36f, inkSoft)

        when (layout) {
            ShareLayout.SUMMARY -> {
                text("${s.total}", LEFT, 470f, 230f, bold = true)
                text(context.getString(R.string.recap_total_unit), LEFT, 545f, 56f, bold = true)
                text(context.getString(R.string.recap_top_title), LEFT, 690f, 40f, inkSoft, bold = true)
                var y = 730f
                s.topApps.take(3).forEachIndexed { i, (key, count) ->
                    val app = entries[key] ?: return@forEachIndexed
                    icon(app, LEFT, y, 96)
                    text("${i + 1}. ${app.label}", LEFT + 128f, y + 62f, 46f, bold = i == 0, max = W - LEFT * 2 - 96 - 220f)
                    text("$count", W - LEFT - 140f, y + 62f, 46f, bold = true, max = 140f)
                    y += 124f
                }
                personas(context, s, c, ink) { t, x, y, size, max -> text(t, x, y, size, bold = true, max = max) }
            }

            ShareLayout.TOP_APPS -> {
                text(context.getString(R.string.recap_top_title), LEFT, 380f, 64f, bold = true)
                val max = (s.topApps.firstOrNull()?.second ?: 1).coerceAtLeast(1)
                val bar = Paint(Paint.ANTI_ALIAS_FLAG)
                var y = 460f
                s.topApps.take(5).forEachIndexed { i, (key, count) ->
                    val app = entries[key] ?: return@forEachIndexed
                    val size = if (i == 0) 128 else 104
                    icon(app, LEFT, y, size)
                    val tx = LEFT + size + 36f
                    text(app.label, tx, y + size * 0.45f, if (i == 0) 52f else 44f, bold = true, max = W - tx - LEFT - 120f)
                    text("$count", W - LEFT - 110f, y + size * 0.45f, if (i == 0) 52f else 44f, bold = true, max = 110f)
                    bar.color = ColorUtils.setAlphaComponent(ink, 0x40)
                    val bw = W - tx - LEFT
                    c.drawRoundRect(RectF(tx, y + size * 0.62f, tx + bw, y + size * 0.62f + 16f), 8f, 8f, bar)
                    bar.color = ink
                    c.drawRoundRect(RectF(tx, y + size * 0.62f, tx + bw * count / max, y + size * 0.62f + 16f), 8f, 8f, bar)
                    y += size + 44f
                }
            }

            ShareLayout.DUO -> {
                text(context.getString(R.string.recap_duo_title), LEFT, 400f, 72f, bold = true)
                val pair = s.topPair
                val a = pair?.let { entries[it.first] }
                val b = pair?.let { entries[it.second] }
                if (a != null && b != null) {
                    val size = 260
                    icon(a, W / 2f - size - 70f, 520f, size)
                    icon(b, W / 2f + 70f, 520f, size)
                    p.textAlign = Paint.Align.CENTER
                    text("⇄", W / 2f, 520f + size / 2f + 40f, 110f, bold = true)
                    text("${a.label} + ${b.label}", W / 2f, 940f, 56f, bold = true)
                    text(
                        context.resources.getQuantityString(R.plurals.recap_duo_count, pair.third, pair.third),
                        W / 2f, 1015f, 42f, inkSoft,
                    )
                    p.textAlign = Paint.Align.LEFT
                } else {
                    text(context.getString(R.string.recap_duo_none), LEFT, 560f, 40f, inkSoft)
                }
            }

            ShareLayout.RHYTHM -> {
                val (res, emoji) = rhythmRes(s.peakHour)
                text(emoji, LEFT, 470f, 150f)
                text(context.getString(res), LEFT, 600f, 88f, bold = true)
                text(context.getString(R.string.recap_peak_hour, hourLabel(context, s.peakHour)), LEFT, 675f, 44f, inkSoft)
                // 24-hour chart.
                val max = (s.hours.maxOrNull() ?: 1).coerceAtLeast(1)
                val top = 760f
                val h = 380f
                val gap = 10f
                val bw = (W - LEFT * 2 - gap * 23) / 24
                val bar = Paint(Paint.ANTI_ALIAS_FLAG)
                s.hours.forEachIndexed { i, v ->
                    val bh = (h * v / max).coerceAtLeast(8f)
                    bar.color = if (i == s.peakHour) 0xFFFFD54F.toInt() else ColorUtils.setAlphaComponent(ink, 0x80)
                    val x = LEFT + i * (bw + gap)
                    c.drawRoundRect(RectF(x, top + h - bh, x + bw, top + h), bw / 3, bw / 3, bar)
                }
                listOf(0, 6, 12, 18).forEach { hr ->
                    text(hourLabel(context, hr), LEFT + hr * (bw + gap), top + h + 50f, 30f, inkSoft, max = 200f)
                }
            }
        }

        // Credit.
        text(context.getString(R.string.recap_card_credit), LEFT, H - 70f, 32f, inkSoft)
        return bmp
    }

    /** Rhythm and style personas in two pills near the bottom. */
    private fun personas(
        context: Context,
        s: Stats.Summary,
        c: Canvas,
        ink: Int,
        text: (String, Float, Float, Float, Float) -> Unit,
    ) {
        val (rhythm, rEmoji) = rhythmRes(s.peakHour)
        val (style, sEmoji) = styleRes(s.kinds)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(ink, 0x33) }
        val rowY = 1130f
        val boxW = (W - LEFT * 2 - 30f) / 2
        listOf(rEmoji to context.getString(rhythm), sEmoji to context.getString(style)).forEachIndexed { i, (e, label) ->
            val x = LEFT + i * (boxW + 30f)
            c.drawRoundRect(RectF(x, rowY - 70f, x + boxW, rowY + 40f), 36f, 36f, pill)
            text("$e $label", x + 30f, rowY + 2f, 40f, boxW - 60f)
        }
    }
}
