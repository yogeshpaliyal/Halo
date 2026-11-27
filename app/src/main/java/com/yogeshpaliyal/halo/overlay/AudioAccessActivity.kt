package com.yogeshpaliyal.halo.overlay

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * Invisible, instant activity that gives Halo back the right to change the volume.
 *
 * Android only lets an app with no visible activity change the volume when its foreground
 * service was started "while in use", that is while the app was on screen. After Halo is swiped
 * out of recents the system restarts [HaloService] in the background, without that access, and
 * from then on every volume change from the dial is silently ignored (issue #17). Starting the
 * service again from a visible activity grants the access. Halo may start an activity from the
 * background because it can draw over other apps.
 *
 * This activity stays open while the radial menu is open (or while volume control is used),
 * keeping Halo in a visible activity state so volume changes succeed.
 */
class AudioAccessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noAnimation()
    }

    override fun onResume() {
        super.onResume()
        activeInstance = WeakReference(this)
        // On screen now: this re-start of the service is "while in use".
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, HaloService::class.java).setAction(HaloService.ACTION_AUDIO_ACCESS),
            )
        }.onFailure { Log.w(TAG, "Couldn't re-start the service", it) }
        val index = intent.getIntExtra(EXTRA_VOLUME_INDEX, -1)
        if (index >= 0) {
            SystemControls.setVolumeIndex(this, index)
        }
        noAnimation()
    }

    override fun onDestroy() {
        if (activeInstance?.get() == this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun noAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "HaloVolume"
        private const val EXTRA_VOLUME_INDEX = "volume_index"
        private var activeInstance: WeakReference<AudioAccessActivity>? = null

        fun isResumed(): Boolean = activeInstance?.get() != null

        /** Opens the invisible activity to grant volume access while radial menu is active. */
        fun open(context: Context, volumeIndex: Int = -1) {
            if (activeInstance?.get() != null) return
            val intent = Intent(context, AudioAccessActivity::class.java)
                .putExtra(EXTRA_VOLUME_INDEX, volumeIndex)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY,
                )
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
            } else {
                null
            }
            runCatching { context.startActivity(intent, options?.toBundle()) }
                .onFailure { Log.w(TAG, "Couldn't open AudioAccessActivity", it) }
        }

        /** Closes the invisible activity when radial menu is closed. */
        fun close() {
            activeInstance?.get()?.let {
                runCatching { it.finish() }
            }
            activeInstance = null
        }
    }
}
