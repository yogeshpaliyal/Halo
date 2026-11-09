package com.yogeshpaliyal.halo.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.TimeUnit

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

class AppRepository(context: Context) {

    private val context = context.applicationContext
    private val pm = this.context.packageManager
    private val iconCache = HashMap<String, Drawable>()

    /** Every app that shows up in the app drawer, sorted by name. */
    fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull(::entryFor)
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun entryFor(pkg: String): AppEntry? = try {
        val info = pm.getApplicationInfo(pkg, 0)
        AppEntry(
            packageName = pkg,
            label = pm.getApplicationLabel(info).toString(),
            icon = iconCache.getOrPut(pkg) { pm.getApplicationIcon(info) },
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun favorites(): List<AppEntry> =
        Prefs.favorites(context).mapNotNull(::entryFor).take(Prefs.MAX_FAVORITES)

    /**
     * Most recently opened apps, newest first. The app currently on screen is left out
     * (switching to it would do nothing), and so are home screens and this app.
     */
    fun recents(limit: Int, exclude: Set<String> = emptySet()): List<AppEntry> {
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
                pkg != context.packageName &&
                    pkg != foreground &&
                    pkg !in home &&
                    pkg !in exclude &&
                    pm.getLaunchIntentForPackage(pkg) != null
            }
            .take(limit)
            .mapNotNull(::entryFor)
            .toList()
    }

    /** Opens [pkg] the same way the launcher does, resuming its task if it is already running. */
    fun launch(pkg: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
    }
}
