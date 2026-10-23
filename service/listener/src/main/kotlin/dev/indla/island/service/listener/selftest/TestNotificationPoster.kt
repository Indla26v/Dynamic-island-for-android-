package dev.indla.island.service.listener.selftest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.service.listener.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a real MessagingStyle chat from Island itself, so "Send test notification" exercises the
 * whole path: listener, parser, engine, overlay, inline reply.
 */
@Singleton
class TestNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(round: Int = 0) {
        if (!canPost()) return
        ensureChannel()
        val sender = Person.Builder().setName(SAMPLES[round % SAMPLES.size].first).setKey("island-test").build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage("Hey, is the island showing up?", System.currentTimeMillis() - 2_000, sender)
            .addMessage(SAMPLES[round % SAMPLES.size].second, System.currentTimeMillis(), sender)
        manager().notify(NOTIFICATION_ID, builder().setStyle(style).build())
    }

    /** Called after an inline reply: append the user's message, the way chat apps do. */
    fun postReplyEcho(reply: CharSequence) {
        if (!canPost()) return
        val sender = Person.Builder().setName(SAMPLES[0].first).setKey("island-test").build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage("Hey, is the island showing up?", System.currentTimeMillis() - 2_000, sender)
            .addMessage(reply, System.currentTimeMillis(), null as Person?)
        manager().notify(NOTIFICATION_ID, builder().setStyle(style).setOnlyAlertOnce(true).build())
    }

    fun cancel() = manager().cancel(NOTIFICATION_ID)

    private fun builder(): NotificationCompat.Builder {
        val replyIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TestNotificationReceiver::class.java).setAction(TestNotificationReceiver.ACTION_REPLY),
            // RemoteInput results are filled in by the sender, so this one must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val readIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, TestNotificationReceiver::class.java).setAction(TestNotificationReceiver.ACTION_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 3, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val reply = NotificationCompat.Action.Builder(0, "Reply", replyIntent)
            .addRemoteInput(RemoteInput.Builder(TestNotificationReceiver.KEY_REPLY).setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false)
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(reply)
            .addAction(0, "Mark as read", readIntent)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.test_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.test_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Suppress("MissingPermission") // canPost() checked by every caller.
    private fun manager() = NotificationManagerCompat.from(context)

    companion object {
        const val CHANNEL_ID = "island_test"
        const val NOTIFICATION_ID = 7_001

        private val SAMPLES = listOf(
            "Island" to "Tap me to expand, then try Reply.",
            "Island" to "Swipe left or right when two things are showing.",
            "Island" to "Long press for quick actions.",
        )
    }
}
