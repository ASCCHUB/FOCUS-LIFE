package com.focuslife.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.focuslife.app.domain.BlockMode
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.LimitAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.store by preferencesDataStore("focuslife_preferences")

class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val light = booleanPreferencesKey("light_theme")
    private val mode = stringPreferencesKey("block_mode")
    private val curiousCount = intPreferencesKey("curious_count")
    private val curiousCooldown = intPreferencesKey("curious_cooldown_min")
    private val timedWatch = intPreferencesKey("timed_watch_min")
    private val timedBlock = intPreferencesKey("timed_block_min")
    private val pauseUntil = longPreferencesKey("pause_until")
    private val enabledApps = stringSetPreferencesKey("enabled_apps")
    private val xEnabled = booleanPreferencesKey("x_enabled")
    private val xWarn = intPreferencesKey("x_warn_after")
    private val xStop = intPreferencesKey("x_stop_after")
    private val xCooldown = intPreferencesKey("x_cooldown_min")
    private val browserEnabled = booleanPreferencesKey("browser_enabled")
    private val blockedDomains = stringSetPreferencesKey("blocked_domains")
    private val limitAction = stringPreferencesKey("limit_action")
    private val onboardingDone = booleanPreferencesKey("onboarding_done")

    val lightTheme = context.store.data.map { it[light] ?: false }
    val blocker: Flow<BlockerConfig> = context.store.data.map { read(it) }

    suspend fun setLight(value: Boolean) { context.store.edit { it[light] = value } }
    suspend fun updateBlocker(transform: (BlockerConfig) -> BlockerConfig) { context.store.edit { prefs -> write(prefs, transform(read(prefs))) } }
    suspend fun clear() { context.store.edit { it.clear() } }

    private fun read(p: androidx.datastore.preferences.core.Preferences): BlockerConfig {
        val d = BlockerConfig()
        return BlockerConfig(
            mode = p[mode]?.let { name -> BlockMode.entries.firstOrNull { it.name == name } } ?: d.mode,
            curiousCount = p[curiousCount] ?: d.curiousCount,
            curiousCooldownMin = p[curiousCooldown] ?: d.curiousCooldownMin,
            timedWatchMin = p[timedWatch] ?: d.timedWatchMin,
            timedBlockMin = p[timedBlock] ?: d.timedBlockMin,
            pauseUntil = p[pauseUntil] ?: d.pauseUntil,
            enabledApps = p[enabledApps] ?: d.enabledApps,
            xEnabled = p[xEnabled] ?: d.xEnabled,
            xWarnAfter = p[xWarn] ?: d.xWarnAfter,
            xStopAfter = p[xStop] ?: d.xStopAfter,
            xCooldownMin = p[xCooldown] ?: d.xCooldownMin,
            browserEnabled = p[browserEnabled] ?: d.browserEnabled,
            blockedDomains = p[blockedDomains] ?: d.blockedDomains,
            limitAction = p[limitAction]?.let { name -> LimitAction.entries.firstOrNull { it.name == name } } ?: d.limitAction,
            onboardingDone = p[onboardingDone] ?: d.onboardingDone,
        )
    }

    private fun write(p: MutablePreferences, c: BlockerConfig) {
        p[mode] = c.mode.name
        p[curiousCount] = c.curiousCount
        p[curiousCooldown] = c.curiousCooldownMin
        p[timedWatch] = c.timedWatchMin
        p[timedBlock] = c.timedBlockMin
        p[pauseUntil] = c.pauseUntil
        p[enabledApps] = c.enabledApps
        p[xEnabled] = c.xEnabled
        p[xWarn] = c.xWarnAfter
        p[xStop] = c.xStopAfter
        p[xCooldown] = c.xCooldownMin
        p[browserEnabled] = c.browserEnabled
        p[blockedDomains] = c.blockedDomains
        p[limitAction] = c.limitAction.name
        p[onboardingDone] = c.onboardingDone
    }
}
