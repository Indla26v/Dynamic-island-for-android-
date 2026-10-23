package dev.indla.island.ui.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.indla.island.service.listener.IslandNotificationListener
import dev.indla.island.service.overlay.IslandAccessibilityService

data class PermissionStatus(
    val notificationAccess: Boolean = false,
    val accessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val postNotifications: Boolean = false,
) {
    val essentials get() = notificationAccess && accessibility
}

object Permissions {
    fun status(context: Context) = PermissionStatus(
        notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
        accessibility = accessibilityEnabled(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        postNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    )

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

    fun openAccessibility(context: Context) = launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

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

    val restrictedSettingsLikely: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
            )
            "huawei", "honor" -> "Huawei / Honor" to listOf(
                "Settings → Battery → App launch → Island → Manage manually, all three on",
            )
            else -> "Your phone" to listOf("Setting battery to Unrestricted is all Island needs here.")
        }
}
