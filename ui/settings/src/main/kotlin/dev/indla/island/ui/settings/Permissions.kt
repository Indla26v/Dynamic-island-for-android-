package dev.indla.island.ui.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.indla.island.data.OverlayKeeper
import dev.indla.island.service.listener.IslandNotificationListener
import dev.indla.island.service.overlay.IslandAccessibilityService

data class PermissionStatus(
    val notificationAccess: Boolean = false,
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val postNotifications: Boolean = false,
    /** Android 13+ blocks the accessibility switch for apps installed from a file until allowed. */
    val restricted: Boolean = false,
    /** Optional: caller and sender photos from the phone's contacts. */
    val contacts: Boolean = false,
    /** Optional (Nearby devices): headphones' own name and battery when they connect. */
    val bluetooth: Boolean = false,
    /** The overlay ran before and is off now: the phone force-stopped Island. */
    val overlayDropped: Boolean = false,
    /** WRITE_SECURE_SETTINGS was granted over adb, so Island switches itself back on. */
    val autoRestore: Boolean = false,
    /** It was dropped before; keep the how-to-keep-it-on card until it's handled or hidden. */
    val keepOnTips: Boolean = false,
) {
    val essentials get() = notificationAccess && accessibility
}

object Permissions {
    fun status(context: Context): PermissionStatus {
        val dropped = OverlayKeeper.wasOn(context) && !accessibilityEnabled(context)
        if (dropped) OverlayKeeper.noteDropped(context)
        return snapshot(context, dropped)
    }

    private fun snapshot(context: Context, dropped: Boolean) = PermissionStatus(
        notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
        accessibility = accessibilityEnabled(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        postNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        restricted = restrictedSettings(context),
        contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        bluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED,
        overlayDropped = dropped,
        autoRestore = OverlayKeeper.canRestore(context),
        keepOnTips = OverlayKeeper.showTips(context),
    )

    /** The one-time command that lets Island switch its accessibility service back on. */
    fun restoreCommand(context: Context) = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /**
     * Whether "restricted settings" is (likely) blocking the accessibility switch. Apps can't read
     * the `access_restricted_settings` app-op itself (it needs a system permission), so:
     * - Android 13+ tags file installs as DOWNLOADED_FILE / LOCAL_FILE, and those are restricted;
     * - and when the user already tried the switch and came back with it still off, on an app
     *   that didn't come from a store, the help is shown too (some OEMs report OTHER as source).
     * Play and USB (adb) installs are never restricted.
     */
    private fun restrictedSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).packageSource }.getOrNull()
        if (source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE || source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE) return true
        return attemptedAccessibility(context) && source != PackageInstaller.PACKAGE_SOURCE_STORE
    }

    private fun prefs(context: Context) = context.getSharedPreferences("island_setup", Context.MODE_PRIVATE)

    private fun attemptedAccessibility(context: Context) = prefs(context).getBoolean(KEY_ATTEMPTED, false)

    private fun accessibilityEnabled(context: Context): Boolean {
        val target = IslandAccessibilityService.component(context)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val running = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == target.packageName && it.resolveInfo.serviceInfo.name == target.className }
        if (running) return true
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(target.flattenToString(), true) || it.equals(target.flattenToShortString(), true) }
    }

    fun openNotificationAccess(context: Context) {
        val component = IslandNotificationListener.component(context).flattenToString()
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        } else {
            null
        }
        launch(context, detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * Straight to Island's own switch where Settings supports it (the details screen takes the
     * service component), otherwise the accessibility list.
     */
    fun openAccessibility(context: Context) {
        prefs(context).edit().putBoolean(KEY_ATTEMPTED, true).apply()
        val component = IslandAccessibilityService.component(context).flattenToString()
        launch(
            context,
            Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, component),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
    }

    /** Where Island's switch lives on this phone, in the words the phone's Settings uses. */
    fun accessibilityPath(manufacturer: String = Build.MANUFACTURER): String = when (manufacturer.lowercase()) {
        "vivo", "iqoo" -> "Settings → Shortcuts & accessibility → Accessibility → Island"
        "samsung" -> "Settings → Accessibility → Installed apps → Island"
        "xiaomi", "redmi", "poco" -> "Settings → Additional settings → Accessibility → Downloaded apps → Island"
        "oneplus", "oppo", "realme" -> "Settings → Additional settings → Accessibility → Downloaded apps → Island"
        "huawei", "honor" -> "Settings → Accessibility features → Accessibility → Island"
        "motorola" -> "Settings → Accessibility → Island"
        else -> "Settings → Accessibility → Island"
    }

    /** Sideloaded APKs on Android 13+: restricted settings are unlocked from App info's ⋮ menu. */
    fun openAppInfo(context: Context) = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    @Suppress("BatteryLife") // Island has no foreground service; this exemption keeps its listeners bound.
    fun requestBatteryExemption(context: Context) = launch(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val KEY_ATTEMPTED = "accessibility_attempted"

    private fun launch(context: Context, vararg intents: Intent?) {
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}

/** Extra steps aggressive OEM skins need beyond "Unrestricted" battery. */
object OemBatterySteps {
    fun forManufacturer(manufacturer: String = Build.MANUFACTURER): Pair<String, List<String>> =
        when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" -> "Xiaomi / HyperOS" to listOf(
                "Security app → Permissions → Autostart → turn on Island",
                "App info → Battery saver → No restrictions",
                "Recent apps → long-press Island → Lock",
            )
            "samsung" -> "Samsung One UI" to listOf(
                "Settings → Battery → Background usage limits → Never sleeping apps → add Island",
                "App info → Battery → Unrestricted",
            )
            "oneplus", "oppo", "realme" -> "OnePlus / OPPO / realme" to listOf(
                "App info → Battery usage → Allow background activity",
                "Settings → Apps → Auto launch → turn on Island",
            )
            "vivo", "iqoo" -> "vivo / iQOO" to listOf(
                "Settings → Battery → Background power consumption management → Island → Allow",
                "i Manager → App manager → Autostart → turn on Island",
                "Recent apps → pull down on Island's card → Lock",
            )
            "huawei", "honor" -> "Huawei / Honor" to listOf(
                "Settings → Battery → App launch → Island → Manage manually, all three on",
            )
            else -> "Your phone" to listOf("Setting battery to Unrestricted is all Island needs here.")
        }
}
