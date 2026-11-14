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

    private fun file(context: Context) = File(context.filesDir, "avatar.png")

    fun exists(context: Context): Boolean = file(context).exists()

    /** Loads the saved avatar, or null if none. Small enough to read on the main thread. */
    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /**
     * Copies [uri] from the photo picker: centre-cropped to a square and scaled down.
     * Call off the main thread. Returns false if the image couldn't be read.
     */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val source = decode(context, uri) ?: return false
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
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
                if (shortSide > SIZE * 2) {
                    val k = SIZE * 2f / shortSide
                    decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
}
