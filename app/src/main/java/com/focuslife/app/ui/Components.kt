package com.focuslife.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary

data class PermissionState(
    val usage: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val serviceRunning: Boolean,
) {
    val required get() = usage && accessibility
    val allGranted get() = usage && accessibility && notifications && battery
}

class PermissionActions(
    val openUsage: () -> Unit,
    val openAccessibility: () -> Unit,
    val openAppInfo: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestBattery: () -> Unit,
    val paymentMode: () -> Unit,
)

@Composable fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Muted)
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun AppCard(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable fun Tile(title: String, value: String, subtitle: String = "") {
    AppCard {
        Text(title, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Accent)
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted)
    }
}

@Composable fun Section(title: String) { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

fun mins(ms: Long): String { val m = ms / 60_000L; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
fun clock(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }

@Composable fun rememberAppLabel(pkg: String): String {
    val pm = LocalContext.current.packageManager
    return remember(pkg) { try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }
}

@Composable fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable fun Stepper(title: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value$suffix", Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable fun <T> RadioGroup(options: List<T>, selected: T, title: (T) -> String, subtitle: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = option == selected, onClick = { onSelect(option) }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Column(Modifier.weight(1f)) {
                    Text(title(option), fontWeight = FontWeight.SemiBold)
                    Text(subtitle(option), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable fun AccessibilityDisclosure(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How Focus Life uses Accessibility") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("To block reels and short videos, Focus Life needs the Accessibility service. It reads only:")
                Text("• which app is open\n• the IDs of on-screen elements that identify a reels/shorts screen\n• scroll events (to count videos)\n• the address bar of supported browsers")
                Text("It never reads your messages, passwords or other screen text. It stores only counts (videos watched, reels blocked, time spent), and nothing leaves your phone.", color = Muted)
            }
        },
        confirmButton = { Button(onClick = onAgree) { Text("I agree") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable fun PermissionsCard(perms: PermissionState, actions: PermissionActions, showAll: Boolean = true) {
    var disclosure by remember { mutableStateOf(false) }
    if (disclosure) AccessibilityDisclosure(onAgree = { disclosure = false; actions.openAccessibility() }, onDismiss = { disclosure = false })
    AppCard {
        Section(if (perms.allGranted) "Permissions" else "Finish setup")
        PermissionRow("Usage Access", "Required. Measures your screen time.", perms.usage, "Grant", actions.openUsage)
        PermissionRow("Accessibility", "Required. Detects and blocks reels and short videos.", perms.accessibility, "Turn on") { disclosure = true }
        if (showAll || !perms.notifications) PermissionRow("Notifications", "Reminders as you approach your limit.", perms.notifications, "Allow", actions.requestNotifications)
        if (showAll || !perms.battery) PermissionRow("Battery: unrestricted", "Keeps blocking working in the background.", perms.battery, "Allow", actions.requestBattery)
        if (perms.accessibility && !perms.serviceRunning) {
            Text("The blocker service is on but not running. Turn Focus Life off and on again in Accessibility settings.", color = Amber)
            OutlinedButton(onClick = actions.openAccessibility) { Text("Open Accessibility settings") }
        }
        if (!perms.usage || !perms.accessibility) {
            HorizontalDivider()
            Text("Switch greyed out, or it says \"Restricted setting\"?", fontWeight = FontWeight.SemiBold)
            Text("Android blocks this for apps installed from an APK file. First try to turn the permission on once. Then tap Open App info, tap ⋮ at the top right, choose \"Allow restricted settings\" and confirm. Come back and turn it on.", color = Muted)
            OutlinedButton(onClick = actions.openAppInfo) { Text("Open App info") }
        }
    }
}

@Composable fun PermissionRow(title: String, description: String, granted: Boolean, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = if (granted) Accent else Amber)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) Text("On", color = Accent, fontWeight = FontWeight.SemiBold) else Button(onClick = onClick) { Text(button) }
    }
}

@Composable fun ClickableRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Muted)
    }
}
