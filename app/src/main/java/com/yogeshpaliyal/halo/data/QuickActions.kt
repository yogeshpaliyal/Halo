package com.yogeshpaliyal.halo.data

import androidx.annotation.StringRes
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.yogeshpaliyal.halo.R

/**
 * Non-app items that can sit in the favourites ring. None of them needs a permission:
 * the torch API doesn't require camera access, the volume panel and media keys are plain
 * AudioManager calls.
 */
enum class QuickAction(val key: String, @StringRes val labelRes: Int) {
    FLASHLIGHT("action:flashlight", R.string.action_flashlight),
    VOLUME("action:volume", R.string.action_volume),
    PLAY_PAUSE("action:play_pause", R.string.action_play_pause),
    ;

    companion object {
        const val PREFIX = "action:"
        fun fromKey(key: String): QuickAction? = entries.firstOrNull { it.key == key }
    }
}

object QuickActions {
    private var torchCameraId: String? = null
    private var callbackRegistered = false

    /** Current torch state, kept in sync by the system callback (also catches Quick Settings changes). */
    @Volatile
    var torchOn = false
        private set

    fun iconRes(action: QuickAction): Int = when (action) {
        QuickAction.FLASHLIGHT -> if (torchOn) R.drawable.ic_action_flashlight_on else R.drawable.ic_action_flashlight
        QuickAction.VOLUME -> R.drawable.ic_action_volume
        QuickAction.PLAY_PAUSE -> R.drawable.ic_action_play_pause
    }

    fun isAvailable(context: Context, action: QuickAction): Boolean =
        action != QuickAction.FLASHLIGHT || torchCamera(context) != null

    fun perform(context: Context, action: QuickAction): Boolean = when (action) {
        QuickAction.FLASHLIGHT -> toggleTorch(context)
        QuickAction.VOLUME -> {
            // ADJUST_SAME + FLAG_SHOW_UI opens the system volume slider without changing anything.
            audio(context).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            true
        }
        QuickAction.PLAY_PAUSE -> {
            val am = audio(context)
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            true
        }
    }

    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)

    private fun toggleTorch(context: Context): Boolean {
        val id = torchCamera(context) ?: return false
        return try {
            context.getSystemService(CameraManager::class.java).setTorchMode(id, !torchOn)
            true
        } catch (_: CameraAccessException) {
            // Usually another app (like the camera) is holding the camera right now.
            Toast.makeText(context, R.string.flashlight_busy, Toast.LENGTH_SHORT).show()
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** The back camera with a flash, and a one-time callback that keeps [torchOn] accurate. */
    private fun torchCamera(context: Context): String? {
        torchCameraId?.let { return it }
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return null
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cid ->
                val c = cm.getCameraCharacteristics(cid)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull() ?: return null
        torchCameraId = id
        if (!callbackRegistered) {
            callbackRegistered = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
        return id
    }
}
