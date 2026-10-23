package dev.indla.island.service.listener

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.ApplicationScope
import dev.indla.island.data.rules.AppRuleRepository
import dev.indla.island.engine.IslandEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries out what the engine asks for, using the real `Notification` behind each key.
 * PendingIntents are sent with background-activity-start allowed: the bound accessibility
 * service is what lets Island open a chat from the overlay.
 */
@Singleton
class NotificationActionPerformer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rules: AppRuleRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : IslandEffects {
    private var listener = WeakReference<NotificationListenerService>(null)
    private val posted = object : LinkedHashMap<String, StatusBarNotification>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatusBarNotification>?) = size > 64
    }

    fun attach(service: NotificationListenerService) {
        listener = WeakReference(service)
    }

    fun detach() {
        listener.clear()
        posted.clear()
    }

    fun remember(sbn: StatusBarNotification) {
        posted[sbn.key] = sbn
    }

    fun forget(key: String) {
        posted.remove(key)
    }

    override fun openItem(key: String) {
        val sbn = posted[key] ?: return
        val sent = sbn.notification.contentIntent?.let { send(it) } == true
        if (!sent) {
            context.packageManager.getLaunchIntentForPackage(sbn.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it) }
            }
        }
        if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) dismissNotification(key)
    }

    override fun invokeAction(key: String, actionIndex: Int) {
        val action = action(key, actionIndex) ?: return
        send(action.actionIntent)
    }

    override fun reply(key: String, actionIndex: Int, text: String) {
        val action = action(key, actionIndex) ?: return
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }?.takeIf { it.isNotEmpty() } ?: return
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs.toTypedArray(), fillIn, results)
        RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        send(action.actionIntent, fillIn)
    }

    override fun dismissNotification(key: String) {
        runCatching { listener.get()?.cancelNotification(key) }
    }

    override fun muteApp(packageName: String) {
        scope.launch { rules.mute(packageName) }
    }

    private fun action(key: String, index: Int): Notification.Action? =
        posted[key]?.notification?.actions?.getOrNull(index)

    private fun send(intent: PendingIntent?, fillIn: Intent? = null): Boolean {
        intent ?: return false
        return try {
            intent.send(context, 0, fillIn, null, null, null, sendOptions())
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "PendingIntent was cancelled", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun sendOptions(): Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    } else {
        null
    }

    private companion object {
        const val TAG = "IslandActions"
    }
}
