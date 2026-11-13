package com.yogeshpaliyal.halo.data

import com.yogeshpaliyal.halo.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * An optional group of apps that takes one favourite slot. In the menu, resting on it fans
 * its apps out on the outer ring. Stored as JSON; favourites refer to it as "folder:<id>".
 */
data class Folder(val id: String, val name: String, val apps: List<String>) {
    val key: String get() = Folders.PREFIX + id
}

object Folders {
    const val PREFIX = "folder:"
    /** As many as fit side by side on the outer ring. */
    const val MAX_APPS = 8

    fun isFolderKey(key: String) = key.startsWith(PREFIX)

    fun newFolder() = Folder(UUID.randomUUID().toString().take(8), "", emptyList())

    fun all(context: Context): List<Folder> {
        val json = Prefs.foldersJson(context) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val apps = o.optJSONArray("apps") ?: JSONArray()
                Folder(o.getString("id"), o.optString("name"), (0 until apps.length()).map { apps.getString(it) })
            }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, key: String): Folder? =
        if (!isFolderKey(key)) null else all(context).firstOrNull { it.key == key }

    private fun write(context: Context, folders: List<Folder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
        Prefs.setFoldersJson(context, arr.toString())
    }

    /**
     * Saves [folder] (new or edited). A new folder is added to favourites if there's room.
     * Returns false when it couldn't be added because favourites are full.
     */
    fun save(context: Context, folder: Folder): Boolean {
        val clean = folder.copy(
            name = folder.name.trim().ifEmpty { context.getString(R.string.folder_default_name) },
            apps = folder.apps.filterNot(::isFolderKey).distinct().take(MAX_APPS),
        )
        val list = all(context).toMutableList()
        val i = list.indexOfFirst { it.id == clean.id }
        if (i >= 0) list[i] = clean else list += clean
        write(context, list)
        val favs = Prefs.favorites(context)
        if (clean.key in favs) {
            Prefs.setFavorites(context, favs) // pings listeners so the bubble's dot updates
            return true
        }
        if (favs.size >= Prefs.MAX_FAVORITES) return false
        Prefs.setFavorites(context, favs + clean.key)
        return true
    }

    /** Deletes the folder and its favourite slot. The apps themselves are untouched. */
    fun delete(context: Context, key: String) {
        write(context, all(context).filterNot { it.key == key })
        Prefs.setFavorites(context, Prefs.favorites(context) - key)
    }

    /** Favourite keys with folders swapped for the apps inside them. */
    fun expand(context: Context, keys: List<String>): Set<String> {
        val folders = all(context).associateBy { it.key }
        return keys.flatMapTo(HashSet()) { k -> folders[k]?.apps ?: listOf(k) }
    }
}

/** A folder's icon: up to four of its apps in a 2×2 grid on a round tile. */
class FolderIcon(private val apps: List<Drawable>) : Drawable() {
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3556.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
    }
    private var alpha = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = b.width().coerceAtMost(b.height()).toFloat()
        if (size <= 0f) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        tile.alpha = alpha * 0xFF / 255
        rim.alpha = alpha * 0x66 / 255
        rim.strokeWidth = size * 0.02f
        canvas.drawCircle(cx, cy, size / 2f, tile)
        canvas.drawCircle(cx, cy, size / 2f - rim.strokeWidth, rim)
        val cell = size * 0.30f
        val gap = size * 0.04f
        val shown = apps.take(4)
        shown.forEachIndexed { i, d ->
            val col = i % 2
            val row = i / 2
            // A single app is centred; with two or three the bottom row is centred.
            val rowCount = if (row == 0) shown.size.coerceAtMost(2) else shown.size - 2
            val rows = if (shown.size > 2) 2 else 1
            val left = cx - (rowCount * cell + (rowCount - 1) * gap) / 2f + col * (cell + gap)
            val top = cy - (rows * cell + (rows - 1) * gap) / 2f + row * (cell + gap)
            d.setBounds(left.toInt(), top.toInt(), (left + cell).toInt(), (top + cell).toInt())
            d.alpha = alpha
            d.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getIntrinsicWidth() = 192
    override fun getIntrinsicHeight() = 192
}
