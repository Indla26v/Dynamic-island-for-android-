package dev.indla.island

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.indla.island.service.listener.FixtureRecorder
import javax.inject.Inject

@HiltAndroidApp
class IslandApplication : Application() {
    @Inject lateinit var recorder: FixtureRecorder

    override fun onCreate() {
        super.onCreate()
        // The fixture exporter exists for building parser tests; release builds record nothing.
        recorder.enabled = BuildConfig.DEBUG
    }
}
