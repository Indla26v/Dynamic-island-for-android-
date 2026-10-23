package dev.indla.island.service.listener.selftest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles Reply and Mark as read on the test notification. */
@AndroidEntryPoint
class TestNotificationReceiver : BroadcastReceiver() {
    @Inject lateinit var poster: TestNotificationPoster

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)
                if (text.isNullOrBlank()) poster.cancel() else poster.postReplyEcho(text)
            }
            ACTION_READ -> poster.cancel()
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.indla.island.selftest.REPLY"
        const val ACTION_READ = "dev.indla.island.selftest.READ"
        const val KEY_REPLY = "reply_text"
    }
}
