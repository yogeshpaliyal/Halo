package com.yogeshpaliyal.halo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R
import kotlin.math.roundToInt

/** Shape of app icons in the dial and in Halo's own lists. */
enum class IconShape(@StringRes val labelRes: Int) {
    /** Whatever shape the phone gives its icons (squircle, rounded square, circle...). */
    SYSTEM(R.string.icon_shape_system),
    /** Every icon cut to a circle, as on Pixel phones, whatever the phone's own shape. */
    CIRCLE(R.string.icon_shape_circle),
}

object IconShapes {
    /** Big enough for the dial's highlighted (1.3×) icon on the densest screens. */
    private const val SIZE_DP = 64f

    /**
     * Redraws [icon] as a circle. Adaptive icons are drawn from their own layers, so the
     * phone's mask (a squircle on many non-Pixel phones) never applies; any other icon is
     * clipped to the largest circle that fits.
     */
    fun circle(context: Context, icon: Drawable): Drawable {
        val size = (SIZE_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val clip = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        canvas.clipPath(clip)
        if (icon is AdaptiveIconDrawable) {
            // Layers are 108 units with the middle 72 visible: draw them 1.5× the icon's size.
            val inset = (size * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
            listOfNotNull(icon.background, icon.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
        } else {
            val old = icon.copyBounds()
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
            icon.bounds = old
        }
        return BitmapDrawable(context.resources, bitmap)
    }
}
