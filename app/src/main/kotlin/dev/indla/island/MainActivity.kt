package dev.indla.island

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.metrics.performance.JankStats
import dagger.hilt.android.AndroidEntryPoint
import dev.indla.island.ui.settings.IslandApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { IslandApp(showDebug = BuildConfig.DEBUG) }
        // Settings screens are an Activity, so JankStats can watch them directly; the overlay
        // has no Window and uses its own Choreographer tracker (OverlayFrameStats).
        jankStats = JankStats.createAndTrack(window) { frame ->
            if (frame.isJank && BuildConfig.DEBUG) Log.d("IslandJank", "Settings jank: ${frame.frameDurationUiNanos / 1_000_000} ms ${frame.states}")
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        super.onPause()
    }
}
