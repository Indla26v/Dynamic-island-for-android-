package dev.indla.island

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.indla.island.data.ApplicationScope
import dev.indla.island.data.SettingsRepository
import dev.indla.island.data.rules.AppRuleRepository
import dev.indla.island.engine.IslandEngine
import dev.indla.island.service.listener.NotificationActionPerformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Main thread: the engine is confined to it, as are both services' callbacks. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun engine(
        @ApplicationScope scope: CoroutineScope,
        settings: SettingsRepository,
        rules: AppRuleRepository,
        effects: NotificationActionPerformer,
    ): IslandEngine = IslandEngine(scope, settings.settings, rules.rules).also { it.effects = effects }
}
