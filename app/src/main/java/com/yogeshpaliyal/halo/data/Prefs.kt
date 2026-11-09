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
    private const val KEY_TAP_ACTION = "tap_action"
    private const val KEY_HOLD_DELAY = "hold_delay"
    private const val KEY_HAPTICS = "haptics"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favorites(context: Context): List<String> =
        sp(context).getString(KEY_FAVORITES, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }

    fun setFavorites(context: Context, packages: List<String>) = sp(context).edit {
        putString(KEY_FAVORITES, packages.take(MAX_FAVORITES).joinToString(","))
    }

    /** [key] is [AppEntry.key]: the package name, plus "@profile" for work apps. */
    fun toggleFavorite(context: Context, key: String): List<String> {
        val current = favorites(context).toMutableList()
        if (!current.remove(key) && current.size < MAX_FAVORITES) current += key
        setFavorites(context, current)
        return current
    }

    /** Moves the favourite at [from] by [delta] slots (negative = earlier / higher on the ring). */
    fun moveFavorite(context: Context, from: Int, delta: Int): List<String> {
        val current = favorites(context).toMutableList()
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (from in current.indices && to != from) current.add(to, current.removeAt(from))
        setFavorites(context, current)
        return current
    }

    fun bubbleOnRight(context: Context): Boolean = sp(context).getBoolean(KEY_BUBBLE_RIGHT, true)
    fun bubbleY(context: Context, default: Int): Int = sp(context).getInt(KEY_BUBBLE_Y, default)

    fun saveBubble(context: Context, onRight: Boolean, y: Int) = sp(context).edit {
        putBoolean(KEY_BUBBLE_RIGHT, onRight)
        putInt(KEY_BUBBLE_Y, y)
    }

    fun tapAction(context: Context): TapAction =
        sp(context).getString(KEY_TAP_ACTION, null)
            ?.let { runCatching { TapAction.valueOf(it) }.getOrNull() }
            ?: TapAction.PREVIOUS_APP

    fun setTapAction(context: Context, action: TapAction) =
        sp(context).edit { putString(KEY_TAP_ACTION, action.name) }

    fun holdDelay(context: Context): HoldDelay =
        sp(context).getString(KEY_HOLD_DELAY, null)
            ?.let { runCatching { HoldDelay.valueOf(it) }.getOrNull() }
            ?: HoldDelay.MEDIUM

    fun setHoldDelay(context: Context, delay: HoldDelay) =
        sp(context).edit { putString(KEY_HOLD_DELAY, delay.name) }

    fun haptics(context: Context): Boolean = sp(context).getBoolean(KEY_HAPTICS, true)
    fun setHaptics(context: Context, on: Boolean) = sp(context).edit { putBoolean(KEY_HAPTICS, on) }

    fun enabled(context: Context): Boolean = sp(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = sp(context).edit { putBoolean(KEY_ENABLED, enabled) }
}
