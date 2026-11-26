package com.yogeshpaliyal.halo.overlay

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Screen brightness and media volume for the sliders around the dial. Values are 0..1.
 *
 * Brightness needs "Modify system settings" (WRITE_SETTINGS), which the user grants in
 * Settings. Volume needs no permission, but newer Android versions (and some OEM builds, such
 * as OxygenOS on Android 16) silently ignore volume changes from an app that has no visible
 * activity and whose foreground service lacks "while in use" access. That is the case once
 * Halo's task is swiped away and the system restarts the service. [setVolumeIndex] reads the
 * volume back so the caller can tell, and [AudioAccessActivity] gets the access back.
 */
object SystemControls {

    private const val MAX_BRIGHTNESS = 255

    /** Media volume (0..1) from before the dial's mute button was used, to bring it back. */
    var volumeBeforeMute = 0f

    fun canWriteBrightness(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Slider position for the current brightness. Squared curve, so low light gets more travel. */
    fun brightness(context: Context): Float {
        val level = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(MAX_BRIGHTNESS / 2)
        return sqrt(((level - 1).coerceAtLeast(0)) / (MAX_BRIGHTNESS - 1f)).coerceIn(0f, 1f)
    }

    /** Brightness setting (1..255) for a slider position. */
    fun brightnessLevel(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return (1 + (MAX_BRIGHTNESS - 1) * v * v).roundToInt()
    }

    /**
     * Sets the brightness and switches adaptive brightness off, as it would otherwise undo the
     * change straight away. Returns false without access.
     */
    fun setBrightnessLevel(context: Context, level: Int): Boolean {
        if (!canWriteBrightness(context)) return false
        return runCatching {
            val cr = context.contentResolver
            val mode = Settings.System.getInt(
                cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            if (mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Settings.System.putInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(1, MAX_BRIGHTNESS))
        }.getOrDefault(false)
    }

    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)

    private fun minVolume(am: AudioManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0

    /** Media volume as 0..1. */
    fun volume(context: Context): Float {
        val am = audio(context) ?: return 0f
        val min = minVolume(am)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= min) return 0f
        return ((am.getStreamVolume(AudioManager.STREAM_MUSIC) - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /** Media volume step for a slider position. */
    fun volumeIndex(context: Context, value: Float): Int {
        val am = audio(context) ?: return 0
        val min = minVolume(am)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (min + (max - min) * value.coerceIn(0f, 1f)).roundToInt()
    }

    /**
     * Sets media volume quietly (no system volume panel on top of the dial). Returns false when
     * the system ignored the change (see the class comment), so the caller can win access back.
     */
    fun setVolumeIndex(context: Context, index: Int): Boolean {
        val am = audio(context) ?: return false
        // Fixed-volume devices (some TVs, HDMI out) never take a change; nothing to fix there.
        if (am.isVolumeFixed) return true
        val result = runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
        result.exceptionOrNull()?.let {
            Log.w(TAG, "setStreamVolume($index) failed", it)
            return false
        }
        val now = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: return true
        if (now != index) {
            Log.w(TAG, "setStreamVolume($index) was ignored by the system, volume is still $now")
            return false
        }
        return true
    }

    private const val TAG = "HaloVolume"

}
