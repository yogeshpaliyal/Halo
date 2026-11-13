package com.yogeshpaliyal.halo.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Folders
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.data.TapAction
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
    /** Where the bubble rests while the menu has temporarily moved it to the dial's centre. */
    private var restY: Int? = null

    /** Bubble width and height in px, from the size setting. */
    private var bubbleSize = 0

    private fun sizePx() = (Prefs.bubbleSize(this).dp * resources.displayMetrics.density).toInt()

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
        addFullscreenDetector()
        Prefs.registerListener(this, prefListener)
        NotificationBadges.addListener(badgeListener)
        updateAppWatch()
        updateBubbleBadge()
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
            val homeY = restY ?: bubbleParams.y
            restY = null
            removeMenu(animate = false)
            snapTo(onRight, homeY, animate = false)
        }
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        main.removeCallbacksAndMessages(null)
        Prefs.unregisterListener(this, prefListener)
        NotificationBadges.removeListener(badgeListener)
        detector?.let { runCatching { wm.removeView(it) } }
        detector = null
        removeMenu(animate = false)
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        _running.value = false
        super.onDestroy()
    }

    // --- full screen ----------------------------------------------------------------------

    private val main = Handler(Looper.getMainLooper())
    private var detector: View? = null
    private var fullscreen = false
    private var hiddenNow = false
    private val applyRunnable = Runnable { applyVisibility() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.KEY_HIDE_FULLSCREEN -> applyVisibility()
            Prefs.KEY_BUBBLE_OPACITY -> bubble?.refreshOpacity()
            Prefs.KEY_BUBBLE_SIZE -> resizeBubble()
            Prefs.KEY_HIDE_IN_APPS -> updateAppWatch()
            Prefs.KEY_NOTIFICATION_DOTS, Prefs.KEY_FAVORITES, Prefs.KEY_FOLDERS -> updateBubbleBadge()
        }
    }

    private val badgeListener: () -> Unit = { main.post { updateBubbleBadge() } }

    /** Dot on the bubble when any favourite has notifications waiting. */
    private fun updateBubbleBadge() {
        val on = Prefs.notificationDots(this) &&
            Folders.expand(this, Prefs.favorites(this)).any { it in NotificationBadges.keys }
        bubble?.badge = on
    }

    /**
     * Games, video and OTT apps hide the status bar when they go full screen. We can't see which
     * app is open (that would need Accessibility), but a 1px, invisible, non-touchable overlay is
     * told whenever the status bar hides or shows, which is exactly the signal we need.
     */
    private fun addFullscreenDetector() {
        val view = View(this)
        val params = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener { _, insets ->
                onFullscreenChanged(!insets.isVisible(WindowInsets.Type.statusBars()))
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            view.setOnSystemUiVisibilityChangeListener { visibility ->
                onFullscreenChanged(visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
            }
        }
        runCatching { wm.addView(view, params) }.onSuccess { detector = view }
    }

    private fun onFullscreenChanged(isFullscreen: Boolean) {
        fullscreen = isFullscreen
        main.removeCallbacks(applyRunnable)
        // Hide straight away; come back after a short pause so swiping the bars into view
        // for a moment in a game doesn't make the bubble flash.
        if (isFullscreen) applyVisibility() else main.postDelayed(applyRunnable, 600)
    }

    // --- hide in chosen apps ----------------------------------------------------------------

    private var foregroundPkg: String? = null
    private var lastEventCheck = 0L
    private val appWatch = object : Runnable {
        override fun run() {
            checkForegroundApp()
            main.postDelayed(this, APP_WATCH_MS)
        }
    }

    /**
     * Watches which app is on screen, but only while the user has picked apps to hide in.
     * Reads only new usage events since the last check (about once a second, screen on only),
     * so the cost stays small.
     */
    private fun updateAppWatch() {
        main.removeCallbacks(appWatch)
        val active = Prefs.hideInApps(this).isNotEmpty() && Permissions.hasUsageAccess(this)
        if (active) {
            lastEventCheck = System.currentTimeMillis() - 60_000
            main.post(appWatch)
        } else {
            foregroundPkg = null
        }
        applyVisibility()
    }

    private fun checkForegroundApp() {
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return
        val usm = getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(lastEventCheck, now) }.getOrNull() ?: return
        lastEventCheck = now
        val e = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION") // Same value as ACTIVITY_RESUMED (API 29+).
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = e.packageName
        }
        if (latest != null && latest != foregroundPkg) {
            foregroundPkg = latest
            applyVisibility()
        }
    }

    /** Hidden while another app is full screen (if enabled) or while a chosen app is open. */
    private fun applyVisibility() {
        val inHiddenApp = foregroundPkg?.let { it in Prefs.hideInApps(this) } == true
        val hide = (fullscreen && Prefs.hideInFullscreen(this)) || inHiddenApp
        if (hide == hiddenNow) return
        hiddenNow = hide
        val view = bubble ?: return
        if (hide) removeMenu()
        // A hidden bubble must not catch touches meant for the game underneath.
        bubbleParams.flags = if (hide) {
            bubbleParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            bubbleParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, bubbleParams) }
        view.setHidden(hide)
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

    /** Applies a new size setting: keep the bubble's centre, then re-dock it. */
    private fun resizeBubble() {
        val view = bubble ?: return
        removeMenu(animate = false)
        val centerY = bubbleParams.y + bubbleSize / 2
        bubbleSize = sizePx()
        bubbleParams.width = bubbleSize
        bubbleParams.height = bubbleSize
        runCatching { wm.updateViewLayout(view, bubbleParams) }
        snapTo(onRight, centerY - bubbleSize / 2, animate = false)
    }

    private fun addBubble() {
        bubbleSize = sizePx()
        val screen = screenSize()
        onRight = Prefs.bubbleOnRight(this)
        bubbleParams = overlayParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        // Work out the docked position first, then add the window already in place.
        snapTo(onRight, Prefs.bubbleY(this, (screen.y * 0.4f).toInt()), animate = false)
        val view = BubbleView(this, this)
        bubble = view
        wm.addView(view, bubbleParams)
    }

    /**
     * @param tapMode true when opened by a tap: the menu window then takes touches itself
     * and waits for the user to tap an app (or anywhere else to close).
     */
    private fun showMenu(tapMode: Boolean = false) {
        removeMenu(animate = false)
        // Empty folders (all their apps uninstalled) are left out of the dial.
        val favorites = repo.favorites().filter { !it.isFolder || it.children.isNotEmpty() }
        val recents = repo.recents(Prefs.MAX_RECENTS, excludeKeys = favorites.mapTo(HashSet()) { it.key })

        // Near the top or bottom the dial can't be centred on the bubble without spilling
        // off screen, so centre it where it fits and slide the bubble there while it's open.
        val half = RadialMenuView.halfExtentPx(
            this, favorites.isNotEmpty(), recents.isNotEmpty() || favorites.any { it.isFolder },
        )
        val screenH = screenSize().y
        val (top, bottom) = safeInsets()
        val bubbleCenter = bubbleParams.y + bubbleSize / 2f
        val lo = top + half
        val hi = screenH - bottom - half
        val centerY = if (lo <= hi) bubbleCenter.coerceIn(lo, hi) else (top + screenH - bottom) / 2f
        if (kotlin.math.abs(centerY - bubbleCenter) > 1f) {
            restY = bubbleParams.y
            moveBubble(bubbleParams.x, (centerY - bubbleSize / 2f).toInt(), animate = true)
        }

        val view = RadialMenuView(
            context = this,
            anchorScreenX = (bubbleParams.x + bubbleSize / 2).toFloat(),
            anchorScreenY = centerY,
            onRightEdge = onRight,
            favorites = favorites,
            recents = recents,
            badged = if (Prefs.notificationDots(this)) NotificationBadges.keys else emptySet(),
        )
        // Exact display size instead of MATCH_PARENT: an overlay sized MATCH_PARENT stops at the
        // navigation bar, which left an undimmed strip at the bottom.
        val full = screenSize()
        val params = overlayParams(
            full.x,
            full.y,
            if (tapMode) {
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            },
        )
        params.x = 0
        params.y = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Don't let the system shrink the window to avoid the status and navigation bars.
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (tapMode) armTapMode(view)
        wm.addView(view, params)
        menu = view
        view.show()
    }

    /** Menus playing their close animation; removed for good when it ends. */
    private val closingMenus = mutableSetOf<RadialMenuView>()

    /**
     * Closes the dial. Animated by default; [animate] = false removes it at once (service
     * shutting down, rotation, resize, or a new menu about to open).
     */
    private fun removeMenu(animate: Boolean = true) {
        val view = menu
        menu = null
        if (!animate) {
            closingMenus.forEach { runCatching { wm.removeView(it) } }
            closingMenus.clear()
        }
        if (view != null) {
            if (animate && view.isAttachedToWindow) {
                // Let touches through to the app underneath while the dial fades out.
                (view.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    runCatching { wm.updateViewLayout(view, lp) }
                }
                closingMenus += view
                view.dismiss {
                    if (closingMenus.remove(view)) runCatching { wm.removeView(view) }
                }
            } else {
                runCatching { wm.removeView(view) }
            }
        }
        restY?.let { y ->
            restY = null
            moveBubble(bubbleParams.x, y, animate = true)
        }
    }

    /**
     * Status bar / cutout height at the top and navigation bar height at the bottom, in px.
     * The bubble stays between them so it never hides behind system bars.
     */
    private fun safeInsets(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            insets.top to insets.bottom
        } else {
            fun dimen(name: String): Int {
                val id = resources.getIdentifier(name, "dimen", "android")
                return if (id > 0) resources.getDimensionPixelSize(id) else 0
            }
            dimen("status_bar_height") to dimen("navigation_bar_height")
        }

    /** Moves the bubble window, optionally animating both axes. */
    private fun moveBubble(targetX: Int, targetY: Int, animate: Boolean) {
        snapAnimator?.cancel()
        if (!animate) {
            bubbleParams.x = targetX
            bubbleParams.y = targetY
            // A window that was just added isn't attached yet but can already be moved, so
            // don't gate on isAttachedToWindow here; only skip if it was never added at all.
            bubble?.let { runCatching { wm.updateViewLayout(it, bubbleParams) } }
            return
        }
        val startX = bubbleParams.x
        val startY = bubbleParams.y
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                bubbleParams.x = (startX + (targetX - startX) * f).toInt()
                bubbleParams.y = (startY + (targetY - startY) * f).toInt()
                bubble?.let { b -> if (b.isAttachedToWindow) wm.updateViewLayout(b, bubbleParams) }
            }
            start()
        }
    }

    // --- docking --------------------------------------------------------------------------

    private fun snapTo(right: Boolean, y: Int, animate: Boolean) {
        val screen = screenSize()
        val (top, bottom) = safeInsets()
        onRight = right
        val targetX = if (right) screen.x - bubbleSize else 0
        val maxY = (screen.y - bottom - bubbleSize).coerceAtLeast(top)
        val targetY = y.coerceIn(top, maxY)
        Prefs.saveBubble(this, right, targetY)
        moveBubble(targetX, targetY, animate)
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
        when (Prefs.tapAction(this)) {
            // Quick toggle, like Alt+Tab.
            TapAction.PREVIOUS_APP -> repo.recents(1).firstOrNull()?.let { repo.launch(it) }
            TapAction.OPEN_MENU -> showMenu(tapMode = true)
            TapAction.NOTHING -> Unit
        }
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
        val view = menu
        view?.updateFinger(rawX, rawY)
        val target = view?.selected
        if (view != null && target != null && target.isFolder) {
            // Let go on a folder: keep the dial up with the folder open, and take the next tap.
            view.openSelectedFolder()
            armTapMode(view)
            (view.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                runCatching { wm.updateViewLayout(view, lp) }
            }
            return
        }
        removeMenu()
        target?.let { repo.launch(it) }
    }

    /** Menu takes touches itself: tapping an app opens it, a folder opens the folder, anything else closes. */
    private fun armTapMode(view: RadialMenuView) {
        view.onPick = { app ->
            removeMenu()
            app?.let { repo.launch(it) }
        }
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
        private const val APP_WATCH_MS = 1000L
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
