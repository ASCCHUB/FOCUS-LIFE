package com.focuslife.app

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.focuslife.app.ui.FocusViewModel
import com.focuslife.app.data.*
import com.focuslife.app.domain.UsageMath
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val dark = Color(0xFF080A12)
private val card = Color(0xFF111827)
private val green = Color(0xFF34D399)
private val gray = Color(0xFF9CA3AF)
private val monitored = listOf("YouTube" to "com.google.android.youtube", "Instagram" to "com.instagram.android", "Facebook" to "com.facebook.katana", "TikTok" to "com.zhiliaoapp.musically", "X" to "com.twitter.android")
@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); com.focuslife.app.worker.UsageWorker.schedule(this); setContent {
        val vm: FocusViewModel = hiltViewModel()
        SideEffect { refreshCallback = vm::refresh }
        val light by vm.light.collectAsState()
        MaterialTheme(colorScheme = if (light) lightColorScheme(primary = Color(0xFF047857)) else darkColorScheme(primary = green, background = dark, surface = card, onBackground = Color.White)) {
            val nav = rememberNavController()
            Scaffold(bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { listOf("Home", "Block", "Focus", "Statistics", "Settings").forEach { name -> NavigationBarItem(selected = nav.currentBackStackEntryAsState().value?.destination?.route == name, onClick = { nav.navigate(name) { launchSingleTop = true; popUpTo("Home") } }, icon = { Text(name.take(1)) }, label = { Text(name) }) } } }) { padding ->
                NavHost(nav, startDestination = "Home", modifier = Modifier.padding(padding)) {
                    composable("Home") { Home(vm, onPermissions = { nav.navigate("Settings") }, onGoals = { nav.navigate("Goals") }) }
                    composable("Block") { Block(vm, ::openUsageSettings, ::openAccessibilitySettings) }
                    composable("Focus") { Focus(vm) }
                    composable("Statistics") { Statistics(vm) }
                    composable("Settings") { SettingsScreen(vm, ::openUsageSettings, ::openAccessibilitySettings, onGoals = { nav.navigate("Goals") }, onSleep = { nav.navigate("Sleep") }) }
                    composable("Goals") { Goals(vm) }
                    composable("Sleep") { Sleep(vm) }
                }
            }
        }
    } }
    private fun openUsageSettings() { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    private fun openAccessibilitySettings() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    override fun onResume() { super.onResume(); if (::refreshCallback.isInitialized) refreshCallback() }
    private lateinit var refreshCallback: () -> Unit
}
@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); content() } }
@Composable private fun Tile(title: String, value: String, subtitle: String = "") { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(title, color = gray); Spacer(Modifier.height(8.dp)); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); if (subtitle.isNotBlank()) Text(subtitle, color = gray) } } }
private fun mins(ms: Long) = "${ms / 60_000L} min"
@Composable private fun Home(vm: FocusViewModel, onPermissions: () -> Unit, onGoals: () -> Unit) {
    val summary by vm.summary.collectAsState(); val settings by vm.settings.collectAsState(); val granted by vm.permission.collectAsState(); val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState(); val weekly by vm.weekly.collectAsState()
    val limit = settings?.dailyLimitMinutes ?: 60
    Page("Good day 👋") {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = gray)
        if (!granted) { Text("Allow Usage Access to display real screen-time statistics. No usage data leaves your phone."); Button(onClick = onPermissions) { Text("Set up permissions") } }
        else {
            Tile("Entertainment time remaining", mins(UsageMath.allowance(limit, summary?.distractingMs ?: 0)), "Daily allowance: $limit min")
            Tile("Today's screen time", mins(summary?.screenMs ?: 0))
            Tile("Distracting app usage", mins(summary?.distractingMs ?: 0))
            Tile("Focus score", "Not yet available", "Requires an established baseline and scoring model")
            Tile("Time saved", "Not yet available", "A historical baseline is needed")
            Text("Weekly screen time", fontWeight = FontWeight.Bold)
            weekly.forEach { Text("${it.date}: ${mins(it.screenMs)}", color = gray) }
        }
        Text("Daily goals", fontWeight = FontWeight.Bold)
        goals.forEach { goal -> val completed = completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0; Text("${goal.name}: $completed / ${goal.targetMinutes} min"); LinearProgressIndicator(progress = { (completed.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()) }
        OutlinedButton(onClick = onGoals) { Text("Manage goals") }
    }
}
@Composable private fun Block(vm: FocusViewModel, openUsage: () -> Unit, openAccessibility: () -> Unit) {
    val limits by vm.limits.collectAsState(); val settings by vm.settings.collectAsState(); val ctx = androidx.compose.ui.platform.LocalContext.current
    Page("App Control") {
        Text("Choose which installed apps count toward your entertainment allowance. YouTube Shorts and Instagram Reels are included in their parent apps.", color = gray)
        monitored.forEach { (name, pkg) ->
            val installed = remember(pkg) { try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name); Text(if (installed) pkg else "Not installed", color = gray) }; Switch(checked = limits.any { it.packageName == pkg && it.enabled }, onCheckedChange = { vm.toggleApp(pkg, it) }, enabled = installed) }
        }
        Text("Daily limit: ${settings?.dailyLimitMinutes ?: 60} min")
        Slider(value = (settings?.dailyLimitMinutes ?: 60).toFloat(), onValueChange = { vm.setLimit(it.toInt().coerceAtLeast(15)) }, valueRange = 15f..180f, steps = 10)
        Button(onClick = openUsage) { Text("Grant Usage Access") }
        OutlinedButton(onClick = openAccessibility) { Text("Configure optional interruption service") }
        Text("Accessibility permission is optional. Focus Life only observes the foreground app package name and never reads screen contents.", color = gray)
    }
}
@Composable private fun Focus(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState(); val active = sessions.firstOrNull { it.state == "RUNNING" || it.state == "PAUSED" }
    var custom by remember { mutableStateOf("35") }
    Page("Focus Mode") {
        if (active == null) {
            Text("Choose your focus duration")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { vm.startFocus(25) }) { Text("25 min") }; Button(onClick = { vm.startFocus(50) }) { Text("50 min") } }
            OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(3) }, label = { Text("Custom minutes") })
            Button(onClick = { vm.startFocus(custom.toIntOrNull()?.coerceIn(1, 240) ?: 25) }) { Text("Start custom session") }
        } else {
            val remaining = UsageMath.elapsed(active.plannedMs, active.elapsedBeforeRunMs, active.resumedAtElapsedRealtime, android.os.SystemClock.elapsedRealtime())
            Tile("Session ${active.state.lowercase()}", mins((active.plannedMs - remaining).coerceAtLeast(0)))
            Button(onClick = { vm.pauseResume(active) }) { Text(if (active.state == "RUNNING") "Pause" else "Resume") }
            Button(onClick = { vm.finish(active) }, enabled = remaining >= active.plannedMs) { Text("Complete session") }
        }
        Text("Session history", fontWeight = FontWeight.Bold)
        sessions.take(20).forEach { Text("${java.util.Date(it.startedAt)} — ${it.plannedMs / 60_000} min — ${it.state}", color = gray) }
    }
}
@Composable private fun Statistics(vm: FocusViewModel) {
    val weekly by vm.weekly.collectAsState(); val usage by vm.usage.collectAsState(); val sessions by vm.sessions.collectAsState(); val completions by vm.completions.collectAsState()
    Page("Statistics") {
        Tile("Today's tracked apps", "${usage.size}")
        Text("App usage", fontWeight = FontWeight.Bold)
        usage.sortedByDescending { it.durationMs }.forEach { Text("${it.packageName}: ${mins(it.durationMs)}") }
        Text("Last 7 days", fontWeight = FontWeight.Bold)
        weekly.forEach { Tile(it.date, mins(it.screenMs), "Entertainment: ${mins(it.distractingMs)}") }
        Tile("Completed focus sessions", sessions.count { it.state == "COMPLETED" }.toString())
        Tile("Today's logged goals", completions.size.toString())
        Text("No invented history: only recorded device and user-entered data appears here.", color = gray)
    }
}
@Composable private fun Goals(vm: FocusViewModel) {
    val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState()
    var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("20") }
    Page("Daily Goals") {
        goals.forEach { goal ->
            val completed = completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0
            Text("${goal.name} — $completed / ${goal.targetMinutes} min")
            var targetEdit by remember(goal.id) { mutableStateOf(goal.targetMinutes.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = targetEdit, onValueChange = { targetEdit = it.filter(Char::isDigit).take(4) }, modifier = Modifier.weight(1f), label = { Text("Target (minutes)") }); TextButton(onClick = { targetEdit.toIntOrNull()?.let { vm.editGoal(goal, it) } }) { Text("Save") } }
            LinearProgressIndicator(progress = { (completed.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { vm.progress(goal, (completed - 5).coerceAtLeast(0)) }) { Text("−5 min") }; Button(onClick = { vm.progress(goal, completed + 5) }) { Text("+5 min") } }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New goal") })
        OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit) }, label = { Text("Daily target in minutes") })
        Button(onClick = { vm.addGoal(name, target.toIntOrNull() ?: 20); name = "" }) { Text("Add goal") }
    }
}
@Composable private fun Sleep(vm: FocusViewModel) {
    val sleep by vm.dao.sleep().collectAsState(initial = null)
    var bed by remember(sleep) { mutableStateOf(sleep?.bedtime ?: "22:30") }
    var wake by remember(sleep) { mutableStateOf(sleep?.wakeTime ?: "06:30") }
    val scope = rememberCoroutineScope()
    Page("Sleep Routine") {
        Text("A behavioral routine planner, not a medical sleep tracker.", color = gray)
        OutlinedTextField(value = bed, onValueChange = { bed = it }, label = { Text("Bedtime (HH:mm)") })
        OutlinedTextField(value = wake, onValueChange = { wake = it }, label = { Text("Wake time (HH:mm)") })
        Button(onClick = { scope.launch { if (Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(bed) && Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(wake)) vm.dao.putSleep(SleepScheduleEntity(bedtime = bed, wakeTime = wake)) } }) { Text("Save routine") }
    }
}
@Composable private fun SettingsScreen(vm: FocusViewModel, openUsage: () -> Unit, openAccessibility: () -> Unit, onGoals: () -> Unit, onSleep: () -> Unit) {
    val settings by vm.settings.collectAsState(); val granted by vm.permission.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Page("Settings") {
        Text("Privacy first: usage records stay on this device. No account, analytics or ad SDK.", color = gray)
        Text("Usage Access: ${if (granted) "Granted" else "Not granted"}")
        Button(onClick = openUsage) { Text("Manage Usage Access") }
        OutlinedButton(onClick = openAccessibility) { Text("Manage Accessibility Service") }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Monitoring enabled", modifier = Modifier.weight(1f)); Switch(checked = settings?.monitoringEnabled ?: true, onCheckedChange = vm::toggleMonitoring) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Light theme", modifier = Modifier.weight(1f)); Switch(checked = vm.light.collectAsState().value, onCheckedChange = { vm.toggleTheme() }) }
        OutlinedButton(onClick = onGoals) { Text("Manage daily goals") }
        OutlinedButton(onClick = onSleep) { Text("Sleep schedule") }
        Text("Deleting data is irreversible.", color = gray)
        OutlinedButton(onClick = { confirm = true }) { Text("Delete all local data") }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete all data?") }, text = { Text("This permanently removes Focus Life usage history, goals and focus sessions.") }, confirmButton = { TextButton(onClick = { vm.deleteData(); confirm = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
    }
}
