package com.focuslife.app.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslife.app.data.*
import com.focuslife.app.domain.UsageMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel class FocusViewModel @Inject constructor(val dao: FocusDao, val usageRepository: UsageRepository, val preferences: Preferences) : ViewModel() {
    private val today = LocalDate.now().toString() // Date-bound flows are refreshed when the activity is recreated after midnight.
    val usage = dao.usage(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val summary = dao.summary(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val limits = dao.limits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = dao.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val goals = dao.goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val completions = dao.completions(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val weekly = dao.summaries(LocalDate.now().minusDays(6).toString(), today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val light = preferences.lightTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val permission = MutableStateFlow(false)
    init { viewModelScope.launch {
        if (settings.first() == null) dao.putSettings(UserSettingsEntity())
        if (goals.first().isEmpty()) listOf("Quran" to 20, "English Learning" to 20, "Professional Skills" to 30, "Exercise" to 20).forEach { dao.addGoal(GoalEntity(name = it.first, targetMinutes = it.second)) }
        refresh()
    } }
    fun refresh() { viewModelScope.launch { permission.value = usageRepository.permissionGranted(); if (permission.value) usageRepository.refresh() } }
    fun setLimit(minutes: Int) { viewModelScope.launch { dao.putSettings((settings.value ?: UserSettingsEntity()).copy(dailyLimitMinutes = minutes)) } }
    fun toggleApp(pkg: String, enabled: Boolean) { viewModelScope.launch { dao.putLimit(AppLimitEntity(pkg, enabled)); refresh() } }
    fun toggleMonitoring(value: Boolean) { viewModelScope.launch { dao.putSettings((settings.value ?: UserSettingsEntity()).copy(monitoringEnabled = value)) } }
    fun toggleTheme() { viewModelScope.launch { preferences.setLight(!light.value) } }
    fun addGoal(name: String, target: Int) { if (name.isNotBlank() && target > 0) viewModelScope.launch { dao.addGoal(GoalEntity(name = name.trim(), targetMinutes = target)) } }
    fun progress(goal: GoalEntity, minutes: Int) { viewModelScope.launch { dao.putCompletion(GoalCompletionEntity(goal.id, today, minutes.coerceAtLeast(0))) } }
    fun editGoal(goal: GoalEntity, target: Int) { if (target > 0) viewModelScope.launch { dao.updateGoal(goal.copy(targetMinutes = target)) } }
    fun startFocus(minutes: Int) { viewModelScope.launch { dao.activeSession()?.let { dao.updateSession(it.copy(state = "CANCELLED", endedAt = System.currentTimeMillis())) }; dao.addSession(FocusSessionEntity(startedAt = System.currentTimeMillis(), plannedMs = minutes * 60_000L, resumedAtElapsedRealtime = SystemClock.elapsedRealtime(), state = "RUNNING")) } }
    fun pauseResume(session: FocusSessionEntity) { viewModelScope.launch { val now = SystemClock.elapsedRealtime(); if (session.state == "RUNNING") dao.updateSession(session.copy(state = "PAUSED", elapsedBeforeRunMs = UsageMath.elapsed(session.plannedMs, session.elapsedBeforeRunMs, session.resumedAtElapsedRealtime, now), resumedAtElapsedRealtime = null)) else dao.updateSession(session.copy(state = "RUNNING", resumedAtElapsedRealtime = now)) } }
    fun finish(session: FocusSessionEntity) { viewModelScope.launch { dao.updateSession(session.copy(state = "COMPLETED", endedAt = System.currentTimeMillis(), resumedAtElapsedRealtime = null, elapsedBeforeRunMs = session.plannedMs)) } }
    fun grantExtension() { viewModelScope.launch { if (settings.value?.extensionEnabled != false) dao.grantExtension(ExtensionUsageEntity(LocalDate.now().toString())) } }
    fun deleteData() { viewModelScope.launch { dao.clearAll(); preferences.clear(); dao.putSettings(UserSettingsEntity(monitoringEnabled = false)) } }
}
