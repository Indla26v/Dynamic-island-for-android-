package dev.indla.island.data.rules

import dev.indla.island.model.AppRule
import dev.indla.island.model.DefaultApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuleRepository @Inject constructor(
    private val dao: AppRuleDao,
) {
    /** Only apps the user configured; everything else falls back to [DefaultApps.defaultRule]. */
    val rules: Flow<Map<String, AppRule>> = dao.observeAll()
        .map { list -> list.associate { it.packageName to it.toModel() } }
        .distinctUntilChanged()

    suspend fun rule(packageName: String): AppRule =
        dao.get(packageName)?.toModel() ?: DefaultApps.defaultRule(packageName)

    suspend fun save(rule: AppRule) = dao.upsert(AppRuleEntity.from(rule))

    suspend fun saveAll(rules: List<AppRule>) = dao.upsertAll(rules.map(AppRuleEntity::from))

    suspend fun mute(packageName: String) = save(rule(packageName).copy(show = false))
}
