package com.focuslife.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslife.app.domain.BlockMode
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.UsageMath
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable fun HomeScreen(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions, onGoals: () -> Unit, onSchedules: () -> Unit) {
    val cfg = vm.blocker.collectAsState().value ?: BlockerConfig()
    val stats by vm.shortVideoToday.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val schedules by vm.schedules.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val paused = cfg.isPaused(now)
    val startOfDay = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    var pauseDialog by remember { mutableStateOf(false) }
    var paymentDialog by remember { mutableStateOf(false) }

    Page("Focus Life", "Reclaim your focus today.") {
        PageTip(vm, "home")
        if (!perms.allGranted || !perms.serviceRunning) PermissionsCard(perms, actions, showAll = false)

        StatusRing(cfg.mode, paused)
        Text(
            if (paused) "Paused until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(cfg.pauseUntil))}" else cfg.mode.title,
            Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        )
        Text(if (paused) "Blocking is off for now" else modeSummary(cfg), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted)

        StatsStrip(
            sessions = sessions.count { it.state == "COMPLETED" && it.startedAt >= startOfDay },
            blocked = stats.sumOf { it.blocked },
            feedMs = stats.sumOf { it.feedMs },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SELECT PLAN", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Muted, style = MaterialTheme.typography.labelLarge)
            if (paused) Button(onClick = vm::resume) { Text("Resume blocking") }
            else FilledTonalButton(onClick = { pauseDialog = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Amber, contentColor = MaterialTheme.colorScheme.background)) { Text("Pause") }
        }
        BlockMode.entries.forEach { mode -> ModeCard(mode, cfg, selected = cfg.mode == mode) { vm.updateBlocker { it.copy(mode = mode) } } }
        if (schedules.any { it.enabled }) Text("Scheduled focus times switch to Block All automatically.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onSchedules, modifier = Modifier.fillMaxWidth()) { Text("Focus schedules") }
        if (perms.accessibility) OutlinedButton(onClick = { paymentDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("💳  Payment mode") }

        DailyAllowance(vm, perms)
        GoalsSummary(vm, onGoals)
    }
    if (paymentDialog) AlertDialog(
        onDismissRequest = { paymentDialog = false },
        title = { Text("Payment mode") },
        text = { Text("Some banking and payment apps refuse to open while an accessibility service is on. This switches the blocker off so you can pay. Turn it back on afterwards in Settings → Accessibility → Focus Life. We'll remind you in 10 minutes.") },
        confirmButton = { Button(onClick = { paymentDialog = false; actions.paymentMode() }) { Text("Turn off for payment") } },
        dismissButton = { TextButton(onClick = { paymentDialog = false }) { Text("Cancel") } },
    )
    if (pauseDialog) PauseDialog(onDismiss = { pauseDialog = false }, onPause = { minutes -> vm.pauseFor(minutes); pauseDialog = false })
}

private fun modeSummary(cfg: BlockerConfig) = when (cfg.mode) {
    BlockMode.BLOCK_ALL -> "No reels, shorts or TikTok at all"
    BlockMode.CURIOUS -> "Watch ${cfg.curiousCount} reels, then a ${cfg.curiousCooldownMin}-min break"
    BlockMode.TIMED -> "Watch for ${cfg.timedWatchMin} min, then a ${cfg.timedBlockMin}-min break"
}

@Composable private fun StatusRing(mode: BlockMode, paused: Boolean) {
    val accent = if (paused) Amber else Accent
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            drawCircle(color = track, style = Stroke(width = 14.dp.toPx()))
            drawArc(
                color = accent, startAngle = -90f,
                sweepAngle = when { paused -> 90f; mode == BlockMode.BLOCK_ALL -> 360f; mode == BlockMode.CURIOUS -> 270f; else -> 200f },
                useCenter = false, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(Modifier.size(120.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
            Text(
                when { paused -> "⏸"; mode == BlockMode.BLOCK_ALL -> "⛔"; mode == BlockMode.CURIOUS -> "🌿"; else -> "⏳" },
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

@Composable private fun StatsStrip(sessions: Int, blocked: Int, feedMs: Long) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat("$sessions", "Sessions", Modifier.weight(1f))
            VerticalDivider(Modifier.height(36.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
            Stat("$blocked", "Blocked", Modifier.weight(1f))
            VerticalDivider(Modifier.height(36.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
            Stat(clock(feedMs), "Scroll time", Modifier.weight(1f))
        }
    }
}

@Composable private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ModeCard(mode: BlockMode, cfg: BlockerConfig, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(when (mode) { BlockMode.BLOCK_ALL -> "⛔"; BlockMode.CURIOUS -> "🌿"; BlockMode.TIMED -> "⏳" }, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(mode.title, fontWeight = FontWeight.Bold)
                Text(modeSummary(cfg.copy(mode = mode)), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable private fun PauseDialog(onDismiss: () -> Unit, onPause: (Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(15) }
    var countdown by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { while (countdown > 0) { delay(1000); countdown-- } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause blocking?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Take a breath. Do you really need reels right now?", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onPause(minutes) }, enabled = countdown == 0) { Text(if (countdown > 0) "Pause ($countdown)" else "Pause $minutes min") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep blocking") } },
    )
}

@Composable private fun DailyAllowance(vm: FocusViewModel, perms: PermissionState) {
    if (!perms.usage) return
    val summary by vm.summary.collectAsState(); val settings by vm.settings.collectAsState(); val limits by vm.limits.collectAsState()
    val limit = settings?.dailyLimitMinutes ?: 60
    val used = summary?.distractingMs ?: 0L
    Section("Daily allowance")
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Entertainment time left", color = Muted)
                Text(mins(UsageMath.allowance(limit, used)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Accent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Screen time", color = Muted)
                Text(mins(summary?.screenMs ?: 0), fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(progress = { (used.toFloat() / (limit * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(if (limits.none { it.enabled }) "Choose limited apps in the Apps tab." else "Daily allowance: $limit min", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun GoalsSummary(vm: FocusViewModel, onGoals: () -> Unit) {
    val goals by vm.goals.collectAsState(); val completions by vm.completions.collectAsState()
    val done = goals.count { goal -> (completions.firstOrNull { it.goalId == goal.id }?.completedMinutes ?: 0) >= goal.targetMinutes }
    AppCard(Modifier.clickable(onClick = onGoals)) {
        ClickableRow("Daily goals", if (goals.isEmpty()) "Tap to add a goal" else "$done of ${goals.size} done today", onGoals)
    }
}
