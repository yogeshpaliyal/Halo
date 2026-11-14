package com.yogeshpaliyal.halo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.Stats
import com.yogeshpaliyal.halo.overlay.Accessory
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import com.yogeshpaliyal.halo.overlay.BubbleRenderer

/**
 * The shareable recap image (1080 × 1350, the 4:5 size most apps show in full): your
 * character, the total, top three apps, rhythm and style, and a small Halo credit.
 */
object RecapShareCard {
    private const val W = 1080
    private const val H = 1350

    fun render(
        context: Context,
        s: Stats.Summary,
        entries: Map<String, AppEntry>,
        character: BubbleCharacter,
        palette: BubblePalette,
        customImage: Bitmap?,
        accessory: Accessory,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (g1, g2) = RECAP_GRADIENTS[0]
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), g1.toInt(), g2.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Soft glow circles for depth.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        c.drawCircle(W * 0.92f, H * 0.08f, 260f, glow)
        c.drawCircle(W * 0.05f, H * 0.95f, 320f, glow)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
        fun text(t: String, x: Float, y: Float, size: Float, p: Paint, bold: Boolean = false, max: Float = W - 2 * 80f) {
            p.textSize = size
            p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            val fit = TextUtils.ellipsize(t, android.text.TextPaint(p), max, TextUtils.TruncateAt.END).toString()
            c.drawText(fit, x, y, p)
        }

        val left = 80f
        // Header: character and title.
        BubbleRenderer().apply {
            this.character = character
            this.palette = palette
            this.customImage = customImage
            this.accessory = accessory
            mood = BubbleRenderer.Mood.HAPPY
        }.draw(c, W - 170f, 175f, 95f)
        text(context.getString(if (s.days == 7) R.string.recap_card_week else R.string.recap_card_month), left, 170f, 52f, white, bold = true, max = W - 340f)
        text("Halo", left, 230f, 36f, soft)

        // Big total.
        text("${s.total}", left, 470f, 230f, white, bold = true)
        text(context.getString(R.string.recap_total_unit), left, 545f, 56f, white, bold = true)

        // Top three apps.
        text(context.getString(R.string.recap_top_title), left, 690f, 40f, soft, bold = true)
        var y = 730f
        s.topApps.take(3).forEachIndexed { i, (key, count) ->
            val app = entries[key] ?: return@forEachIndexed
            val size = 96
            app.icon.setBounds(left.toInt(), y.toInt(), left.toInt() + size, y.toInt() + size)
            app.icon.alpha = 255
            app.icon.draw(c)
            text("${i + 1}. ${app.label}", left + size + 32f, y + 62f, 46f, white, bold = i == 0, max = W - left * 2 - size - 220f)
            text("$count", W - left - 140f, y + 62f, 46f, white, bold = true, max = 140f)
            y += size + 28f
        }

        // Rhythm and style.
        val (rhythm, rEmoji) = rhythmRes(s.peakHour)
        val (style, sEmoji) = styleRes(s.kinds)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        val rowY = 1130f
        val boxW = (W - left * 2 - 30f) / 2
        listOf(rEmoji to context.getString(rhythm), sEmoji to context.getString(style)).forEachIndexed { i, (e, label) ->
            val x = left + i * (boxW + 30f)
            c.drawRoundRect(RectF(x, rowY - 70f, x + boxW, rowY + 40f), 36f, 36f, pill)
            text("$e $label", x + 30f, rowY + 2f, 40f, white, bold = true, max = boxW - 60f)
        }

        // Credit.
        text(context.getString(R.string.recap_card_credit), left, H - 70f, 32f, soft)
        return bmp
    }
}
