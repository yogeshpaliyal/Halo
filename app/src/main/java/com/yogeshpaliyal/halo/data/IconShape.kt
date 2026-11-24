package com.yogeshpaliyal.halo.data

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Shape of app icons in the dial and in Halo's own lists. Apps can't read the shape picked
 * in a launcher's settings, so these mirror the common launcher choices instead.
 */
enum class IconShape(@StringRes val labelRes: Int) {
    /** The shape Android gives icons on this phone (squircle, rounded square, circle...). */
    SYSTEM(R.string.icon_shape_system),
    CIRCLE(R.string.icon_shape_circle),
    SQUIRCLE(R.string.icon_shape_squircle),
    ROUNDED_SQUARE(R.string.icon_shape_rounded),
    TEARDROP(R.string.icon_shape_teardrop),
}

/** Everything that decides how an icon is drawn; part of the icon cache key. */
data class IconStyle(val shape: IconShape, val themed: Boolean, val night: Boolean) {
    /** Nothing to redraw: use the icon exactly as the phone gives it. */
    val isDefault: Boolean get() = shape == IconShape.SYSTEM && !themed
    val cacheKey: String get() = "${shape.name}${if (themed) (if (night) "-tn" else "-t") else ""}"
}

object IconShapes {
    /** Big enough for the dial's highlighted (1.3×) icon on the densest screens. */
    private const val SIZE_DP = 64f

    /** Themed icons use the monochrome layer apps added for Android 13. */
    val themedSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun style(context: Context): IconStyle {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return IconStyle(Prefs.iconShape(context), Prefs.themedIcons(context) && themedSupported, night)
    }

    /**
     * Redraws [icon] in [style]. Adaptive icons are drawn from their own layers, so the phone's
     * mask never applies; any other icon is clipped to the shape. Themed icons draw the app's
     * monochrome layer in wallpaper colours; apps without one keep their normal icon.
     */
    fun apply(context: Context, icon: Drawable, style: IconStyle): Drawable {
        if (style.isDefault) return icon
        val size = (SIZE_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(48)
        val adaptive = icon as? AdaptiveIconDrawable
        val path = shapePath(style.shape, size.toFloat(), adaptive)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (path != null) canvas.clipPath(path)
        // Layers are 108 units with the middle 72 visible: draw them 1.5× the icon's size.
        val inset = (size * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
        val mono = if (style.themed && adaptive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adaptive.monochrome
        } else {
            null
        }
        when {
            mono != null -> {
                val (bg, fg) = themedColors(context, style.night)
                canvas.drawPaint(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
                val layer = mono.mutate()
                layer.setTint(fg)
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
            adaptive != null -> listOfNotNull(adaptive.background, adaptive.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
            // A plain icon on the phone's own shape: nothing to change.
            style.shape == IconShape.SYSTEM -> return icon
            else -> {
                val old = icon.copyBounds()
                icon.setBounds(0, 0, size, size)
                icon.draw(canvas)
                icon.bounds = old
            }
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    /** Outline of [shape] in a [size] square; null means don't clip. */
    private fun shapePath(shape: IconShape, size: Float, adaptive: AdaptiveIconDrawable?): Path? {
        val r = size / 2f
        return when (shape) {
            IconShape.SYSTEM -> adaptive?.let {
                // The phone's mask, scaled to the bounds it was last given.
                it.setBounds(0, 0, size.toInt(), size.toInt())
                Path(it.iconMask)
            }
            IconShape.CIRCLE -> Path().apply { addCircle(r, r, r, Path.Direction.CW) }
            IconShape.ROUNDED_SQUARE -> Path().apply {
                addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
            }
            IconShape.TEARDROP -> Path().apply {
                val big = r
                val small = size * 0.12f
                // Round everywhere except a tighter bottom-right corner.
                addRoundRect(
                    RectF(0f, 0f, size, size),
                    floatArrayOf(big, big, big, big, small, small, big, big),
                    Path.Direction.CW,
                )
            }
            IconShape.SQUIRCLE -> Path().apply {
                // Superellipse |x|^4 + |y|^4 = r^4.
                val steps = 96
                for (i in 0..steps) {
                    val t = 2.0 * Math.PI * i / steps
                    val c = cos(t)
                    val s = sin(t)
                    val x = r + r * (sign(c) * abs(c).pow(0.5)).toFloat()
                    val y = r + r * (sign(s) * abs(s).pow(0.5)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }

    /** Background and foreground of a themed icon, as Pixel's launcher picks them. */
    private fun themedColors(context: Context, night: Boolean): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0xFFE8DEF8.toInt() to 0xFF4A4458.toInt()
        val bg = if (night) android.R.color.system_neutral1_800 else android.R.color.system_accent1_100
        val fg = if (night) android.R.color.system_accent1_100 else android.R.color.system_neutral2_700
        return context.getColor(bg) to context.getColor(fg)
    }
}
