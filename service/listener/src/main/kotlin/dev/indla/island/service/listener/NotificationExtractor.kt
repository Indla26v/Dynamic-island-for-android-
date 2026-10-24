package dev.indla.island.service.listener

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.icons.IconStore
import dev.indla.island.model.RawAction
import dev.indla.island.model.RawMessage
import dev.indla.island.model.RawNotification
import dev.indla.island.model.RawPerson
import dev.indla.island.model.RawRanking
import dev.indla.island.service.listener.selftest.TestNotificationPoster
import javax.inject.Inject
import javax.inject.Singleton

/** Flattens a `StatusBarNotification` into a [RawNotification]; caches avatars on the way. */
@Singleton
class NotificationExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val icons: IconStore,
    private val apps: AppInfoCache,
) {
    private val ranking = Ranking()

    fun extract(sbn: StatusBarNotification, rankingMap: RankingMap?, groupHasChildren: Boolean): RawNotification {
        val n = sbn.notification
        val extras = n.extras
        val pkg = sbn.packageName
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val metric = metric(extras)
        // Android 16 promoted ongoing notifications may carry their glance as short critical text.
        val criticalText = extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT)?.toString()

        return RawNotification(
            key = sbn.key,
            packageName = pkg,
            appLabel = apps.label(pkg),
            postTime = sbn.postTime,
            groupKey = sbn.groupKey,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            groupHasChildren = groupHasChildren,
            onlyAlertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            ongoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            category = n.category,
            channelId = n.channelId,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: metric?.label,
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: criticalText,
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation == true,
            messages = style?.let { messages(pkg, it) }.orEmpty(),
            actions = n.actions.orEmpty().mapIndexed { i, a ->
                RawAction(
                    index = i,
                    title = a.title?.toString().orEmpty(),
                    hasRemoteInput = a.remoteInputs?.any { it.allowFreeFormInput } == true,
                    isContextual = a.isContextual,
                )
            },
            ranking = ranking(sbn.key, rankingMap),
            template = extras.getString(Notification.EXTRA_TEMPLATE),
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax = progressMax,
            progressIndeterminate = indeterminate,
            callType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                extras.getInt(Notification.EXTRA_CALL_TYPE, 0)
            } else {
                extras.getInt("android.callType", 0)
            },
            callCanAnswer = extras.containsKey(Notification.EXTRA_ANSWER_INTENT),
            callCanDecline = extras.containsKey(Notification.EXTRA_DECLINE_INTENT),
            callCanHangUp = extras.containsKey(Notification.EXTRA_HANG_UP_INTENT),
            chronometerBase = when {
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) -> n.`when`.takeIf { it > 0 }
                else -> metric?.baseWallMs
            },
            chronometerCountDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) || metric?.countDown == true,
            largeIconKey = n.getLargeIcon()?.let { cacheIcon("$pkg|large|${sbn.key}", it) },
            appAccentArgb = apps.accent(pkg),
            isSelfTest = pkg == context.packageName && n.channelId == TestNotificationPoster.CHANNEL_ID,
        )
    }

    private class Metric(val label: String?, val baseWallMs: Long?, val countDown: Boolean)

    /**
     * Newer Clock apps post timers as `MetricStyle`: `android.metrics` holds bundles with a label
     * and a value whose `zeroElapsedRealtime` is when a countdown reaches zero (or a stopwatch
     * started). Converted to wall-clock time so it ticks like a chronometer.
     */
    private fun metric(extras: Bundle): Metric? {
        @Suppress("DEPRECATION")
        val list = (extras.get(EXTRA_METRICS) as? List<*>)?.filterIsInstance<Bundle>()?.takeIf { it.isNotEmpty() } ?: return null
        val chosen = list.getOrNull(extras.getInt(EXTRA_METRICS_CRITICAL, 0)) ?: list.first()
        val value = chosen.getBundle("value")
        val zero = value?.getLong("zeroElapsedRealtime", -1L)?.takeIf { it > 0 }
        return Metric(
            label = chosen.getCharSequence("label")?.toString(),
            baseWallMs = zero?.let { System.currentTimeMillis() + (it - SystemClock.elapsedRealtime()) },
            countDown = value?.getBoolean("countDown", false) == true,
        )
    }

    /** Android's own localized "content hidden" text, so redaction is detected in any language. */
    fun systemRedactedMarkers(): Set<String> = REDACTED_STRING_NAMES.mapNotNull { name ->
        val id = Resources.getSystem().getIdentifier(name, "string", "android")
        if (id == 0) null else runCatching { Resources.getSystem().getString(id) }.getOrNull()
    }.toSet()

    private fun messages(pkg: String, style: NotificationCompat.MessagingStyle): List<RawMessage> {
        val user = style.user
        return style.messages.map { m ->
            val person = m.person
            val fromUser = person == null ||
                (person.key != null && person.key == user.key) ||
                (person.key == null && person.name != null && person.name == user.name)
            RawMessage(
                text = m.text?.toString(),
                timestamp = m.timestamp,
                sender = if (fromUser) null else RawPerson(
                    name = person.name?.toString(),
                    key = person.key,
                    iconKey = person.icon?.let { icon ->
                        val id = person.key ?: person.name?.toString() ?: return@let null
                        cacheIcon(IconStore.personKey(pkg, id), icon)
                    },
                ),
                dataMimeType = m.dataMimeType,
            )
        }
    }

    private fun ranking(key: String, map: RankingMap?): RawRanking {
        if (map == null || !map.getRanking(key, ranking)) return RawRanking()
        return RawRanking(
            importance = ranking.importance,
            isAmbient = ranking.isAmbient,
            matchesInterruptionFilter = ranking.matchesInterruptionFilter(),
            isSuspended = ranking.isSuspended,
        )
    }

    private fun cacheIcon(key: String, icon: IconCompat): String? =
        runCatching { icon.loadDrawable(context) }.getOrNull()?.let { store(key, it) }

    private fun cacheIcon(key: String, icon: Icon): String? =
        runCatching { icon.loadDrawable(context) }.getOrNull()?.let { store(key, it) }

    private fun store(key: String, drawable: Drawable): String? {
        val bitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return null
        val scaled = if (bitmap.width > ICON_PX || bitmap.height > ICON_PX) bitmap.scale(ICON_PX, ICON_PX) else bitmap
        icons.put(key, scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled)
        return key
    }

    private companion object {
        const val ICON_PX = 128
        const val EXTRA_METRICS = "android.metrics"
        const val EXTRA_METRICS_CRITICAL = "android.metrics.criticalIndex"
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
        val REDACTED_STRING_NAMES = listOf("redacted_notification_message", "redacted_otp_notification_message")
    }
}
