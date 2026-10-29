package com.niramoy.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Num

/** Brands that kill background apps unless "Autostart" / "Background activity" is allowed. */
private val AUTOSTART_BRANDS = setOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo", "huawei", "honor", "oneplus", "letv", "asus", "samsung", "tecno", "infinix", "itel")

private val AUTOSTART_INTENTS = listOf(
    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
    ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
)

private fun needsAutostart() = Build.MANUFACTURER.lowercase() in AUTOSTART_BRANDS || Build.BRAND.lowercase() in AUTOSTART_BRANDS

private fun openFirst(context: Context, intents: List<Intent>) {
    for (i in intents) {
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) { }
    }
}

private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

data class PermStep(val key: String, val icon: ImageVector, val done: Boolean?, val action: () -> Unit)

/**
 * One-time, picture-based setup in simple words. Each step is a big card with a picture-like icon,
 * a one-line reason and one button; a green tick appears when it is done. Can be reopened from Settings.
 */
@SuppressLint("BatteryLife")
@Composable
fun PermissionGuideScreen(vm: MainViewModel, onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var autostartTapped by remember { mutableStateOf(false) }

    val steps = remember(tick, autostartTapped) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        buildList {
            add(PermStep("perm_notif", Icons.Filled.NotificationsActive,
                if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                else nm.areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openFirst(context, listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName), appDetails(context)))
            })
            if (Build.VERSION.SDK_INT >= 31) add(PermStep("perm_alarm", Icons.Filled.Alarm, am.canScheduleExactAlarms()) {
                openFirst(context, listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")), appDetails(context)))
            })
            add(PermStep("perm_battery", Icons.Filled.BatteryChargingFull, pm.isIgnoringBatteryOptimizations(context.packageName)) {
                openFirst(context, listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context)))
            })
            if (Build.VERSION.SDK_INT >= 34) add(PermStep("perm_fullscreen", Icons.Filled.Fullscreen, nm.canUseFullScreenIntent()) {
                openFirst(context, listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")), appDetails(context)))
            })
            if (needsAutostart()) add(PermStep("perm_autostart", Icons.Filled.RestartAlt, if (autostartTapped) true else null) {
                autostartTapped = true
                openFirst(context, AUTOSTART_INTENTS.map { Intent().setComponent(it) } + appDetails(context))
            })
        }
    }
    val lang = LocalLang.current

    Page(vm, t("perm_title"), onBack = onBack, speak = t("perm_intro")) {
        Text(t("perm_intro"), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        steps.forEachIndexed { i, step ->
            AppCard(Modifier.padding(vertical = 8.dp), color = if (step.done == true) AppColors.TakenSoft else AppColors.Surface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(AppColors.BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(step.icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${Num.loc(i + 1, lang)}. ${t(step.key)}", style = MaterialTheme.typography.titleMedium)
                        Text(t(step.key + "_why"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (step.done == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Taken, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("perm_done"), color = AppColors.Taken, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    BigButton(t("perm_allow"), Modifier.fillMaxWidth(), icon = Icons.Filled.TouchApp, onClick = step.action)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigOutlineButton(t("perm_test"), Modifier.fillMaxWidth(), icon = Icons.Filled.NotificationImportant) {
            ReminderScheduler.scheduleTest(context, System.currentTimeMillis() + 15_000L)
            android.widget.Toast.makeText(context, com.niramoy.i18n.Strings.t("perm_test_set", lang), android.widget.Toast.LENGTH_LONG).show()
        }
        Spacer(Modifier.height(12.dp))
        BigButton(t("finish"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken, onClick = onDone)
    }
}
