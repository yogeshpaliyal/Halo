package com.yogeshpaliyal.halo.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Quick Settings tile that turns the bubble on and off. It uses the same start/stop as the
 * switch on the home screen, and opens Halo instead when "Display over other apps" is missing.
 */
class HaloTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        // Follows the service while the panel is open, so a tap (or the notification's Stop) shows at once.
        watch?.cancel()
        watch = HaloService.running.onEach { updateTile(it) }.launchIn(scope)
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        when {
            HaloService.running.value -> HaloService.stop(this)
            !Permissions.canDrawOverlays(this) -> openApp()
            // If Android won't let the service start from here (background start limits), open Halo,
            // whose home screen starts the bubble since it's now marked on.
            else -> runCatching { HaloService.start(this, wake = true) }.onFailure { openApp() }
        }
    }

    private fun updateTile(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ only accepts a PendingIntent here.
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to refresh the tile, e.g. after the bubble was switched from the app. */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, HaloTileService::class.java))
            }
        }
    }
}
