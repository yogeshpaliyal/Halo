package com.yogeshpaliyal.halo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small settings store. Favourites are kept as an ordered, comma-separated package list. */
object Prefs {
    const val MAX_FAVORITES = 6
    const val MAX_RECENTS = 8

    private const val FILE = "halo"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_BUBBLE_RIGHT = "bubble_right"
    private const val KEY_BUBBLE_Y = "bubble_y"
    private const val KEY_ENABLED = "enabled"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favorites(context: Context): List<String> =
        sp(context).getString(KEY_FAVORITES, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }

    fun setFavorites(context: Context, packages: List<String>) = sp(context).edit {
        putString(KEY_FAVORITES, packages.take(MAX_FAVORITES).joinToString(","))
    }

    fun toggleFavorite(context: Context, pkg: String): List<String> {
        val current = favorites(context).toMutableList()
        if (!current.remove(pkg) && current.size < MAX_FAVORITES) current += pkg
        setFavorites(context, current)
        return current
    }

    fun bubbleOnRight(context: Context): Boolean = sp(context).getBoolean(KEY_BUBBLE_RIGHT, true)
    fun bubbleY(context: Context, default: Int): Int = sp(context).getInt(KEY_BUBBLE_Y, default)

    fun saveBubble(context: Context, onRight: Boolean, y: Int) = sp(context).edit {
        putBoolean(KEY_BUBBLE_RIGHT, onRight)
        putInt(KEY_BUBBLE_Y, y)
    }

    fun enabled(context: Context): Boolean = sp(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = sp(context).edit { putBoolean(KEY_ENABLED, enabled) }
}
