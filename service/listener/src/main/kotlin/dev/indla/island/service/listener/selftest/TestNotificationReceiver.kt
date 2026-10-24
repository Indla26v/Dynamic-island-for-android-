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
    @Inject lateinit var live: TestLiveActivities

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)
                if (text.isNullOrBlank()) poster.cancel() else poster.postReplyEcho(text)
            }
            ACTION_READ -> poster.cancel()
            ACTION_CALL_ANSWER -> live.answerCall()
            ACTION_CALL_END -> live.endCall()
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.indla.island.selftest.REPLY"
        const val ACTION_READ = "dev.indla.island.selftest.READ"
        const val ACTION_CALL_ANSWER = "dev.indla.island.selftest.CALL_ANSWER"
        const val ACTION_CALL_END = "dev.indla.island.selftest.CALL_END"
        const val KEY_REPLY = "reply_text"
    }
}
