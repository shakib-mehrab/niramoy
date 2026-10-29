package com.niramoy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.niramoy.data.UiPrefs
import com.niramoy.model.RecordKind
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.LocalLang
import com.niramoy.ui.components.t
import com.niramoy.ui.screens.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.ui.theme.NiramoyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            NiramoyTheme(lang = settings.language) {
                CompositionLocalProvider(LocalLang provides settings.language) {
                    NiramoyApp(vm)
                }
            }
        }
    }
}

private data class Tab(val route: String, val labelKey: String, val on: ImageVector, val off: ImageVector)

private val TABS = listOf(
    Tab("home", "tab_home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("medicines", "tab_medicines", Icons.Filled.Medication, Icons.Outlined.Medication),
    Tab("health", "tab_health", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Tab("records", "tab_records", Icons.Filled.Folder, Icons.Outlined.Folder)
)

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun NiramoyApp(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UiPrefs(context) }
    val settings by vm.settings.collectAsState()
    val user by vm.user.collectAsState()
    val firebaseReady by vm.firebaseReady.collectAsState()
    val restoring by vm.restoring.collectAsState()
    var guest by remember { mutableStateOf(prefs.guestMode) }
    var guideSeen by remember { mutableStateOf(prefs.permissionGuideSeen) }
    val update by vm.updateState.collectAsState()

    // New version on GitHub? Checked on launch (at most every 6 h); the dialog shows only when there is one.
    LaunchedEffect(Unit) { vm.checkForUpdates(manual = false) }
    if (update != com.niramoy.ui.UpdateUiState.Idle) {
        com.niramoy.ui.components.UpdateDialog(update, onDownload = { vm.downloadUpdate() }, onInstall = { vm.installUpdate() }, onLater = { vm.dismissUpdate() })
    }

    when {
        firebaseReady && user == null && !guest -> AuthScreen(vm, onSkip = { prefs.guestMode = true; guest = true })
        restoring -> RestoringScreen()
        !settings.onboarded -> OnboardingScreen(vm)
        !guideSeen -> PermissionGuideScreen(vm, onDone = { prefs.permissionGuideSeen = true; guideSeen = true })
        else -> MainScaffold(vm, onSignedOut = { prefs.guestMode = false; guest = false })
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, onSignedOut: () -> Unit) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "home"
    val showTabs = TABS.any { it.route == route }

    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = { if (showTabs) BottomTabs(route) { nav.navigateTab(it) } }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("home") { HomeScreen(vm, nav) }
            composable("medicines") { MedicinesScreen(vm, nav) }
            composable("health") { HealthScreen(vm, nav) }
            composable("records") { RecordsScreen(vm, nav) }

            composable("med_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) {
                MedicineEditScreen(vm, nav, it.arguments?.getString("id"))
            }
            composable("med_bulk?review={review}", arguments = listOf(navArgument("review") { type = NavType.BoolType; defaultValue = false })) {
                BulkAddScreen(vm, nav, fromOcr = it.arguments?.getBoolean("review") == true)
            }
            composable("med_ocr") { OcrScreen(vm, nav) }
            composable("med/{id}") { MedicineDetailScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }

            composable("health_entry?id={id}&voice={voice}", arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true },
                navArgument("voice") { type = NavType.BoolType; defaultValue = false }
            )) { HealthEntryScreen(vm, nav, it.arguments?.getString("id"), it.arguments?.getBoolean("voice") == true) }

            composable("visits") { VisitsScreen(vm, nav) }
            composable("files/{kind}") { FilesScreen(vm, nav, RecordKind.of(it.arguments?.getString("kind"))) }
            composable("doctors") { DoctorsScreen(vm, nav) }
            composable("profile") { ProfileScreen(vm, nav) }
            composable("emergency") { EmergencyScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav, onSignedOut) }
            composable("permissions") { PermissionGuideScreen(vm, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }) }
        }
    }
}

/** Four big tabs: large icon + word, the selected one filled purple. */
@Composable
private fun BottomTabs(current: String, onSelect: (String) -> Unit) {
    Surface(color = AppColors.Surface, shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp)) {
            TABS.forEach { tab ->
                val on = current == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) AppColors.BrandSoft else AppColors.Surface)
                        .clickable(role = Role.Tab) { onSelect(tab.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(if (on) tab.on else tab.off, contentDescription = null, tint = if (on) AppColors.Brand else AppColors.TextMuted, modifier = Modifier.size(32.dp))
                    Text(t(tab.labelKey), style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) AppColors.Brand else AppColors.TextMuted, maxLines = 1)
                }
            }
        }
    }
}
