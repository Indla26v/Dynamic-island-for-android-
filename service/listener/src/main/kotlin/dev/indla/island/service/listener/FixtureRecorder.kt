package dev.indla.island.service.listener

import android.app.Notification
import android.app.Person
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class FixtureEntry(val postTime: Long, val packageName: String, val title: String, val json: String)

/**
 * Debug-only: keeps the last few notifications' extras as JSON so they can be exported and
 * turned into parser test fixtures. Off in release builds; memory only, never written to disk.
 */
@Singleton
class FixtureRecorder @Inject constructor() {
    @Volatile var enabled: Boolean = false

    private val _entries = MutableStateFlow<List<FixtureEntry>>(emptyList())
    val entries: StateFlow<List<FixtureEntry>> = _entries.asStateFlow()

    fun record(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (!enabled) return
        val n = sbn.notification
        val json = JSONObject().apply {
            put("key", sbn.key)
            put("packageName", sbn.packageName)
            put("postTime", sbn.postTime)
            put("groupKey", sbn.groupKey)
            put("flags", n.flags)
            put("isGroupSummary", n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            put("category", n.category)
            put("channelId", n.channelId)
            put("template", n.extras.getString(Notification.EXTRA_TEMPLATE))
            put("actions", JSONArray().apply {
                n.actions.orEmpty().forEach { a ->
                    put(JSONObject().apply {
                        put("title", a.title?.toString())
                        put("remoteInputs", a.remoteInputs?.size ?: 0)
                        put("contextual", a.isContextual)
                        put("semantic", a.semanticAction)
                    })
                }
            })
            val ranking = Ranking()
            if (rankingMap?.getRanking(sbn.key, ranking) == true) {
                put("ranking", JSONObject().apply {
                    put("importance", ranking.importance)
                    put("ambient", ranking.isAmbient)
                    put("matchesInterruptionFilter", ranking.matchesInterruptionFilter())
                })
            }
            put("extras", bundleToJson(n.extras))
        }
        val entry = FixtureEntry(
            postTime = sbn.postTime,
            packageName = sbn.packageName,
            title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            json = json.toString(2),
        )
        _entries.value = (listOf(entry) + _entries.value).take(MAX)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun bundleToJson(bundle: Bundle): JSONObject = JSONObject().apply {
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            put(key, valueToJson(bundle.get(key)))
        }
    }

    private fun valueToJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is CharSequence -> value.toString()
        is Number, is Boolean -> value
        is Bundle -> bundleToJson(value)
        is Person -> JSONObject().put("name", value.name?.toString()).put("key", value.key)
        is Icon -> "<icon type=${value.type}>"
        is Bitmap -> "<bitmap ${value.width}x${value.height}>"
        is Array<*> -> JSONArray().apply { value.forEach { put(valueToJson(it)) } }
        is List<*> -> JSONArray().apply { value.forEach { put(valueToJson(it)) } }
        is IntArray -> JSONArray(value.toList())
        is LongArray -> JSONArray(value.toList())
        is Parcelable -> "<${value.javaClass.simpleName}>"
        else -> value.toString()
    }

    private companion object {
        const val MAX = 30
    }
}
