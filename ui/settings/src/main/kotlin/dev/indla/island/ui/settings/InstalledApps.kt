package dev.indla.island.ui.settings

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.indla.island.model.DefaultApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppEntry(val packageName: String, val label: String, val isMessaging: Boolean)

/** Launchable apps, via the `<queries>` launcher intent (no QUERY_ALL_PACKAGES). */
@Singleton
class InstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in DefaultApps.messaging) }
            .sortedWith(compareByDescending<AppEntry> { it.isMessaging }.thenBy { it.label.lowercase() })
    }
}
