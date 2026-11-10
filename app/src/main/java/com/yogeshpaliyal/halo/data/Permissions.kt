package com.yogeshpaliyal.halo.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun overlaySettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun usageSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Halo's App info page, where the ⋮ menu holds "Allow restricted settings". */
    fun appInfoIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Android 13+ (overlay and usage access from Android 15) blocks these special permissions
     * for apps installed outside an app store, until the user taps "Allow restricted settings"
     * in App info. Apps can't read that state, so we just show the hint on those versions.
     */
    val mayBeRestricted: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
