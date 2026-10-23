package dev.indla.island.service.listener

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.indla.island.engine.IslandEngine
import javax.inject.Inject

/**
 * Reads notifications and hands them to the engine. Callbacks arrive on the main thread, which
 * is the engine's thread. No wakelocks, no polling, no foreground service.
 */
@AndroidEntryPoint
class IslandNotificationListener : NotificationListenerService() {
    @Inject lateinit var engine: IslandEngine
    @Inject lateinit var extractor: NotificationExtractor
    @Inject lateinit var performer: NotificationActionPerformer
    @Inject lateinit var recorder: FixtureRecorder

    override fun onListenerConnected() {
        performer.attach(this)
        engine.addRedactedMarkers(extractor.systemRedactedMarkers())
        val ranking = currentRanking
        val existing = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
        existing.forEach(performer::remember)
        engine.onSnapshot(existing.map { extractor.extract(it, ranking, groupHasChildren = false) })
        engine.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        engine.setListenerConnected(false)
        performer.detach()
        // OEM skins kill and unbind us; ask to come back instead of waiting for a reboot.
        requestRebind(component(this))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try {
            performer.remember(sbn)
            recorder.record(sbn, rankingMap)
            engine.onPosted(extractor.extract(sbn, rankingMap, groupHasChildren(sbn)))
        } catch (e: RuntimeException) {
            // One malformed notification must never take the listener down.
            Log.w(TAG, "Could not read ${sbn.packageName} notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        performer.forget(sbn.key)
        engine.onRemoved(sbn.key)
    }

    private fun groupHasChildren(sbn: StatusBarNotification): Boolean {
        if (!sbn.isGroup || sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0) return false
        // Only summaries pay for this IPC.
        return runCatching {
            activeNotifications.orEmpty().any {
                it.groupKey == sbn.groupKey && it.key != sbn.key &&
                    it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "IslandListener"

        fun component(context: Context) = ComponentName(context, IslandNotificationListener::class.java)
    }
}
