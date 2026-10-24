package dev.indla.island.service.listener

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.data.icons.IconStore
import dev.indla.island.engine.IslandEngine
import dev.indla.island.model.LiveContent
import dev.indla.island.model.MediaCommand
import dev.indla.island.model.MediaSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follows the device's media sessions (allowed because Island is a notification listener) and
 * feeds the most relevant one to the engine. Event-driven: no polling, the controller calls back.
 */
@Singleton
class MediaWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: Lazy<IslandEngine>,
    private val icons: IconStore,
    private val apps: AppInfoCache,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private var component: ComponentName? = null
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        choose(sessions.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            component?.let { c -> choose(runCatching { manager.getActiveSessions(c) }.getOrDefault(emptyList())) }
        }
    }

    fun start(listener: ComponentName) {
        component = listener
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            choose(manager.getActiveSessions(listener))
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        component = null
        engine.get().onMedia(null)
    }

    fun sessionActivity(packageName: String): android.app.PendingIntent? =
        controller?.takeIf { it.packageName == packageName }?.sessionActivity

    fun command(packageName: String, command: MediaCommand) {
        val c = controller?.takeIf { it.packageName == packageName } ?: return
        val controls = c.transportControls
        when (command) {
            MediaCommand.PlayPause ->
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            MediaCommand.Next -> controls.skipToNext()
            MediaCommand.Previous -> controls.skipToPrevious()
            is MediaCommand.SeekTo -> controls.seekTo(command.positionMs)
        }
    }

    /** Prefer the session that is playing; otherwise the most recent one. */
    private fun choose(sessions: List<MediaController>) {
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val metadata = c?.metadata
        val playback = c?.playbackState
        val live = playback?.state in LIVE_STATES
        if (c == null || metadata == null || playback == null || !live) {
            engine.get().onMedia(null)
            return
        }
        val pkg = c.packageName
        val track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return engine.get().onMedia(null)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val actions = playback.actions
        engine.get().onMedia(
            MediaSnapshot(
                packageName = pkg,
                appLabel = apps.label(pkg),
                media = LiveContent.Media(
                    track = track,
                    artist = artist,
                    playing = playback.state == PlaybackState.STATE_PLAYING || playback.state == PlaybackState.STATE_BUFFERING,
                    durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                    positionMs = playback.position.coerceAtLeast(0),
                    positionAtElapsedMs = playback.lastPositionUpdateTime,
                    speed = playback.playbackSpeed,
                    artKey = art(pkg, track, metadata),
                    canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                    canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
                ),
                appAccentArgb = apps.accent(pkg),
                isSelfTest = pkg == context.packageName,
            ),
        )
    }

    private fun art(pkg: String, track: String, metadata: MediaMetadata): String? {
        val key = "media|$pkg|${track.hashCode()}"
        if (icons.contains(key)) return key
        val bitmap: Bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val size = ART_PX.coerceAtMost(maxOf(bitmap.width, bitmap.height))
        icons.put(key, if (bitmap.width > size || bitmap.height > size) bitmap.scale(size, size) else bitmap)
        return key
    }

    private companion object {
        const val ART_PX = 192
        val LIVE_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        )
    }
}
