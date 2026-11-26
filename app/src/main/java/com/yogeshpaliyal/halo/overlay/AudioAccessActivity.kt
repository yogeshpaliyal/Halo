package com.yogeshpaliyal.halo.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

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
 * The dial opens this the first time a volume change is ignored. It sets the volume the slider
 * asked for while it is on screen, re-starts the service, and closes straight away.
 */
class AudioAccessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noAnimation()
    }

    override fun onResume() {
        super.onResume()
        // On screen now: this re-start of the service is "while in use".
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, HaloService::class.java).setAction(HaloService.ACTION_AUDIO_ACCESS),
            )
        }.onFailure { Log.w(TAG, "Couldn't re-start the service", it) }
        val index = intent.getIntExtra(EXTRA_VOLUME_INDEX, -1)
        if (index >= 0 && !SystemControls.setVolumeIndex(this, index)) {
            Log.w(TAG, "Volume still ignored with an activity on screen")
        }
        finish()
        noAnimation()
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
        /** Don't keep flashing an activity if access can't be won back on this phone. */
        private const val RETRY_MS = 60_000L
        private var lastOpened = 0L

        /** Opens it, unless it was tried in the last minute. */
        fun open(context: Context, volumeIndex: Int) {
            val now = SystemClock.elapsedRealtime()
            if (lastOpened != 0L && now - lastOpened < RETRY_MS) return
            lastOpened = now
            val intent = Intent(context, AudioAccessActivity::class.java)
                .putExtra(EXTRA_VOLUME_INDEX, volumeIndex)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY,
                )
            runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "Couldn't open AudioAccessActivity", it) }
        }
    }
}
