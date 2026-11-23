package com.focuslife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslife.app.detection.BrowserUrlMatcher
import com.focuslife.app.detection.ShortVideoRules
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.LimitAction
import com.focuslife.app.domain.ScheduleMath
import com.focuslife.app.service.FocusAccessibilityService
import kotlinx.coroutines.delay
import java.time.DayOfWeek

private val limitApps = listOf(
    "YouTube" to BlockerConfig.YOUTUBE, "Instagram" to BlockerConfig.INSTAGRAM, "Facebook" to BlockerConfig.FACEBOOK,
    "TikTok" to BlockerConfig.TIKTOK, "X" to BlockerConfig.X, "Snapchat" to BlockerConfig.SNAPCHAT,
)

@Composable private fun isInstalled(pkg: String): Boolean {
    val pm = LocalContext.current.packageManager
    return remember(pkg) { try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false } }
}

/** Apps tab: which feeds to block, which apps count toward the daily limit, and what happens at the limit. */
@Composable fun AppsScreen(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions) {
    val cfg = vm.blocker.collectAsState().value ?: BlockerConfig()
    val limits by vm.limits.collectAsState(); val settings by vm.settings.collectAsState()
    var sliderValue by remember(settings?.dailyLimitMinutes) { mutableFloatStateOf((settings?.dailyLimitMinutes ?: 60).toFloat()) }
    Page("App Control") {
        if (!perms.required) PermissionsCard(perms, actions, showAll = false)

        Section("Block short videos in")
        AppCard {
            ShortVideoRules.userFacing.forEach { (pkg, label) ->
                val installed = isInstalled(pkg) || (pkg == BlockerConfig.TIKTOK && isInstalled(BlockerConfig.TIKTOK_ALT))
                SwitchRow(label, if (installed) "Installed" else "Not installed", checked = pkg in cfg.enabledApps) { on ->
                    val group = if (pkg == BlockerConfig.TIKTOK) setOf(BlockerConfig.TIKTOK, BlockerConfig.TIKTOK_ALT) else setOf(pkg)
                    vm.updateBlocker { it.copy(enabledApps = if (on) it.enabledApps + group else it.enabledApps - group) }
                }
            }
            Text("Chats, search and profiles keep working. Only the reels/shorts feeds are blocked.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        Section("Daily time limit")
        AppCard {
            Text("These apps count toward your daily entertainment allowance: ALL of their time, not only reels. Leave YouTube off here if you only want Shorts blocked.", color = Muted, style = MaterialTheme.typography.bodySmall)
            limitApps.forEach { (name, pkg) ->
                val installed = isInstalled(pkg)
                SwitchRow(name, if (installed) null else "Not installed", checked = limits.any { it.packageName == pkg && it.enabled }, enabled = installed) { vm.toggleApp(pkg, it) }
            }
            HorizontalDivider()
            Text("Daily limit: ${sliderValue.toInt()} min", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 15, 30, 60).forEach { m -> FilterChip(selected = sliderValue.toInt() == m, onClick = { sliderValue = m.toFloat(); vm.setLimit(m) }, label = { Text("$m") }) }
            }
            Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { vm.setLimit(sliderValue.toInt().coerceAtLeast(1)) }, valueRange = 1f..180f)
            Text("When my daily limit is reached", fontWeight = FontWeight.SemiBold)
            RadioGroup(LimitAction.entries, cfg.limitAction, { it.title }, { it.description }) { action -> vm.updateBlocker { it.copy(limitAction = action) } }
        }
    }
}

/** Fine-tuning for each mode, the X rule and the browser blocker. */
@Composable fun RulesScreen(vm: FocusViewModel, onDetection: () -> Unit = {}) {
    val cfg = vm.blocker.collectAsState().value ?: BlockerConfig()
    var domain by remember { mutableStateOf("") }
    Page("Blocking rules") {
        Section("Curious mode")
        AppCard {
            Stepper("Reels allowed", cfg.curiousCount, 1..5) { v -> vm.updateBlocker { it.copy(curiousCount = v) } }
            Stepper("Break length", cfg.curiousCooldownMin, 1..60, " min") { v -> vm.updateBlocker { it.copy(curiousCooldownMin = v) } }
        }
        Section("Timed mode")
        AppCard {
            Stepper("Watch for", cfg.timedWatchMin, 1..60, " min") { v -> vm.updateBlocker { it.copy(timedWatchMin = v) } }
            Stepper("Then block for", cfg.timedBlockMin, 1..120, " min") { v -> vm.updateBlocker { it.copy(timedBlockMin = v) } }
        }
        Section("X (Twitter) videos")
        AppCard {
            SwitchRow("Video limit on X", "Warn, then close X after a number of videos", cfg.xEnabled) { on -> vm.updateBlocker { it.copy(xEnabled = on) } }
            if (cfg.xEnabled) {
                SwitchRow("Strict: only 1 video", "Swiping to the 2nd video closes X", cfg.xStopAfter == 2) { on ->
                    vm.updateBlocker { it.copy(xStopAfter = if (on) 2 else 5, xWarnAfter = if (on) 1 else 2) }
                }
                Stepper("Warn after", cfg.xWarnAfter, 1..(cfg.xStopAfter - 1).coerceAtLeast(1), " videos") { v -> vm.updateBlocker { it.copy(xWarnAfter = v) } }
                Stepper("Close X at video no.", cfg.xStopAfter, 2..20, "") { v -> vm.updateBlocker { it.copy(xStopAfter = v, xWarnAfter = it.xWarnAfter.coerceAtMost(v - 1)) } }
                Stepper("Break length", cfg.xCooldownMin, 1..120, " min") { v -> vm.updateBlocker { it.copy(xCooldownMin = v) } }
            }
            Text("Scheduled focus times use Block All for X too.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onDetection, modifier = Modifier.fillMaxWidth()) { Text("X not blocked? Run a detection check") }
        Section("Browser blocker")
        AppCard {
            SwitchRow("Block video pages in browsers", "Facebook reels/watch, Instagram reels, YouTube Shorts, TikTok, X videos in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera", cfg.browserEnabled) { on -> vm.updateBlocker { it.copy(browserEnabled = on) } }
            Text("Block whole websites", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("e.g. facebook.com") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val d = BrowserUrlMatcher.normalizeDomain(domain)
                    if (d.contains('.')) vm.updateBlocker { it.copy(blockedDomains = it.blockedDomains + d) }
                    domain = ""
                }) { Text("Add") }
            }
            cfg.blockedDomains.sorted().forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, Modifier.weight(1f))
                    TextButton(onClick = { vm.updateBlocker { it.copy(blockedDomains = it.blockedDomains - d) } }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable fun SchedulesScreen(vm: FocusViewModel) {
    val schedules by vm.schedules.collectAsState()
    var label by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("22:30") }
    var end by remember { mutableStateOf("06:30") }
    var days by remember { mutableIntStateOf(ScheduleMath.ALL_DAYS) }
    var error by remember { mutableStateOf("") }
    Page("Focus schedules", "Reels are fully blocked during these times, even if paused.") {
        if (schedules.isEmpty()) Text("No schedules yet.", color = Muted)
        schedules.forEach { s ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label, fontWeight = FontWeight.Bold)
                        Text("${ScheduleMath.format(s.startMinute)} – ${ScheduleMath.format(s.endMinute)} · ${ScheduleMath.daysLabel(s.daysMask)}", color = Muted)
                    }
                    Switch(checked = s.enabled, onCheckedChange = { vm.toggleSchedule(s, it) })
                }
                TextButton(onClick = { vm.deleteSchedule(s) }) { Text("Delete") }
            }
        }
        Section("Add a schedule")
        AppCard {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (e.g. Sleep, Study, Prayer)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it.take(5) }, label = { Text("Start (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = end, onValueChange = { end = it.take(5) }, label = { Text("End (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("Days", fontWeight = FontWeight.SemiBold)
            FlowRowDays(days) { days = it }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val s = ScheduleMath.parse(start); val e = ScheduleMath.parse(end)
                error = when {
                    s == null || e == null -> "Use 24-hour time, for example 22:30."
                    s == e -> "Start and end can't be the same."
                    days == 0 -> "Pick at least one day."
                    else -> { vm.addSchedule(label, s, e, days); label = ""; "" }
                }
            }) { Text("Add schedule") }
        }
    }
}

@Composable private fun FlowRowDays(mask: Int, onChange: (Int) -> Unit) {
    val all = DayOfWeek.values().toList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(all.take(4), all.drop(4)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { day ->
                    val bit = ScheduleMath.dayBit(day)
                    FilterChip(selected = (mask and bit) != 0, onClick = { onChange(mask xor bit) }, label = { Text(day.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }) })
                }
            }
        }
    }
}

/** First-launch walkthrough. */
@Composable fun OnboardingScreen(perms: PermissionState, actions: PermissionActions, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var disclosure by remember { mutableStateOf(false) }
    if (disclosure) AccessibilityDisclosure(onAgree = { disclosure = false; actions.openAccessibility() }, onDismiss = { disclosure = false })
    val steps = 4
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(0.3f))
        when (step) {
            0 -> OnboardingStep("🌿", "Welcome to Focus Life", "Stop endless scrolling. Focus Life blocks reels and short videos the way you choose: completely, after a couple of reels, or after a few minutes. It also tracks your screen time and helps you build better habits.\n\nEverything stays on your phone.")
            1 -> {
                OnboardingStep("📊", "Usage Access", "Lets Focus Life measure how long you use each app, for your daily limit and statistics.")
                PermissionButton(perms.usage, "Grant Usage Access", actions.openUsage)
                RestrictedHint(perms.usage, actions)
            }
            2 -> {
                OnboardingStep("🛡️", "Accessibility", "This is what blocks reels. Focus Life detects reels/shorts screens and browser video pages, then takes you out of them. It never reads your messages or passwords.")
                PermissionButton(perms.accessibility, "Turn on Accessibility") { disclosure = true }
                RestrictedHint(perms.accessibility, actions)
            }
            else -> {
                OnboardingStep("🔔", "Reminders & battery", "Allow notifications for limit reminders, and unrestricted battery so Android doesn't stop the blocker in the background.")
                PermissionButton(perms.notifications, "Allow notifications", actions.requestNotifications)
                PermissionButton(perms.battery, "Allow unrestricted battery", actions.requestBattery)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Spacer(Modifier.width(1.dp))
            Button(onClick = { if (step < steps - 1) step++ else onFinish() }) { Text(if (step < steps - 1) "Next" else "Start") }
        }
    }
}

@Composable private fun OnboardingStep(emoji: String, title: String, body: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(emoji, style = MaterialTheme.typography.displayLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable private fun PermissionButton(granted: Boolean, label: String, onClick: () -> Unit) {
    if (granted) Text("✓ Done", Modifier.fillMaxWidth(), color = Accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    else Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable private fun RestrictedHint(granted: Boolean, actions: PermissionActions) {
    if (granted) return
    AppCard {
        Text("Switch greyed out (\"Restricted setting\")?", fontWeight = FontWeight.SemiBold)
        Text("Try turning it on once, then tap below → ⋮ (top right) → Allow restricted settings → confirm. Come back and turn it on.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = actions.openAppInfo) { Text("Open App info") }
    }
}

/** Shows what the blocker sees, so detection rules can be tuned for a specific phone and app version. */
@Composable fun DetectionCheckScreen() {
    var enabled by remember { mutableStateOf(FocusAccessibilityService.debugEnabled) }
    var lines by remember { mutableStateOf(FocusAccessibilityService.debugSnapshot()) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(enabled) { while (true) { lines = FocusAccessibilityService.debugSnapshot(); delay(1_000) } }
    Page("Detection check", "See what the blocker detects in each app.") {
        AppCard {
            Text("1. Turn on recording below.\n2. Open X (or another app) and scroll a few videos.\n3. Come back here, tap Copy, and send the text for tuning.", color = Muted)
            SwitchRow("Record what the blocker sees", "Only kept in memory; turn off when done", enabled) { on ->
                enabled = on
                FocusAccessibilityService.debugEnabled = on
                if (on) FocusAccessibilityService.clearDebug()
            }
            if (!FocusAccessibilityService.running) Text("The blocker service isn't running. Turn on Accessibility for Focus Life first.", color = Amber)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }, enabled = lines.isNotEmpty()) { Text("Copy") }
                OutlinedButton(onClick = { FocusAccessibilityService.clearDebug(); lines = emptyList() }) { Text("Clear") }
            }
        }
        AppCard {
            if (lines.isEmpty()) Text("Nothing recorded yet.", color = Muted)
            lines.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
