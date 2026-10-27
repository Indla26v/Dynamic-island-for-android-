package dev.indla.island.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Keeps the accessibility service (the overlay) switched on.
 *
 * Android switches an app's accessibility service off when the app is force-stopped, which is
 * what vivo, Xiaomi and OPPO battery managers and "clean all" in recents do. Island remembers
 * that the service was on; if it finds it dropped and the user granted WRITE_SECURE_SETTINGS
 * once over adb, it puts itself back in the enabled list, and the system rebinds it at once.
 * Without the grant, Home explains how to stop the phone from doing it.
 */
object OverlayKeeper {
    private const val SERVICE = "dev.indla.island.service.overlay.IslandAccessibilityService"
    private const val PREFS = "island_health"
    private const val KEY_WAS_ON = "overlay_was_on"
    private const val KEY_AUTO = "overlay_auto_restore"
    private const val KEY_DROPS = "overlay_drops"
    private const val KEY_DROP_SEEN = "overlay_drop_seen"
    private const val KEY_TIPS_HIDDEN = "overlay_tips_hidden"
    private const val KEY_NOTICE = "overlay_notice_shown"
    private const val TAG = "OverlayKeeper"
    /** The "Island was switched off" notification. */
    const val NOTICE_ID = 7_100

    fun component(context: Context) = ComponentName(context.packageName, SERVICE)

    /** Called by the service once it is up: from now on, "off" means the system dropped it. */
    fun markRunning(context: Context) {
        prefs(context).edit().putBoolean(KEY_WAS_ON, true).putBoolean(KEY_NOTICE, false).apply()
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTICE_ID)
    }

    fun noticeShown(context: Context) = prefs(context).getBoolean(KEY_NOTICE, false)

    fun setNoticeShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTICE, shown).apply()
    }

    fun wasOn(context: Context) = prefs(context).getBoolean(KEY_WAS_ON, false)

    /** The service was found dropped at least once: worth showing how to stop that. */
    fun noteDropped(context: Context) {
        prefs(context).edit().putBoolean(KEY_DROP_SEEN, true).putBoolean(KEY_TIPS_HIDDEN, false).apply()
    }

    fun showTips(context: Context) = prefs(context).let { it.getBoolean(KEY_DROP_SEEN, false) && !it.getBoolean(KEY_TIPS_HIDDEN, false) }

    fun hideTips(context: Context) {
        prefs(context).edit().putBoolean(KEY_TIPS_HIDDEN, true).apply()
    }

    /** How many times Island found the service dropped and put it back. */
    fun restoredCount(context: Context) = prefs(context).getInt(KEY_DROPS, 0)

    fun canRestore(context: Context) =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun autoRestore(context: Context) = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoRestore(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, on).apply()
    }

    fun isEnabled(context: Context): Boolean {
        val target = component(context)
        return enabledList(context).any { it.equals(target.flattenToString(), true) || it.equals(target.flattenToShortString(), true) }
    }

    /**
     * Turns the service back on if it was on before, auto-restore is allowed and the grant is
     * there. Returns true when it did.
     */
    fun restoreIfDropped(context: Context): Boolean {
        if (!wasOn(context) || isEnabled(context)) return false
        if (!autoRestore(context) || !canRestore(context)) {
            Log.i(TAG, "accessibility service was dropped; can't restore (${if (canRestore(context)) "auto-restore off" else "no WRITE_SECURE_SETTINGS grant"})")
            return false
        }
        return runCatching {
            val resolver = context.contentResolver
            val list = enabledList(context) + component(context).flattenToString()
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            prefs(context).edit().putInt(KEY_DROPS, restoredCount(context) + 1).apply()
            Log.i(TAG, "accessibility service was dropped; restored")
            true
        }.getOrElse {
            Log.w(TAG, "restore failed", it)
            false
        }
    }

    private fun enabledList(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
