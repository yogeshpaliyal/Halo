package com.yogeshpaliyal.halo.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One item that can sit in the menu: a launchable app in one profile, or a [QuickAction].
 * The same package can exist twice (personal + work), so items are identified by [key].
 */
data class AppEntry(
    val key: String,
    val packageName: String,
    val label: String,
    /** Loads the icon on first use; app lists fetch hundreds of labels but only show a few icons. */
    val iconLoader: () -> Drawable,
    /** Null for quick actions. */
    val component: ComponentName?,
    val user: UserHandle?,
    /** True for work-profile apps. */
    val isWork: Boolean,
    /** False while the work profile is paused. Opening it then shows the system "Unpause?" prompt. */
    val available: Boolean,
    /** Set for quick actions (flashlight, volume, play/pause) instead of an app. */
    val action: QuickAction? = null,
    /** The apps inside, when this is a folder. */
    val children: List<AppEntry> = emptyList(),
) {
    val isFolder: Boolean get() = Folders.isFolderKey(key)

    val icon: Drawable by lazy { iconLoader() }
}

/**
 * Lists and opens apps across the user's profiles (personal + work) through [LauncherApps].
 *
 * Private space is a hidden profile: Android only reveals it to the default home app, so it
 * never shows up here.
 */
class AppRepository(context: Context) {

    private val context = context.applicationContext
    private val launcherApps = this.context.getSystemService(LauncherApps::class.java)
    private val userManager = this.context.getSystemService(UserManager::class.java)
    private val me = Process.myUserHandle()
    // Shared by background loaders, so it must be thread-safe.
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    /**
     * Favourites key format: a bare package name for the personal profile (the format older
     * versions stored, so existing favourites keep working) and "package@serial" for others.
     */
    fun keyFor(pkg: String, user: UserHandle): String =
        if (user == me) pkg else "$pkg@${userManager.getSerialNumberForUser(user)}"

    /** Every app in the app drawer of every visible profile, personal first, sorted by name. */
    fun launchableApps(): List<AppEntry> =
        launcherApps.profiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .distinctBy { it.componentName.packageName }
                .filter { !(user == me && it.componentName.packageName == context.packageName) }
                .map { toEntry(it, user) }
        }.sortedWith(compareBy({ it.label.lowercase() }, { it.isWork }))

    /** Quick actions offered in the favourites picker, before the apps. */
    fun quickActions(): List<AppEntry> = QuickAction.entries.mapNotNull { actionEntry(it) }

    private fun actionEntry(action: QuickAction): AppEntry? {
        if (!QuickActions.isAvailable(context, action)) return null
        val icon = ContextCompat.getDrawable(context, QuickActions.iconRes(action)) ?: return null
        return AppEntry(
            key = action.key,
            packageName = "",
            label = context.getString(action.labelRes),
            iconLoader = { icon },
            component = null,
            user = null,
            isWork = false,
            available = true,
            action = action,
        )
    }

    fun entryFor(key: String): AppEntry? {
        if (key.startsWith(QuickAction.PREFIX)) return QuickAction.fromKey(key)?.let(::actionEntry)
        if (Folders.isFolderKey(key)) return folderEntry(key)
        val pkg = key.substringBefore('@')
        val user = if ('@' in key) {
            key.substringAfter('@').toLongOrNull()?.let { userManager.getUserForSerialNumber(it) }
        } else {
            me
        } ?: return null
        val info = runCatching { launcherApps.getActivityList(pkg, user) }.getOrNull()?.firstOrNull()
            ?: return null
        return toEntry(info, user)
    }

    fun favorites(): List<AppEntry> =
        Prefs.favorites(context).mapNotNull(::entryFor).take(Prefs.MAX_FAVORITES)

    /** A folder with whichever of its apps are still installed. */
    private fun folderEntry(key: String): AppEntry? {
        val folder = Folders.get(context, key) ?: return null
        val children = folder.apps.filterNot(Folders::isFolderKey).mapNotNull(::entryFor)
        return AppEntry(
            key = key,
            packageName = "",
            label = folder.name,
            iconLoader = { FolderIcon(children.map { it.icon }) },
            component = null,
            user = null,
            isWork = false,
            available = true,
            children = children,
        )
    }

    /**
     * Most recently opened personal-profile apps, newest first. Android's usage data only
     * covers this profile, so work apps can be favourites but never recents. The app on
     * screen right now, home screens and Halo itself are left out.
     */
    fun recents(limit: Int, excludeKeys: Set<String> = emptySet()): List<AppEntry> {
        if (!Permissions.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()

        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - TimeUnit.DAYS.toMillis(3), end)
        val lastResumed = HashMap<String, Long>()
        var foreground: String? = null
        var foregroundTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION") // Same value as ACTIVITY_RESUMED (API 29+).
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // Track what's on screen even for excluded screens, so it's never offered.
                if (event.timeStamp >= foregroundTime) {
                    foregroundTime = event.timeStamp
                    foreground = event.packageName
                }
                // Screens Android itself keeps out of Recents (call UI, assistant pop-ups,
                // permission dialogs and so on) shouldn't count as recently used here either.
                if (!excludedFromRecents(event.packageName, event.className)) {
                    lastResumed[event.packageName] = event.timeStamp
                }
            }
        }

        val home = homePackages()
        val hidden = Prefs.hiddenRecents(context)
        return lastResumed.entries
            .asSequence()
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { pkg ->
                pkg != context.packageName && pkg != foreground && pkg !in home && pkg !in hidden && pkg !in excludeKeys
            }
            .mapNotNull(::entryFor) // Also drops packages with no launcher activity.
            .take(limit)
            .toList()
    }

    /** Opens [app] in its own profile, resuming its task if it is already running. */
    fun launch(app: AppEntry): Boolean = try {
        val action = app.action
        if (app.isFolder) {
            false // folders open in the menu, never launch
        } else if (action != null) {
            QuickActions.perform(context, action)
        } else {
            launcherApps.startMainActivity(app.component, app.user, null, null)
            true
        }
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        // Profile locked or being removed.
        false
    }

    private fun toEntry(info: LauncherActivityInfo, user: UserHandle): AppEntry {
        val pkg = info.componentName.packageName
        val key = keyFor(pkg, user)
        return AppEntry(
            key = key,
            packageName = pkg,
            label = info.label.toString(),
            // Badged: work apps get the briefcase, personal apps are unchanged.
            iconLoader = { iconCache.getOrPut(key) { info.getBadgedIcon(0) } },
            component = info.componentName,
            user = user,
            isWork = user != me,
            available = user == me || !userManager.isQuietModeEnabled(user),
        )
    }

    private val excludedCache = HashMap<String, Boolean>()

    /** True if this activity is declared with excludeFromRecents. */
    private fun excludedFromRecents(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        return excludedCache.getOrPut("$pkg/$className") {
            try {
                val info = context.packageManager.getActivityInfo(ComponentName(pkg, className), 0)
                info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }
}
