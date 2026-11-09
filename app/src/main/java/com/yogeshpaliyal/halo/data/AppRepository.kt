package com.yogeshpaliyal.halo.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.util.concurrent.TimeUnit

/**
 * One launchable app in one profile. The same package can exist twice (personal + work),
 * so apps are identified by [key], not by package name.
 */
data class AppEntry(
    val key: String,
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val component: ComponentName,
    val user: UserHandle,
    /** True for work-profile apps. */
    val isWork: Boolean,
    /** False while the work profile is paused. Opening it then shows the system "Unpause?" prompt. */
    val available: Boolean,
)

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
    private val iconCache = HashMap<String, Drawable>()

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

    fun entryFor(key: String): AppEntry? {
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
                lastResumed[event.packageName] = event.timeStamp
                if (event.timeStamp >= foregroundTime) {
                    foregroundTime = event.timeStamp
                    foreground = event.packageName
                }
            }
        }

        val home = homePackages()
        return lastResumed.entries
            .asSequence()
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { pkg ->
                pkg != context.packageName && pkg != foreground && pkg !in home && pkg !in excludeKeys
            }
            .mapNotNull(::entryFor) // Also drops packages with no launcher activity.
            .take(limit)
            .toList()
    }

    /** Opens [app] in its own profile, resuming its task if it is already running. */
    fun launch(app: AppEntry): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, null, null)
        true
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
            icon = iconCache.getOrPut(key) { info.getBadgedIcon(0) },
            component = info.componentName,
            user = user,
            isWork = user != me,
            available = user == me || !userManager.isQuietModeEnabled(user),
        )
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }
}
