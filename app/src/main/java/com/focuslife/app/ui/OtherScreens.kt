package com.focuslife.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslife.app.data.SleepScheduleEntity
import com.focuslife.app.detection.ShortVideoRules
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.UsageMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable fun FocusScreen(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val active = sessions.firstOrNull { it.state == "RUNNING" || it.state == "PAUSED" }
    var custom by remember { mutableStateOf("35") }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active?.id, active?.state) {
        now = SystemClock.elapsedRealtime()
        while (active?.state == "RUNNING") { delay(1000); now = SystemClock.elapsedRealtime() }
    }
    Page("Focus Mode") {
        if (active == null) {
            Text("Choose your focus duration")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.startFocus(25) }) { Text("25 min") }
                Button(onClick = { vm.startFocus(50) }) { Text("50 min") }
            }
            OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(3) }, label = { Text("Custom minutes (1–240)") }, singleLine = true)
            Button(onClick = { vm.startFocus(custom.toIntOrNull()?.coerceIn(1, 240) ?: 25) }) { Text("Start custom session") }
            Text("During a focus session, opening a limited app shows a reminder to get back to work.", color = Muted, style = MaterialTheme.typography.bodySmall)
        } else {
            val elapsed = UsageMath.elapsed(active.plannedMs, active.elapsedBeforeRunMs, active.resumedAtElapsedRealtime, now)
            val remaining = (active.plannedMs - elapsed).coerceAtLeast(0)
            LaunchedEffect(active.id, remaining == 0L) { if (remaining == 0L) vm.finish(active) }
            Tile(if (active.state == "RUNNING") "Focusing — time left" else "Paused — time left", clock(remaining), "Session length: ${active.plannedMs / 60_000} min")
            LinearProgressIndicator(progress = { (elapsed.toFloat() / active.plannedMs.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.pauseResume(active) }) { Text(if (active.state == "RUNNING") "Pause" else "Resume") }
                OutlinedButton(onClick = { vm.cancel(active) }) { Text("Stop") }
            }
        }
        Section("Session history")
        if (sessions.isEmpty()) Text("No sessions yet.", color = Muted)
        val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        sessions.take(20).forEach {
            Row(Modifier.fillMaxWidth()) {
                Text("${format.format(Date(it.startedAt))} · ${it.plannedMs / 60_000} min", Modifier.weight(1f), color = Muted)
                Text(it.state.lowercase().replaceFirstChar { c -> c.uppercase() }, color = if (it.state == "COMPLETED") Accent else Muted)
            }
        }
    }
}

@Composable fun StatisticsScreen(vm: FocusViewModel) {
    val weekly by vm.weekly.collectAsState(); val usage by vm.usage.collectAsState(); val sessions by vm.sessions.collectAsState()
    val reelsToday by vm.shortVideoToday.collectAsState(); val reelsWeek by vm.shortVideoWeek.collectAsState()
    Page("Statistics") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Tile("Reels blocked today", "${reelsToday.sumOf { it.blocked }}") }
            Box(Modifier.weight(1f)) { Tile("Videos watched", "${reelsToday.sumOf { it.videos }}") }
        }

        Section("Scroll time, last 7 days")
        val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        val perDay = days.map { d -> reelsWeek.filter { it.date == d.toString() }.sumOf { it.feedMs } }
        BarChart(days.map { it.format(DateTimeFormatter.ofPattern("EEE")) }, perDay)
        Text("Reels blocked this week: ${reelsWeek.sumOf { it.blocked }}", color = Muted)

        Section("Short videos today, by app")
        val labels = ShortVideoRules.apps.mapValues { it.value.label }
        if (reelsToday.isEmpty()) Text("Nothing yet today.", color = Muted)
        reelsToday.sortedByDescending { it.feedMs }.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(labels[row.packageName] ?: row.packageName, Modifier.weight(1f))
                Text("${row.videos} videos · ${clock(row.feedMs)} · ${row.blocked} blocked", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("Today's app usage")
        val top = usage.filter { it.durationMs >= 60_000L }.sortedByDescending { it.durationMs }.take(15)
        if (top.isEmpty()) Text("No usage recorded yet. Make sure Usage Access is turned on.", color = Muted)
        top.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(rememberAppLabel(row.packageName), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mins(row.durationMs), color = Muted)
            }
        }

        Section("Screen time, last 7 days")
        val screen = days.map { d -> weekly.firstOrNull { it.date == d.toString() }?.screenMs ?: 0L }
        BarChart(days.map { it.format(DateTimeFormatter.ofPattern("EEE")) }, screen)
        Tile("Completed focus sessions", sessions.count { it.state == "COMPLETED" }.toString())
    }
}

@Composable private fun BarChart(labels: List<String>, values: List<Long>) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    AppCard {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v >= 60_000L) "${v / 60_000L}m" else "", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 0.85f))
                            .background(if (i == values.lastIndex) Accent else Accent.copy(alpha = 0.45f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                    )
                    Text(labels[i], style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable fun GoalsScreen(vm: FocusViewModel) {
    val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState()
    var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("20") }
    Page("Daily Goals") {
        goals.forEach { goal ->
            val completed = completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0
            AppCard {
                Text("${goal.name} — $completed / ${goal.targetMinutes} min", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { (completed.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.progress(goal, (completed - 5).coerceAtLeast(0)) }) { Text("−5 min") }
                    Button(onClick = { vm.progress(goal, completed + 5) }) { Text("+5 min") }
                }
                var targetEdit by remember(goal.id) { mutableStateOf(goal.targetMinutes.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = targetEdit, onValueChange = { targetEdit = it.filter(Char::isDigit).take(4) }, modifier = Modifier.weight(1f), label = { Text("Target (minutes)") }, singleLine = true)
                    TextButton(onClick = { targetEdit.toIntOrNull()?.let { vm.editGoal(goal, it) } }) { Text("Save") }
                }
            }
        }
        Section("Add a goal")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit).take(4) }, label = { Text("Daily target in minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addGoal(name, target.toIntOrNull() ?: 20); name = "" }) { Text("Add goal") }
    }
}

@Composable fun SleepScreen(vm: FocusViewModel) {
    val sleep by vm.dao.sleep().collectAsState(initial = null)
    var bed by remember(sleep) { mutableStateOf(sleep?.bedtime ?: "22:30") }
    var wake by remember(sleep) { mutableStateOf(sleep?.wakeTime ?: "06:30") }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val timeFormat = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    Page("Sleep Routine", "A routine planner, not a medical sleep tracker.") {
        OutlinedTextField(value = bed, onValueChange = { bed = it.take(5) }, label = { Text("Bedtime (HH:mm)") }, singleLine = true)
        OutlinedTextField(value = wake, onValueChange = { wake = it.take(5) }, label = { Text("Wake time (HH:mm)") }, singleLine = true)
        Button(onClick = {
            if (timeFormat.matches(bed) && timeFormat.matches(wake)) { scope.launch { vm.dao.putSleep(SleepScheduleEntity(bedtime = bed, wakeTime = wake)) }; message = "Saved." }
            else message = "Use 24-hour time, for example 22:30."
        }) { Text("Save routine") }
        if (message.isNotEmpty()) Text(message, color = Muted)
        Text("Tip: add a \"Sleep\" focus schedule to block reels at night.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun SettingsScreen(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions, onRules: () -> Unit, onSchedules: () -> Unit, onGoals: () -> Unit, onSleep: () -> Unit, onDetection: () -> Unit) {
    val settings by vm.settings.collectAsState(); val light by vm.light.collectAsState()
    val cfg = vm.blocker.collectAsState().value ?: BlockerConfig()
    var confirm by remember { mutableStateOf(false) }
    Page("Settings") {
        PermissionsCard(perms, actions)
        AppCard {
            ClickableRow("Blocking rules", "Curious & Timed limits, X videos, browser blocker", onRules)
            HorizontalDivider()
            ClickableRow("Focus schedules", "Block reels at set times", onSchedules)
            HorizontalDivider()
            ClickableRow("Daily goals", null, onGoals)
            HorizontalDivider()
            ClickableRow("Sleep routine", null, onSleep)
            HorizontalDivider()
            ClickableRow("Detection check", "See what the blocker detects (for troubleshooting)", onDetection)
        }
        AppCard {
            SwitchRow("Daily limit monitoring", null, settings?.monitoringEnabled ?: true, onChange = vm::toggleMonitoring)
            SwitchRow("Light theme", null, light) { vm.toggleTheme() }
            if (cfg.isPaused(System.currentTimeMillis())) OutlinedButton(onClick = vm::resume) { Text("Resume blocking now") }
        }
        Text("Privacy first: your data stays on this phone. No account, analytics or ads.", color = Muted)
        OutlinedButton(onClick = { confirm = true }) { Text("Delete all local data") }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently removes usage history, reels statistics, schedules, goals, focus sessions and settings.") },
            confirmButton = { TextButton(onClick = { vm.deleteData(); confirm = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
