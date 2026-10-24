package dev.indla.island.service.listener

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.indla.island.data.OverlayKeeper

/**
 * When the phone force-stops Island, Android switches its accessibility service off with it and
 * the island quietly disappears. If Island can't switch it back on itself (no adb grant), this
 * says so right away, once per drop, with a tap straight to Island's switch.
 */
internal object DroppedNotice {
    private const val CHANNEL_ID = "island_status"

    fun showIfDropped(context: Context) {
        if (!OverlayKeeper.wasOn(context) || OverlayKeeper.isEnabled(context)) return
        if (OverlayKeeper.noticeShown(context) || !canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island status", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when the phone switched Island off"
            },
        )
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, OverlayKeeper.component(context).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (details.resolveActivity(context.packageManager) != null) {
            details
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val open = PendingIntent.getActivity(context, 0, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Island was switched off")
            .setContentText("The phone stopped Island in the background. Tap to turn it back on.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The phone stopped Island in the background, and Android turned its accessibility switch off. " +
                        "Tap to turn it back on. Open Island to make it switch itself back on next time.",
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(OverlayKeeper.NOTICE_ID, n)
        OverlayKeeper.setNoticeShown(context, true)
    }

    private fun canPost(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
