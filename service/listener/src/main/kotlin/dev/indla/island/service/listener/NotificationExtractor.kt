package dev.indla.island.service.listener

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
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
) {
    private val labels = HashMap<String, String>()
    private val ranking = Ranking()

    fun extract(sbn: StatusBarNotification, rankingMap: RankingMap?, groupHasChildren: Boolean): RawNotification {
        val n = sbn.notification
        val extras = n.extras
        val pkg = sbn.packageName
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        return RawNotification(
            key = sbn.key,
            packageName = pkg,
            appLabel = appLabel(pkg),
            postTime = sbn.postTime,
            groupKey = sbn.groupKey,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            groupHasChildren = groupHasChildren,
            onlyAlertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            ongoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            category = n.category,
            channelId = n.channelId,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
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
            hasProgress = progressMax > 0 || indeterminate,
            largeIconKey = n.getLargeIcon()?.let { cacheIcon("$pkg|large|${sbn.key}", it) },
            isSelfTest = pkg == context.packageName && n.channelId == TestNotificationPoster.CHANNEL_ID,
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

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
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
        val REDACTED_STRING_NAMES = listOf("redacted_notification_message", "redacted_otp_notification_message")
    }
}
