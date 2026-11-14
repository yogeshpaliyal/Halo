package com.yogeshpaliyal.halo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * The photo for the "Your photo" bubble. A small square copy (256 px) is kept in Halo's
 * private storage; the original is never touched and nothing leaves the phone.
 */
object CustomAvatar {
    private const val SIZE = 256
    /** Enough detail to zoom in on while cropping. */
    private const val MAX_CROP = 1600

    private fun file(context: Context) = File(context.filesDir, "avatar.png")

    fun exists(context: Context): Boolean = file(context).exists()

    /** Loads the saved avatar, or null if none. Small enough to read on the main thread. */
    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /**
     * Reads the picked photo for the crop screen, upright and at most ~2048 px on its short
     * side. Call off the main thread; null if it can't be read.
     */
    fun decodeForCrop(context: Context, uri: Uri): Bitmap? = runCatching { decode(context, uri) }.getOrNull()

    /**
     * Saves the square [side]×[side] area of [source] starting at ([left], [top]) as the
     * avatar, scaled to 256 px. Call off the main thread.
     */
    fun saveCrop(context: Context, source: Bitmap, left: Int, top: Int, side: Int): Boolean = runCatching {
        val s = side.coerceIn(1, minOf(source.width, source.height))
        val l = left.coerceIn(0, source.width - s)
        val t = top.coerceIn(0, source.height - s)
        val square = Bitmap.createBitmap(source, l, t, s, s)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        file(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Prefs.bumpAvatarVersion(context)
        true
    }.getOrDefault(false)

    private fun decode(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Handles rotation from EXIF, and downsamples big photos while decoding.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortSide = minOf(info.size.width, info.size.height)
                if (shortSide > MAX_CROP) {
                    val k = MAX_CROP.toFloat() / shortSide
                    decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_CROP) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
}
