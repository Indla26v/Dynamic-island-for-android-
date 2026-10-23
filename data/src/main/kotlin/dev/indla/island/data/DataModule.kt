package dev.indla.island.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.indla.island.data.rules.AppRuleDao
import dev.indla.island.data.rules.AppRuleDatabase
import javax.inject.Singleton

private val Context.islandSettings: DataStore<Preferences> by preferencesDataStore(name = "island_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.islandSettings

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppRuleDatabase =
        Room.databaseBuilder(context, AppRuleDatabase::class.java, "island.db").build()

    @Provides
    fun ruleDao(db: AppRuleDatabase): AppRuleDao = db.rules()
}
