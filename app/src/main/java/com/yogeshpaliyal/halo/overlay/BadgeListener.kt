package com.yogeshpaliyal.halo.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Notification access, used only to know WHICH apps have notifications waiting so the menu can
 * show a dot on them. Titles, text and everything else in a notification are never read or kept.
 * Ongoing notifications (music, downloads, Halo's own) don't count.
 */
class BadgeListener : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() = NotificationBadges.update(emptySet())
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) = refresh()

    private fun refresh() {
        val me = Process.myUserHandle()
        val um = getSystemService(UserManager::class.java)
        val keys = runCatching { activeNotifications }.getOrNull().orEmpty()
            .asSequence()
            .filter { it.isClearable && !it.isOngoing && it.packageName != packageName }
            // Same key format as favourites: package, plus "@profile" for work apps.
            .map { if (it.user == me) it.packageName else "${it.packageName}@${um.getSerialNumberForUser(it.user)}" }
            .toHashSet()
        NotificationBadges.update(keys)
    }
}

object NotificationBadges {
    /** Keys (package, or package@profile) of apps with notifications waiting. */
    @Volatile
    var keys: Set<String> = emptySet()
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun update(next: Set<String>) {
        if (next == keys) return
        keys = next
        listeners.forEach { it() }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens Halo's own Notification access page where Android has one, else the list. */
    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, BadgeListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}
