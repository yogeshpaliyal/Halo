package com.yogeshpaliyal.halo.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.text.format.DateFormat
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Folders
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.data.Stats
import com.yogeshpaliyal.halo.data.TapAction
import com.yogeshpaliyal.halo.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date

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

    // The window is a bit larger than the bubble itself, so its animations have room.
    private fun sizePx() = (Prefs.bubbleSize(this).dp / BubbleView.ART_SCALE * resources.displayMetrics.density).toInt()

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
        Prefs.registerListener(this, prefListener)
        NotificationBadges.addListener(badgeListener)
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Adds the bubble, unless a snooze from before a restart is still running.
        applySnooze()
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
        if (intent?.action == ACTION_WAKE) Prefs.clearSnooze(this)
        // Started again from AudioAccessActivity: every start via startForegroundService must
        // be answered with startForeground, and this one also carries "while in use" access.
        if (intent?.action == ACTION_AUDIO_ACCESS) startAsForeground()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // New wallpaper colours (Material You), language or dark mode.
        bubble?.refreshStyle()
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
        runCatching { unregisterReceiver(screenOnReceiver) }
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
            Prefs.KEY_SNOOZED_UNTIL -> applySnooze()
            Prefs.KEY_BUBBLE_OPACITY -> bubble?.refreshOpacity()
            Prefs.KEY_BUBBLE_SIZE -> resizeBubble()
            Prefs.KEY_TUCK_BUBBLE -> applyTuckSetting()
            Prefs.KEY_BUBBLE_RIGHT -> applySideSetting()
            Prefs.KEY_BUBBLE_PALETTE, Prefs.KEY_BUBBLE_CHARACTER, Prefs.KEY_BUBBLE_ANIMATIONS,
            Prefs.KEY_BUBBLE_REACTIONS, Prefs.KEY_AVATAR_VERSION, Prefs.KEY_BUBBLE_ACCESSORY -> bubble?.refreshStyle()
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

    // --- snooze ---------------------------------------------------------------------------

    private val snoozeEnd = Runnable { applySnooze() }

    /**
     * Handler delays stop counting while the phone sleeps, so a long snooze is also checked
     * again each time the screen comes on.
     */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applySnooze()
    }

    private fun snoozed(): Boolean = Prefs.snoozedUntil(this) > System.currentTimeMillis()

    /** Picked on the dial's snooze button: hide the bubble for a while, or turn it off. */
    private fun snooze(choice: SnoozeChoice) {
        if (choice == SnoozeChoice.OFF) {
            Toast.makeText(this, R.string.snooze_off_toast, Toast.LENGTH_LONG).show()
            stop(this)
            return
        }
        val until = System.currentTimeMillis() + choice.minutes * 60_000L
        Prefs.setSnoozedUntil(this, until)
        Toast.makeText(this, getString(R.string.snooze_toast, formatTime(until)), Toast.LENGTH_SHORT).show()
    }

    /**
     * Removes the bubble (and the full-screen detector) from the screen for the saved snooze,
     * adds them back once it's over, and wakes up when it ends.
     */
    private fun applySnooze() {
        main.removeCallbacks(snoozeEnd)
        val until = Prefs.snoozedUntil(this)
        val left = until - System.currentTimeMillis()
        when {
            left > 0 -> main.postDelayed(snoozeEnd, left)
            // Over: forget it (this calls back in here once more, with nothing saved).
            until != 0L -> {
                Prefs.clearSnooze(this)
                return
            }
        }
        if (left > 0) removeWindows() else addWindows()
        applyVisibility()
        updateNotification()
    }

    /** Puts the bubble and the full-screen detector on screen, if they aren't already. */
    private fun addWindows() {
        if (bubble != null) return
        hiddenNow = false
        touching = false
        addBubble()
        if (detector == null) addFullscreenDetector()
        updateBubbleBadge()
    }

    /** Takes the bubble and its helper windows off the screen entirely while snoozed. */
    private fun removeWindows() {
        if (bubble == null && detector == null) return
        // The dial is usually already closing; let that animation finish on its own.
        if (menu != null) removeMenu()
        restY = null
        snapAnimator?.cancel()
        main.removeCallbacks(tuckRunnable)
        main.removeCallbacks(applyRunnable)
        detector?.let { runCatching { wm.removeView(it) } }
        detector = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        fullscreen = false
        hiddenNow = false
        touching = false
    }

    private fun formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

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
        setNoLimits(Prefs.tuckBubble(this))
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
        val recents = if (Prefs.showRecents(this)) {
            repo.recents(Prefs.MAX_RECENTS, excludeKeys = favorites.mapTo(HashSet()) { it.key })
        } else {
            emptyList()
        }

        // Near the top or bottom the dial can't be centred on the bubble without spilling
        // off screen, so centre it where it fits and slide the bubble there while it's open.
        val showBrightness = Prefs.brightnessSlider(this)
        val showVolume = Prefs.volumeSlider(this)
        val fullHalf = RadialMenuView.halfExtentPx(
            this,
            favorites.isNotEmpty(),
            recents.isNotEmpty() || favorites.any { it.isFolder },
            hasSliders = showBrightness || showVolume,
        )
        val screen = screenSize()
        val screenH = screen.y
        val (top, bottom) = safeInsets()
        // Compact dial (Settings), and shrink further whenever the dial wouldn't fit between the
        // system bars or across the screen, e.g. in landscape, so no icon is ever cut off.
        val fit = if (fullHalf > 0f) {
            minOf((screenH - top - bottom) / (2f * fullHalf), (screen.x - bubbleSize / 2f) / fullHalf)
        } else {
            1f
        }
        val scale = minOf(Prefs.dialSize(this).scale, fit).coerceIn(MIN_DIAL_SCALE, 1f)
        val half = fullHalf * scale
        val bubbleCenter = bubbleParams.y + bubbleSize / 2f
        val lo = top + half
        val hi = screenH - bottom - half
        val centerY = if (lo <= hi) bubbleCenter.coerceIn(lo, hi) else (top + screenH - bottom) / 2f
        if (kotlin.math.abs(centerY - bubbleCenter) > 1f) {
            restY = bubbleParams.y
        }
        // A tucked bubble is already sliding out from the touch; make sure it ends fully out
        // (and at the dial's centre) even if the move above would cancel that slide.
        if (kotlin.math.abs(centerY - bubbleCenter) > 1f || bubbleParams.x != dockedX(onRight)) {
            moveBubble(dockedX(onRight), (centerY - bubbleSize / 2f).toInt(), animate = true)
        }

        val view = RadialMenuView(
            context = this,
            anchorScreenX = (dockedX(onRight) + bubbleSize / 2).toFloat(),
            anchorScreenY = centerY,
            onRightEdge = onRight,
            favorites = favorites,
            recents = recents,
            badged = if (Prefs.notificationDots(this)) NotificationBadges.keys else emptySet(),
            showBrightness = showBrightness,
            showVolume = showVolume,
            volumeOnTop = Prefs.volumeOnTop(this),
            bottomInset = bottom,
            scale = scale,
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
        // Glass theme: blur whatever is behind the dial, on phones that support it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            DialColors.of(Prefs.dialTheme(this), Prefs.bubblePalette(this)).blur &&
            wm.isCrossWindowBlurEnabled
        ) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = (24 * resources.displayMetrics.density).toInt()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Don't let the system shrink the window to avoid the status and navigation bars.
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        // Brightness slider used before "Modify system settings" is allowed: take them there.
        view.onBrightnessLocked = {
            removeMenu()
            runCatching { startActivity(SystemControls.writeSettingsIntent(this)) }
        }
        // Volume change ignored: Halo was restarted in the background without audio access.
        view.onVolumeBlocked = { index -> AudioAccessActivity.open(this, index) }
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
                    // Drop the glass blur as it closes, and let touches through to the app.
                    lp.flags = (lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) and
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
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
        scheduleTuck()
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

    /**
     * Docks to a side. Animated snaps (after a drag) land fully out and tuck in a moment later;
     * instant ones (start, rotation, resize) go straight to the tucked spot when tucking is on.
     */
    private fun snapTo(right: Boolean, y: Int, animate: Boolean) {
        val screen = screenSize()
        val (top, bottom) = safeInsets()
        onRight = right
        val maxY = (screen.y - bottom - bubbleSize).coerceAtLeast(top)
        val targetY = y.coerceIn(top, maxY)
        Prefs.saveBubble(this, right, targetY)
        if (animate) {
            moveBubble(dockedX(right), targetY, animate = true)
            scheduleTuck()
        } else {
            main.removeCallbacks(tuckRunnable)
            moveBubble(restX(right), targetY, animate = false)
        }
    }

    // --- tucking into the edge ------------------------------------------------------------

    /** True while a finger is on the bubble; it stays out until the gesture ends. */
    private var touching = false
    private val tuckRunnable = Runnable { tuckNow() }

    /** X with the whole bubble on screen against its edge. */
    private fun dockedX(right: Boolean) = if (right) screenSize().x - bubbleSize else 0

    /** X where the bubble rests: docked, or half off screen when tucking is on. */
    private fun restX(right: Boolean): Int {
        if (!Prefs.tuckBubble(this)) return dockedX(right)
        val hidden = (bubbleSize * TUCK_FRACTION).toInt()
        return if (right) dockedX(right) + hidden else -hidden
    }

    /** Lets the bubble window sit partly past the screen edge (only needed when tucking). */
    private fun setNoLimits(on: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        bubbleParams.flags = if (on) bubbleParams.flags or flag else bubbleParams.flags and flag.inv()
    }

    /** Slides back into the edge after a short pause, unless something still needs it out. */
    private fun scheduleTuck() {
        main.removeCallbacks(tuckRunnable)
        if (Prefs.tuckBubble(this)) main.postDelayed(tuckRunnable, TUCK_DELAY_MS)
    }

    private fun tuckNow() {
        if (touching || menu != null || restY != null || bubble == null) return
        val target = restX(onRight)
        if (bubbleParams.x != target) moveBubble(target, bubbleParams.y, animate = true)
    }

    /** Side picked in the app (tutorial): slide the bubble across to that edge, same height. */
    private fun applySideSetting() {
        if (bubble == null || menu != null || touching) return
        val right = Prefs.bubbleOnRight(this)
        // snapTo saves the side too; an unchanged value doesn't call back here.
        if (right != onRight) snapTo(right, bubbleParams.y, animate = true)
    }

    private fun applyTuckSetting() {
        val view = bubble ?: return
        val on = Prefs.tuckBubble(this)
        main.removeCallbacks(tuckRunnable)
        if (on) {
            setNoLimits(true)
            runCatching { wm.updateViewLayout(view, bubbleParams) }
            scheduleTuck()
        } else if (menu == null && !touching) {
            // Slide back out fully, then drop the off-screen allowance once it's in place.
            moveBubble(dockedX(onRight), bubbleParams.y, animate = false)
            setNoLimits(false)
            runCatching { wm.updateViewLayout(view, bubbleParams) }
        } else {
            setNoLimits(false)
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

    /** Opens an app and counts the switch (it unlocks accessories; stays on the phone). */
    private fun launch(app: AppEntry, kind: Stats.Kind) {
        if (repo.launch(app)) {
            Prefs.addSwitch(this)
            // Local tally for the Recap; quick actions aren't app switches.
            if (app.action == null && !app.isFolder) Stats.record(this, app.key, kind)
        }
    }

    override fun onTap() = perform(Prefs.tapAction(this), Stats.Kind.TAP, Prefs.tapApp(this))

    override fun onDoubleTap() = perform(Prefs.doubleTapAction(this), Stats.Kind.DOUBLE, Prefs.doubleTapApp(this))

    private fun perform(action: TapAction, kind: Stats.Kind, appKey: String?) {
        when (action) {
            // Quick toggle, like Alt+Tab.
            TapAction.PREVIOUS_APP -> repo.recents(1).firstOrNull()?.let { launch(it, kind) }
            // The one before that: tap and double tap cover your two latest apps.
            TapAction.SECOND_APP -> repo.recents(2).getOrNull(1)?.let { launch(it, kind) }
            TapAction.OPEN_MENU -> showMenu(tapMode = true)
            // The app picked for this gesture; works for quick actions and work apps too.
            TapAction.OPEN_APP -> appKey?.let(repo::entryFor)?.let { launch(it, kind) }
            TapAction.NOTHING -> Unit
        }
    }

    override fun onTouchStart() {
        touching = true
        main.removeCallbacks(tuckRunnable)
        val out = dockedX(onRight)
        if (Prefs.tuckBubble(this) && menu == null && bubbleParams.x != out) {
            moveBubble(out, bubbleParams.y, animate = true)
        }
    }

    override fun onTouchEnd() {
        touching = false
        scheduleTuck()
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
        // Let go on a snooze time: hide the bubble for that long.
        view?.snoozeChoice?.let { choice ->
            removeMenu()
            snooze(choice)
            return
        }
        // Let go on the snooze button itself: keep its times up and take the next tap.
        if (view != null && view.onSnoozeButton) {
            keepOpenForTap(view)
            return
        }
        // Let go on the brightness or volume slider: the change is already made, just close.
        if (view?.endSlider() == true) {
            removeMenu()
            return
        }
        val target = view?.selected
        if (view != null && target != null && target.isFolder) {
            // Let go on a folder: keep the dial up with the folder open, and take the next tap.
            view.openSelectedFolder()
            keepOpenForTap(view)
            return
        }
        removeMenu()
        target?.let { launch(it, Stats.Kind.DIAL) }
    }

    /** A hold ended but the dial stays up: let its window take the next tap. */
    private fun keepOpenForTap(view: RadialMenuView) {
        armTapMode(view)
        (view.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(view, lp) }
        }
    }

    /** Menu takes touches itself: tapping an app opens it, a folder opens the folder, anything else closes. */
    private fun armTapMode(view: RadialMenuView) {
        view.onPick = { app ->
            removeMenu()
            app?.let { launch(it, Stats.Kind.DIAL) }
        }
        view.onSnoozePick = { choice ->
            removeMenu()
            snooze(choice)
        }
    }

    override fun onMenuCancel() = removeMenu()

    // --- foreground notification ----------------------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    /** Shows the snooze in the notification, with a button to bring the bubble back now. */
    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val until = Prefs.snoozedUntil(this)
        val snoozed = until > System.currentTimeMillis()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HaloService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (snoozed) getString(R.string.notification_snoozed, formatTime(until))
                else getString(R.string.notification_text),
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
        if (snoozed) {
            val wake = PendingIntent.getService(
                this, 2, Intent(this, HaloService::class.java).setAction(ACTION_WAKE), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(R.string.notification_wake), wake)
        }
        return builder.addAction(0, getString(R.string.notification_stop), stop).build()
    }

    companion object {
        private const val CHANNEL_ID = "bubble"
        private const val APP_WATCH_MS = 1000L
        /** Share of the bubble window hidden past the edge when tucked. */
        private const val TUCK_FRACTION = 0.5f
        /** Smallest the dial shrinks to fit a short (landscape) screen; icons stay tappable. */
        private const val MIN_DIAL_SCALE = 0.5f
        /** How long the bubble stays out after you let go before tucking back in. */
        private const val TUCK_DELAY_MS = 1500L
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.yogeshpaliyal.halo.STOP"
        private const val ACTION_WAKE = "com.yogeshpaliyal.halo.WAKE"
        const val ACTION_AUDIO_ACCESS = "com.yogeshpaliyal.halo.AUDIO_ACCESS"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        /** [wake] = true also ends a snooze (the user switched the bubble on themselves). */
        fun start(context: Context, wake: Boolean = false) {
            if (wake) Prefs.clearSnooze(context)
            Prefs.setEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, HaloService::class.java))
        }

        fun stop(context: Context) {
            Prefs.setEnabled(context, false)
            Prefs.clearSnooze(context)
            context.stopService(Intent(context, HaloService::class.java))
        }
    }
}
