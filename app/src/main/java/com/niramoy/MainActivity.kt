package com.niramoy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
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
            val largeText by vm.largeText.collectAsState()
            NiramoyTheme(lang = settings.language, textScale = if (largeText) 1.18f else 1f) {
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
    Tab("health", "tab_health", Icons.Filled.MonitorHeart, Icons.Outlined.MonitorHeart),
    Tab("records", "tab_records", Icons.Filled.Person, Icons.Outlined.Person)
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

    Box(Modifier.fillMaxSize().background(AppColors.BackgroundBrush)) {
        // Content scrolls under the floating bar; screens leave this much space at their end.
        CompositionLocalProvider(com.niramoy.ui.components.LocalBottomSpace provides if (showTabs) 120.dp else 32.dp) {
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize().imePadding(),
            enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 30 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) }) {
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
        AnimatedVisibility(
            visible = showTabs,
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { FloatingNav(route) { nav.navigateTab(it) } }
    }
}

/** Floating pill bar with round icon buttons (reference style); the selected tab shows its label. */
@Composable
private fun FloatingNav(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .shadow(14.dp, CircleShape, spotColor = AppColors.Brand.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(AppColors.Surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.forEach { tab ->
            val on = current == tab.route
            val label = t(tab.labelKey)
            Row(
                Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(if (on) AppColors.Brand else AppColors.SurfaceAlt)
                    .clickable(role = Role.Tab, onClickLabel = label) { onSelect(tab.route) }
                    .animateContentSize()
                    .padding(horizontal = if (on) 16.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (on) tab.on else tab.off, contentDescription = label, tint = if (on) Color.White else AppColors.TextMuted, modifier = Modifier.size(24.dp))
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}
