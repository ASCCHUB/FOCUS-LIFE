package com.focuslife.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focuslife.app.service.FocusAccessibilityService
import com.focuslife.app.ui.*
import com.focuslife.app.worker.UsageWorker
import dagger.hilt.android.AndroidEntryPoint

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("Home", "Home", Icons.Filled.Home),
    Tab("Apps", "Apps", Icons.Filled.Block),
    Tab("Focus", "Focus", Icons.Filled.Timer),
    Tab("Statistics", "Stats", Icons.Filled.BarChart),
    Tab("Settings", "Settings", Icons.Filled.Settings),
)

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private var accessibilityOn by mutableStateOf(false)
    private var notificationsOn by mutableStateOf(false)
    private var batteryOn by mutableStateOf(false)
    private var serviceRunning by mutableStateOf(false)
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
        val actions = PermissionActions(::openUsageSettings, ::openAccessibilitySettings, ::openAppInfo, ::requestNotifications, ::requestBattery)
        setContent {
            val vm: FocusViewModel = hiltViewModel()
            SideEffect { refreshCallback = vm::refresh }
            val light by vm.light.collectAsState()
            val usageOn by vm.permission.collectAsState()
            val blocker by vm.blocker.collectAsState()
            val perms = PermissionState(usageOn, accessibilityOn, notificationsOn, batteryOn, serviceRunning || !accessibilityOn)
            LaunchedEffect(light) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
                    navigationBarStyle = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
                )
            }
            FocusLifeTheme(light) {
                val config = blocker
                when {
                    config == null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    !config.onboardingDone -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().systemBarsPadding()) {
                            OnboardingScreen(perms, actions) { vm.updateBlocker { it.copy(onboardingDone = true) } }
                        }
                    }
                    else -> MainScaffold(vm, perms, actions)
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
        serviceRunning = FocusAccessibilityService.running
        notificationsOn = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        batteryOn = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun isAccessibilityServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val mine = ComponentName(this, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    private fun openUsageSettings() = start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    private fun openAccessibilitySettings() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    private fun openAppInfo() = start(appDetails())
    private fun openNotificationSettings() = start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName), appDetails())

    private fun requestBattery() = start(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

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

@Composable private fun MainScaffold(vm: FocusViewModel, perms: PermissionState, actions: PermissionActions) {
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
            composable("Home") { HomeScreen(vm, perms, actions, onGoals = { nav.navigate("Goals") }, onSchedules = { nav.navigate("Schedules") }) }
            composable("Apps") { AppsScreen(vm, perms, actions) }
            composable("Focus") { FocusScreen(vm) }
            composable("Statistics") { StatisticsScreen(vm) }
            composable("Settings") {
                SettingsScreen(vm, perms, actions,
                    onRules = { nav.navigate("Rules") }, onSchedules = { nav.navigate("Schedules") },
                    onGoals = { nav.navigate("Goals") }, onSleep = { nav.navigate("Sleep") })
            }
            composable("Rules") { RulesScreen(vm) }
            composable("Schedules") { SchedulesScreen(vm) }
            composable("Goals") { GoalsScreen(vm) }
            composable("Sleep") { SleepScreen(vm) }
        }
    }
}
