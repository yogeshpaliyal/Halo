package com.yogeshpaliyal.halo.data

import android.content.Context
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small settings store. Favourites are kept as an ordered, comma-separated package list. */
object Prefs {
    const val MAX_FAVORITES = 6
    const val MAX_RECENTS = 8

    private const val FILE = "halo"
    const val KEY_FAVORITES = "favorites"
    private const val KEY_BUBBLE_RIGHT = "bubble_right"
    private const val KEY_BUBBLE_Y = "bubble_y"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TAP_ACTION = "tap_action"
    private const val KEY_DOUBLE_TAP_ACTION = "double_tap_action"
    private const val KEY_HOLD_DELAY = "hold_delay"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_HIDDEN_RECENTS = "hidden_recents"
    const val KEY_HIDE_FULLSCREEN = "hide_fullscreen"
    const val KEY_BUBBLE_OPACITY = "bubble_opacity"
    const val KEY_BUBBLE_SIZE = "bubble_size"
    const val KEY_HIDE_IN_APPS = "hide_in_apps"
    const val KEY_NOTIFICATION_DOTS = "notification_dots"
    const val KEY_FOLDERS = "folders"
    const val KEY_BUBBLE_PALETTE = "bubble_palette"
    const val KEY_BUBBLE_CHARACTER = "bubble_character"
    const val KEY_BUBBLE_ANIMATIONS = "bubble_animations"

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

    fun foldersJson(context: Context): String? = sp(context).getString(KEY_FOLDERS, null)
    fun setFoldersJson(context: Context, json: String) = sp(context).edit { putString(KEY_FOLDERS, json) }

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

    fun bubblePalette(context: Context): BubblePalette =
        sp(context).getString(KEY_BUBBLE_PALETTE, null)
            ?.let { runCatching { BubblePalette.valueOf(it) }.getOrNull() }
            ?: BubblePalette.MIDNIGHT

    fun setBubblePalette(context: Context, palette: BubblePalette) =
        sp(context).edit { putString(KEY_BUBBLE_PALETTE, palette.name) }

    fun bubbleCharacter(context: Context): BubbleCharacter =
        sp(context).getString(KEY_BUBBLE_CHARACTER, null)
            ?.let { runCatching { BubbleCharacter.valueOf(it) }.getOrNull() }
            ?: BubbleCharacter.CLASSIC

    fun setBubbleCharacter(context: Context, character: BubbleCharacter) =
        sp(context).edit { putString(KEY_BUBBLE_CHARACTER, character.name) }

    /** Blinks and idle moves for characters. Touch reactions always play. */
    fun bubbleAnimations(context: Context): Boolean = sp(context).getBoolean(KEY_BUBBLE_ANIMATIONS, true)
    fun setBubbleAnimations(context: Context, on: Boolean) = sp(context).edit { putBoolean(KEY_BUBBLE_ANIMATIONS, on) }

    /** Packages where the bubble hides itself while they're on screen. */
    fun hideInApps(context: Context): Set<String> =
        sp(context).getStringSet(KEY_HIDE_IN_APPS, emptySet()).orEmpty()

    fun toggleHideInApp(context: Context, pkg: String): Set<String> {
        val next = hideInApps(context).toMutableSet().apply { if (!remove(pkg)) add(pkg) }
        sp(context).edit { putStringSet(KEY_HIDE_IN_APPS, next) }
        return next
    }

    /** Show dots for apps with notifications. Off by default: it needs Notification access. */
    fun notificationDots(context: Context): Boolean = sp(context).getBoolean(KEY_NOTIFICATION_DOTS, false)
    fun setNotificationDots(context: Context, on: Boolean) = sp(context).edit { putBoolean(KEY_NOTIFICATION_DOTS, on) }

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

    /** Defaults to the app before last, so tap and double tap reach your two latest apps. */
    fun doubleTapAction(context: Context): TapAction =
        sp(context).getString(KEY_DOUBLE_TAP_ACTION, null)
            ?.let { runCatching { TapAction.valueOf(it) }.getOrNull() }
            ?: TapAction.SECOND_APP

    fun setDoubleTapAction(context: Context, action: TapAction) =
        sp(context).edit { putString(KEY_DOUBLE_TAP_ACTION, action.name) }

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
