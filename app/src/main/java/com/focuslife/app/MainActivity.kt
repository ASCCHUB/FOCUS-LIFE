package com.focuslife.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focuslife.app.data.SleepScheduleEntity
import com.focuslife.app.domain.UsageMath
import com.focuslife.app.service.FocusAccessibilityService
import com.focuslife.app.ui.FocusViewModel
import com.focuslife.app.worker.UsageWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

private val dark = Color(0xFF080A12)
private val card = Color(0xFF111827)
private val green = Color(0xFF34D399)
private val amber = Color(0xFFF59E0B)
private val gray = Color(0xFF9CA3AF)
private val monitored = listOf("YouTube" to "com.google.android.youtube", "Instagram" to "com.instagram.android", "Facebook" to "com.facebook.katana", "TikTok" to "com.zhiliaoapp.musically", "X" to "com.twitter.android", "Snapchat" to "com.snapchat.android")

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("Home", "Home", Icons.Filled.Home),
    Tab("Block", "Apps", Icons.Filled.Block),
    Tab("Focus", "Focus", Icons.Filled.Timer),
    Tab("Statistics", "Stats", Icons.Filled.BarChart),
    Tab("Settings", "Settings", Icons.Filled.Settings),
)

data class PermissionState(val usage: Boolean, val accessibility: Boolean, val notifications: Boolean) {
    val allGranted get() = usage && accessibility && notifications
}
class PermissionActions(val openUsage: () -> Unit, val openAccessibility: () -> Unit, val openAppInfo: () -> Unit, val requestNotifications: () -> Unit)

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private var accessibilityOn by mutableStateOf(false)
    private var notificationsOn by mutableStateOf(false)
    private var refreshCallback: (() -> Unit)? = null
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        if (!granted) openNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        UsageWorker.schedule(this)
        refreshPermissions()
        val actions = PermissionActions(::openUsageSettings, ::openAccessibilitySettings, ::openAppInfo, ::requestNotifications)
        setContent {
            val vm: FocusViewModel = hiltViewModel()
            SideEffect { refreshCallback = vm::refresh }
            val light by vm.light.collectAsState()
            val usageOn by vm.permission.collectAsState()
            val perms = PermissionState(usageOn, accessibilityOn, notificationsOn)
            MaterialTheme(colorScheme = if (light) lightColorScheme(primary = Color(0xFF047857)) else darkColorScheme(primary = green, background = dark, surface = card, onBackground = Color.White, onSurface = Color.White)) {
                val nav = rememberNavController()
                val current = nav.currentBackStackEntryAsState().value?.destination?.route
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = current == tab.route,
                                    onClick = { nav.navigate(tab.route) { launchSingleTop = true; popUpTo("Home") } },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = "Home", modifier = Modifier.padding(padding)) {
                        composable("Home") { Home(vm, perms, actions, onGoals = { nav.navigate("Goals") }) }
                        composable("Block") { Block(vm, perms, actions) }
                        composable("Focus") { Focus(vm) }
                        composable("Statistics") { Statistics(vm) }
                        composable("Settings") { SettingsScreen(vm, perms, actions, onGoals = { nav.navigate("Goals") }, onSleep = { nav.navigate("Sleep") }) }
                        composable("Goals") { Goals(vm) }
                        composable("Sleep") { Sleep(vm) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshCallback?.invoke()
    }

    private fun refreshPermissions() {
        accessibilityOn = isAccessibilityServiceOn()
        notificationsOn = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun isAccessibilityServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val mine = ComponentName(this, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun openUsageSettings() = start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    private fun openAccessibilitySettings() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    private fun openAppInfo() = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    private fun openNotificationSettings() = start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun requestNotifications() {
        val prefs = getSharedPreferences("permission_requests", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notifications_asked", false)) {
            prefs.edit().putBoolean("notifications_asked", true).apply()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openNotificationSettings()
    }

    private fun start(vararg intents: Intent) {
        for (intent in intents) {
            try { startActivity(intent); return } catch (_: Exception) { }
        }
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
    }
}

@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun Tile(title: String, value: String, subtitle: String = "") {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = gray)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (subtitle.isNotBlank()) Text(subtitle, color = gray)
        }
    }
}
@Composable private fun Section(title: String) { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
private fun mins(ms: Long): String { val m = ms / 60_000L; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
private fun clock(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }

@Composable private fun rememberAppLabel(pkg: String): String {
    val pm = LocalContext.current.packageManager
    return remember(pkg) { try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }
}

@Composable private fun PermissionsCard(perms: PermissionState, actions: PermissionActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(if (perms.allGranted) "Permissions" else "Finish setup")
            PermissionRow("Usage Access", "Required. Lets Focus Life measure your screen time.", perms.usage, "Grant", actions.openUsage)
            PermissionRow("Accessibility", "Shows the reminder screen when your daily limit is reached.", perms.accessibility, "Turn on", actions.openAccessibility)
            PermissionRow("Notifications", "Sends reminders as you approach your limit.", perms.notifications, "Allow", actions.requestNotifications)
            if (!perms.usage || !perms.accessibility) {
                HorizontalDivider()
                Text("Switch greyed out, or it says \"Restricted setting\"?", fontWeight = FontWeight.SemiBold)
                Text("Android blocks these for apps installed from an APK file. Tap Open App info, then the ⋮ menu at the top right, choose \"Allow restricted settings\" and confirm. Then come back and turn the permission on.", color = gray)
                OutlinedButton(onClick = actions.openAppInfo) { Text("Open App info") }
            }
        }
    }
}
@Composable private fun PermissionRow(title: String, description: String, granted: Boolean, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = if (granted) green else amber)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = gray, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) Text("On", color = green, fontWeight = FontWeight.SemiBold) else Button(onClick = onClick) { Text(button) }
    }
}

@Composable private fun Home(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions, onGoals: () -> Unit) {
    val summary by vm.summary.collectAsState(); val settings by vm.settings.collectAsState(); val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState(); val weekly by vm.weekly.collectAsState(); val limits by vm.limits.collectAsState()
    val limit = settings?.dailyLimitMinutes ?: 60
    val used = summary?.distractingMs ?: 0L
    Page("Good day 👋") {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = gray)
        if (!perms.allGranted) PermissionsCard(perms, actions)
        if (perms.usage) {
            if (limits.none { it.enabled }) Text("Tip: open the Apps tab and switch on the apps you want to limit.", color = amber)
            Tile("Entertainment time remaining", mins(UsageMath.allowance(limit, used)), "Daily allowance: $limit min")
            LinearProgressIndicator(progress = { (used.toFloat() / (limit * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Tile("Today's screen time", mins(summary?.screenMs ?: 0))
            Tile("Limited apps used today", mins(used))
            if (weekly.isNotEmpty()) {
                Section("Last 7 days")
                weekly.forEach { day ->
                    val label = try { LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, MMM d")) } catch (_: Exception) { day.date }
                    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(mins(day.screenMs), color = gray) }
                }
            }
        }
        Section("Daily goals")
        goals.forEach { goal ->
            val completed = completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0
            Text("${goal.name}: $completed / ${goal.targetMinutes} min")
            LinearProgressIndicator(progress = { (completed.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = onGoals) { Text("Manage goals") }
    }
}

@Composable private fun Block(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions) {
    val limits by vm.limits.collectAsState(); val settings by vm.settings.collectAsState(); val ctx = LocalContext.current
    var sliderValue by remember(settings?.dailyLimitMinutes) { mutableFloatStateOf((settings?.dailyLimitMinutes ?: 60).toFloat()) }
    Page("App Control") {
        Text("Choose which apps count toward your daily entertainment allowance. YouTube Shorts and Instagram Reels count as part of their apps.", color = gray)
        monitored.forEach { (name, pkg) ->
            val installed = remember(pkg) { try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text(if (installed) "Installed" else "Not installed", color = gray, style = MaterialTheme.typography.bodySmall) }
                Switch(checked = limits.any { it.packageName == pkg && it.enabled }, onCheckedChange = { vm.toggleApp(pkg, it) }, enabled = installed)
            }
        }
        HorizontalDivider()
        Text("Daily limit: ${sliderValue.toInt()} min", fontWeight = FontWeight.SemiBold)
        Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { vm.setLimit(sliderValue.toInt().coerceAtLeast(15)) }, valueRange = 15f..180f, steps = 10)
        if (!perms.usage || !perms.accessibility) PermissionsCard(perms, actions)
        Text("Focus Life only sees which app is open. It never reads what's on your screen.", color = gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Focus(vm: FocusViewModel) {
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
        if (sessions.isEmpty()) Text("No sessions yet.", color = gray)
        val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        sessions.take(20).forEach {
            Row(Modifier.fillMaxWidth()) {
                Text("${format.format(Date(it.startedAt))} · ${it.plannedMs / 60_000} min", Modifier.weight(1f), color = gray)
                Text(it.state.lowercase().replaceFirstChar { c -> c.uppercase() }, color = if (it.state == "COMPLETED") green else gray)
            }
        }
    }
}

@Composable private fun Statistics(vm: FocusViewModel) {
    val weekly by vm.weekly.collectAsState(); val usage by vm.usage.collectAsState(); val sessions by vm.sessions.collectAsState(); val completions by vm.completions.collectAsState()
    Page("Statistics") {
        Section("Today's app usage")
        val top = usage.filter { it.durationMs >= 60_000L }.sortedByDescending { it.durationMs }.take(15)
        if (top.isEmpty()) Text("No usage recorded yet. Make sure Usage Access is turned on.", color = gray)
        top.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(rememberAppLabel(row.packageName), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mins(row.durationMs), color = gray)
            }
        }
        Section("Last 7 days")
        weekly.forEach { Tile(it.date, mins(it.screenMs), "Limited apps: ${mins(it.distractingMs)}") }
        Tile("Completed focus sessions", sessions.count { it.state == "COMPLETED" }.toString())
        Tile("Goals logged today", completions.size.toString())
    }
}

@Composable private fun Goals(vm: FocusViewModel) {
    val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState()
    var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("20") }
    Page("Daily Goals") {
        goals.forEach { goal ->
            val completed = completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0
            Text("${goal.name} — $completed / ${goal.targetMinutes} min", fontWeight = FontWeight.SemiBold)
            var targetEdit by remember(goal.id) { mutableStateOf(goal.targetMinutes.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = targetEdit, onValueChange = { targetEdit = it.filter(Char::isDigit).take(4) }, modifier = Modifier.weight(1f), label = { Text("Target (minutes)") }, singleLine = true)
                TextButton(onClick = { targetEdit.toIntOrNull()?.let { vm.editGoal(goal, it) } }) { Text("Save") }
            }
            LinearProgressIndicator(progress = { (completed.toFloat() / goal.targetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.progress(goal, (completed - 5).coerceAtLeast(0)) }) { Text("−5 min") }
                Button(onClick = { vm.progress(goal, completed + 5) }) { Text("+5 min") }
            }
            HorizontalDivider()
        }
        Section("Add a goal")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit).take(4) }, label = { Text("Daily target in minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addGoal(name, target.toIntOrNull() ?: 20); name = "" }) { Text("Add goal") }
    }
}

@Composable private fun Sleep(vm: FocusViewModel) {
    val sleep by vm.dao.sleep().collectAsState(initial = null)
    var bed by remember(sleep) { mutableStateOf(sleep?.bedtime ?: "22:30") }
    var wake by remember(sleep) { mutableStateOf(sleep?.wakeTime ?: "06:30") }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val timeFormat = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    Page("Sleep Routine") {
        Text("A routine planner, not a medical sleep tracker.", color = gray)
        OutlinedTextField(value = bed, onValueChange = { bed = it.take(5) }, label = { Text("Bedtime (HH:mm)") }, singleLine = true)
        OutlinedTextField(value = wake, onValueChange = { wake = it.take(5) }, label = { Text("Wake time (HH:mm)") }, singleLine = true)
        Button(onClick = {
            if (timeFormat.matches(bed) && timeFormat.matches(wake)) { scope.launch { vm.dao.putSleep(SleepScheduleEntity(bedtime = bed, wakeTime = wake)) }; message = "Saved." }
            else message = "Use 24-hour time, for example 22:30."
        }) { Text("Save routine") }
        if (message.isNotEmpty()) Text(message, color = gray)
    }
}

@Composable private fun SettingsScreen(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions, onGoals: () -> Unit, onSleep: () -> Unit) {
    val settings by vm.settings.collectAsState(); val light by vm.light.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Page("Settings") {
        PermissionsCard(perms, actions)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Monitoring enabled", modifier = Modifier.weight(1f)); Switch(checked = settings?.monitoringEnabled ?: true, onCheckedChange = vm::toggleMonitoring) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Light theme", modifier = Modifier.weight(1f)); Switch(checked = light, onCheckedChange = { vm.toggleTheme() }) }
        OutlinedButton(onClick = onGoals, modifier = Modifier.fillMaxWidth()) { Text("Manage daily goals") }
        OutlinedButton(onClick = onSleep, modifier = Modifier.fillMaxWidth()) { Text("Sleep schedule") }
        HorizontalDivider()
        Text("Privacy first: your usage data stays on this phone. No account, analytics or ads.", color = gray)
        OutlinedButton(onClick = { confirm = true }) { Text("Delete all local data") }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently removes Focus Life usage history, goals and focus sessions.") },
            confirmButton = { TextButton(onClick = { vm.deleteData(); confirm = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
