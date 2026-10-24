package dev.indla.island.service.listener

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.indla.island.data.OverlayKeeper
import dev.indla.island.engine.IslandEngine
import javax.inject.Inject

/**
 * Reads notifications and hands them to the engine. Reading (icons, contact photos, inflating
 * custom views) happens on one background thread so the island's animations never wait on it;
 * results reach the engine on the main thread in the order they were posted, removals included.
 * No wakelocks, no polling, no foreground service.
 */
@AndroidEntryPoint
class IslandNotificationListener : NotificationListenerService() {
    @Inject lateinit var engine: IslandEngine
    @Inject lateinit var extractor: NotificationExtractor
    @Inject lateinit var performer: NotificationActionPerformer
    @Inject lateinit var recorder: FixtureRecorder
    @Inject lateinit var media: MediaWatcher

    /** One thread with a Looper: some custom views need one to inflate. */
    private val readerThread = HandlerThread("island-reader", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val reader = Handler(readerThread.looper)
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        OverlayKeeper.restoreIfDropped(this)
        performer.attach(this)
        engine.addRedactedMarkers(extractor.systemRedactedMarkers())
        val ranking = currentRanking
        val existing = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
        existing.forEach(performer::remember)
        reader.post {
            val raws = existing.mapNotNull { runCatching { extractor.extract(it, ranking, groupHasChildren = false) }.getOrNull() }
            main.post { engine.onSnapshot(raws) }
        }
        engine.setListenerConnected(true)
        media.start(component(this))
    }

    override fun onDestroy() {
        readerThread.quitSafely()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        engine.setListenerConnected(false)
        performer.detach()
        media.stop()
        // OEM skins kill and unbind us; ask to come back instead of waiting for a reboot.
        requestRebind(component(this))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        performer.remember(sbn)
        reader.post {
            try {
                recorder.record(sbn, rankingMap)
                val raw = extractor.extract(sbn, rankingMap, groupHasChildren(sbn))
                main.post { engine.onPosted(raw) }
            } catch (e: RuntimeException) {
                // One malformed notification must never take the listener down.
                Log.w(TAG, "Could not read ${sbn.packageName} notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        performer.forget(sbn.key)
        // Through the reader too, so it can't overtake a post of the same key still being read.
        reader.post { main.post { engine.onRemoved(sbn.key) } }
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
