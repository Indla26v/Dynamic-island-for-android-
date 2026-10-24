package dev.indla.island.service.listener.selftest

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Try it" live activities posted by Island itself: an incoming call you can answer, a download
 * that fills up, and a music session with working controls. Everything goes through the real
 * listener, MediaSession and engine paths, so it doubles as an end-to-end check.
 */
@Singleton
class TestLiveActivities @Inject constructor(
    @ApplicationContext private val context: Context,
    private val poster: TestNotificationPoster,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var download: Job? = null
    private var session: MediaSession? = null
    private var sessionTimeout: Job? = null
    private var track = 0

    fun incomingCall() {
        if (!poster.canPost()) return
        poster.ensureChannel()
        val n = baseCall()
            .setContentText("Mobile")
            .addAction(0, "Decline", receiver(TestNotificationReceiver.ACTION_CALL_END, 11))
            .addAction(0, "Answer", receiver(TestNotificationReceiver.ACTION_CALL_ANSWER, 12))
            .build()
        manager.notify(CALL_ID, n)
    }

    fun answerCall() {
        if (!poster.canPost()) return
        val n = baseCall()
            .setContentText("Ongoing call")
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(0, "Hang up", receiver(TestNotificationReceiver.ACTION_CALL_END, 13))
            .build()
        manager.notify(CALL_ID, n)
    }

    fun endCall() = manager.cancel(CALL_ID)

    fun startDownload() {
        if (!poster.canPost()) return
        poster.ensureChannel()
        download?.cancel()
        download = scope.launch {
            for (percent in 0..100 step 4) {
                manager.notify(DOWNLOAD_ID, downloadBuilder(percent).build())
                delay(600)
            }
            manager.notify(
                DOWNLOAD_ID,
                NotificationCompat.Builder(context, TestNotificationPoster.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("island-demo.apk")
                    .setContentText("Download complete")
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    /** A real MediaSession, so the island's media controls are exercised for real. */
    fun playMusic() {
        val s = session ?: MediaSession(context, "IslandTest").also { created ->
            created.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = setPlaying(true)
                override fun onPause() = setPlaying(false)
                override fun onSkipToNext() = changeTrack(1)
                override fun onSkipToPrevious() = changeTrack(-1)
                override fun onSeekTo(pos: Long) = setState(playing(), pos)
            })
            session = created
        }
        s.isActive = true
        updateMetadata()
        setState(true, 0)
        sessionTimeout?.cancel()
        sessionTimeout = scope.launch {
            delay(SESSION_LIFETIME_MS)
            stopMusic()
        }
    }

    fun stopMusic() {
        sessionTimeout?.cancel()
        session?.run {
            isActive = false
            release()
        }
        session = null
    }

    private var position = 0L
    private var positionAt = 0L

    private fun playing() = session?.controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun currentPosition(): Long {
        if (!playing()) return position
        return position + (SystemClock.elapsedRealtime() - positionAt)
    }

    private fun setPlaying(play: Boolean) = setState(play, currentPosition())

    private fun changeTrack(step: Int) {
        track = (track + step).mod(TRACKS.size)
        updateMetadata()
        setState(true, 0)
    }

    private fun setState(play: Boolean, pos: Long) {
        position = pos
        positionAt = SystemClock.elapsedRealtime()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setState(if (play) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, 1f, positionAt)
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO,
                )
                .build(),
        )
    }

    private fun updateMetadata() {
        val (title, artist, colors) = TRACKS[track]
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 211_000)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, art(colors))
                .build(),
        )
    }

    private fun art(colors: Pair<Int, Int>): Bitmap {
        val size = 192
        val bitmap = createBitmap(size, size)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors.first, colors.second, Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    private fun baseCall() = NotificationCompat.Builder(context, TestNotificationPoster.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_phone_call)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setContentTitle("Tania Castillo")
        .setOngoing(true)
        .setOnlyAlertOnce(true)

    private fun downloadBuilder(percent: Int) = NotificationCompat.Builder(context, TestNotificationPoster.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentTitle("island-demo.apk")
        .setContentText("$percent% · ${((100 - percent) * 0.6 / 4).toInt() + 1} s left")
        .setProgress(100, percent, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)

    private fun receiver(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, TestNotificationReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CALL_ID = 7_002
        const val DOWNLOAD_ID = 7_003
        const val SESSION_LIFETIME_MS = 5 * 60_000L
        val TRACKS = listOf(
            Triple("Entropy", "Beach Bunny", 0xFF7C4DFF.toInt() to 0xFFFF4081.toInt()),
            Triple("Sunset Drive", "The Islanders", 0xFFFF9100.toInt() to 0xFFD500F9.toInt()),
            Triple("Night Bus", "Low Tide", 0xFF00B8D4.toInt() to 0xFF304FFE.toInt()),
        )
    }
}
