package com.yogeshpaliyal.halo.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs

/**
 * Restarts the bubble after the phone boots or Halo is updated, but only if the user had it
 * turned on. specialUse foreground services may start from BOOT_COMPLETED, and apps holding
 * "Display over other apps" are allowed to start them from the background.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        if (!Prefs.enabled(context) || !Permissions.canDrawOverlays(context)) return
        runCatching { HaloService.start(context) }
    }
}
