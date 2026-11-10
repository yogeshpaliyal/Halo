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
    private const val KEY_HIDDEN_RECENTS = "hidden_recents"
    const val KEY_HIDE_FULLSCREEN = "hide_fullscreen"
    const val KEY_BUBBLE_OPACITY = "bubble_opacity"
    const val KEY_BUBBLE_SIZE = "bubble_size"
    const val KEY_HIDE_IN_APPS = "hide_in_apps"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favorites(context: Context): List<String> =
        sp(context).getString(KEY_FAVORITES, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }

    fun setFavorites(context: Context, packages: List<String>) = sp(context).edit {
        putString(KEY_FAVORITES, packages.take(MAX_FAVORITES).joinToString(","))
    }

    /** [key] is [AppEntry.key]: the package name, plus "@profile" for work apps, or "action:…" for quick actions. */
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

    /** Packages the user never wants in the recent-apps ring. */
    fun hiddenRecents(context: Context): Set<String> =
        sp(context).getStringSet(KEY_HIDDEN_RECENTS, emptySet()).orEmpty()

    fun toggleHiddenRecent(context: Context, pkg: String): Set<String> {
        val next = hiddenRecents(context).toMutableSet().apply { if (!remove(pkg)) add(pkg) }
        sp(context).edit { putStringSet(KEY_HIDDEN_RECENTS, next) }
        return next
    }

    /** Hide the bubble while another app is full screen (games, video, OTT). On by default. */
    fun hideInFullscreen(context: Context): Boolean = sp(context).getBoolean(KEY_HIDE_FULLSCREEN, true)
    fun setHideInFullscreen(context: Context, on: Boolean) = sp(context).edit { putBoolean(KEY_HIDE_FULLSCREEN, on) }

    /** Resting opacity of the bubble, 0.3 to 1.0. The menu and the held bubble stay fully opaque. */
    fun bubbleOpacity(context: Context): Float =
        sp(context).getFloat(KEY_BUBBLE_OPACITY, 0.8f).coerceIn(0.3f, 1f)

    fun setBubbleOpacity(context: Context, value: Float) =
        sp(context).edit { putFloat(KEY_BUBBLE_OPACITY, value.coerceIn(0.3f, 1f)) }

    fun bubbleSize(context: Context): BubbleSize =
        sp(context).getString(KEY_BUBBLE_SIZE, null)
            ?.let { runCatching { BubbleSize.valueOf(it) }.getOrNull() }
            ?: BubbleSize.MEDIUM

    fun setBubbleSize(context: Context, size: BubbleSize) =
        sp(context).edit { putString(KEY_BUBBLE_SIZE, size.name) }

    /** Packages where the bubble hides itself while they're on screen. */
    fun hideInApps(context: Context): Set<String> =
        sp(context).getStringSet(KEY_HIDE_IN_APPS, emptySet()).orEmpty()

    fun toggleHideInApp(context: Context, pkg: String): Set<String> {
        val next = hideInApps(context).toMutableSet().apply { if (!remove(pkg)) add(pkg) }
        sp(context).edit { putStringSet(KEY_HIDE_IN_APPS, next) }
        return next
    }

    fun registerListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp(context).registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp(context).unregisterOnSharedPreferenceChangeListener(listener)

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
