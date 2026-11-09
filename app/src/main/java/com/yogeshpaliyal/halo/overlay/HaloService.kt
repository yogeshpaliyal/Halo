package com.yogeshpaliyal.halo.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Foreground service that owns the bubble window and, during a long-press, the menu window. */
class HaloService : Service(), BubbleView.Listener {

    private lateinit var wm: WindowManager
    private lateinit var repo: AppRepository
    private var bubble: BubbleView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var menu: RadialMenuView? = null
    private var onRight = true
    private var snapAnimator: ValueAnimator? = null

    private val bubbleSize by lazy { (52 * resources.displayMetrics.density).toInt() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Permissions.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(WindowManager::class.java)
        repo = AppRepository(this)
        addBubble()
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation or fold/unfold: re-dock to the same side of the new screen size.
        if (bubble != null) {
            removeMenu()
            snapTo(onRight, bubbleParams.y, animate = false)
        }
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        removeMenu()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        _running.value = false
        super.onDestroy()
    }

    // --- windows --------------------------------------------------------------------------

    private fun overlayParams(width: Int, height: Int, extraFlags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun addBubble() {
        val screen = screenSize()
        onRight = Prefs.bubbleOnRight(this)
        bubbleParams = overlayParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        val view = BubbleView(this, this)
        bubble = view
        wm.addView(view, bubbleParams)
        snapTo(onRight, Prefs.bubbleY(this, (screen.y * 0.4f).toInt()), animate = false)
    }

    private fun showMenu() {
        val favorites = repo.favorites()
        val recents = repo.recents(Prefs.MAX_RECENTS, exclude = favorites.mapTo(HashSet()) { it.packageName })
        val view = RadialMenuView(
            context = this,
            anchorScreenX = (bubbleParams.x + bubbleSize / 2).toFloat(),
            anchorScreenY = (bubbleParams.y + bubbleSize / 2).toFloat(),
            onRightEdge = onRight,
            favorites = favorites,
            recents = recents,
        )
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        wm.addView(view, params)
        menu = view
        view.show()
    }

    private fun removeMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    // --- docking --------------------------------------------------------------------------

    private fun snapTo(right: Boolean, y: Int, animate: Boolean) {
        val screen = screenSize()
        onRight = right
        val targetX = if (right) screen.x - bubbleSize else 0
        val targetY = y.coerceIn(0, screen.y - bubbleSize)
        bubbleParams.y = targetY
        Prefs.saveBubble(this, right, targetY)

        snapAnimator?.cancel()
        if (!animate) {
            bubbleParams.x = targetX
            bubble?.let { wm.updateViewLayout(it, bubbleParams) }
            return
        }
        snapAnimator = ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                bubble?.let { b -> if (b.isAttachedToWindow) wm.updateViewLayout(b, bubbleParams) }
            }
            start()
        }
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }

    // --- BubbleView.Listener --------------------------------------------------------------

    override fun onTap() {
        // Quick toggle: jump back to the previous app, like Alt+Tab.
        repo.recents(1).firstOrNull()?.let { repo.launch(it.packageName) }
    }

    override fun onDragMove(rawX: Float, rawY: Float) {
        snapAnimator?.cancel()
        bubbleParams.x = (rawX - bubbleSize / 2f).toInt()
        bubbleParams.y = (rawY - bubbleSize / 2f).toInt()
        bubble?.let { wm.updateViewLayout(it, bubbleParams) }
    }

    override fun onDragEnd(rawX: Float, rawY: Float) {
        snapTo(right = rawX > screenSize().x / 2f, y = bubbleParams.y, animate = true)
    }

    override fun onMenuOpen(rawX: Float, rawY: Float) = showMenu()

    override fun onMenuMove(rawX: Float, rawY: Float) {
        if (menu?.updateFinger(rawX, rawY) == true) bubble?.tick()
    }

    override fun onMenuRelease(rawX: Float, rawY: Float) {
        menu?.updateFinger(rawX, rawY)
        val target = menu?.selected
        removeMenu()
        target?.let { repo.launch(it.packageName) }
    }

    override fun onMenuCancel() = removeMenu()

    // --- foreground notification ----------------------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HaloService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "bubble"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.yogeshpaliyal.halo.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            Prefs.setEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, HaloService::class.java))
        }

        fun stop(context: Context) {
            Prefs.setEnabled(context, false)
            context.stopService(Intent(context, HaloService::class.java))
        }
    }
}
