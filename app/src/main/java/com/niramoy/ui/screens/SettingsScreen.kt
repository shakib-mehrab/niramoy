package com.niramoy.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.niramoy.alarm.SoundHelper
import com.niramoy.data.UiPrefs
import com.niramoy.data.local.SettingsEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.MealTiming
import com.niramoy.model.Slot
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Num
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController, onSignedOut: () -> Unit) {
    val s by vm.settings.collectAsState()
    val user by vm.user.collectAsState()
    val firebaseReady by vm.firebaseReady.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var editTime by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    Page(vm, t("settings"), onBack = { nav.popBackStack() }) {
        // ---- account
        SectionTitle(t("account"))
        AppCard {
            if (user != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = AppColors.Taken, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t("signed_in"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                        Text(user!!.email, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(10.dp))
                BigOutlineButton(t("sync_now"), Modifier.fillMaxWidth(), icon = Icons.Filled.Sync) {
                    vm.syncNow(); message = Strings.t("sync_started", lang)
                }
                Spacer(Modifier.height(8.dp))
                BigOutlineButton(t("sign_out"), Modifier.fillMaxWidth(), icon = Icons.Filled.Logout, color = AppColors.Missed) { confirmSignOut = true }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = TouchMin)) {
                    Text(t("delete_account"), color = AppColors.Missed, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = AppColors.Skipped, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t(if (firebaseReady) "not_signed_in" else "cloud_not_setup"), style = MaterialTheme.typography.bodyLarge)
                }
                if (firebaseReady) {
                    Spacer(Modifier.height(10.dp))
                    BigButton(t("auth_signin"), Modifier.fillMaxWidth(), icon = Icons.Filled.Login) {
                        UiPrefs(context).guestMode = false; onSignedOut()
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Brand, modifier = Modifier.padding(top = 8.dp)) }
        }

        // ---- language
        SectionTitle(t("language"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("বাংলা", lang == "bn", Modifier.weight(1f)) { vm.updateSettings { it.copy(language = "bn") } }
            ChoiceChip("English", lang == "en", Modifier.weight(1f)) { vm.updateSettings { it.copy(language = "en") } }
        }

        // ---- routine
        SectionTitle(t("routine"))
        AppCard {
            listOf(
                Triple("wake", Icons.Filled.WbTwilight, s.wakeTime),
                Triple("breakfast", Icons.Filled.FreeBreakfast, s.breakfastTime),
                Triple("lunch", Icons.Filled.LunchDining, s.lunchTime),
                Triple("dinner", Icons.Filled.DinnerDining, s.dinnerTime),
                Triple("sleep", Icons.Filled.Bedtime, s.sleepTime)
            ).forEachIndexed { i, (key, icon, time) ->
                if (i > 0) HorizontalDivider(color = AppColors.Border)
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("rt_$key"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                        Text(Num.time(time, lang), style = MaterialTheme.typography.titleLarge)
                    }
                    BigOutlineButton(t("change"), onClick = { editTime = key })
                }
            }
        }
        // Resulting dose times, so the family can check them at a glance.
        AppCard(Modifier.padding(top = 10.dp), color = AppColors.SurfaceAlt) {
            Text(t("dose_times"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            Slot.entries.forEach { slot ->
                Text("${t("slot_${slot.id}")}: ${t("timing_before")} ${Num.time(s.doseTime(slot, MealTiming.BEFORE), lang)} · ${t("timing_after")} ${Num.time(s.doseTime(slot, MealTiming.AFTER), lang)}",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ---- reminders
        SectionTitle(t("reminders"))
        AppCard {
            Stepper(t("remind_count"), tf("n_times", Num.loc(s.remindCount, lang)),
                onMinus = { vm.updateSettings { it.copy(remindCount = (it.remindCount - 1).coerceAtLeast(1)) } },
                onPlus = { vm.updateSettings { it.copy(remindCount = (it.remindCount + 1).coerceAtMost(8)) } })
            Spacer(Modifier.height(12.dp))
            Text(t("remind_interval"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { m -> ChoiceChip(tf("n_min", Num.loc(m, lang)), s.remindInterval == m) { vm.updateSettings { it.copy(remindInterval = m) } } }
            }
            Spacer(Modifier.height(8.dp))
            Text(t("snooze_for"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(5, 10, 15, 30).forEach { m -> ChoiceChip(tf("n_min", Num.loc(m, lang)), s.snoozeMinutes == m) { vm.updateSettings { it.copy(snoozeMinutes = m) } } }
            }
            Spacer(Modifier.height(8.dp))
            Stepper(t("before_offset"), tf("n_min", Num.loc(s.beforeMealOffset, lang)),
                onMinus = { vm.updateSettings { it.copy(beforeMealOffset = (it.beforeMealOffset - 5).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(beforeMealOffset = (it.beforeMealOffset + 5).coerceAtMost(120)) } })
            Stepper(t("after_offset"), tf("n_min", Num.loc(s.afterMealOffset, lang)),
                onMinus = { vm.updateSettings { it.copy(afterMealOffset = (it.afterMealOffset - 5).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(afterMealOffset = (it.afterMealOffset + 5).coerceAtMost(120)) } })
        }

        // ---- tone
        SectionTitle(t("tone"))
        AppCard {
            SettingsEntity.TONES.forEach { tone ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChip(t("tone_$tone"), s.tone == tone, Modifier.weight(1f)) { vm.updateSettings { it.copy(tone = tone) } }
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(Icons.Filled.PlayArrow, t("listen"), {
                        runCatching { player?.release() }
                        player = SoundHelper.preview(context, SoundHelper.uriFor(context, tone, lang, Slot.MORNING, MealTiming.AFTER))
                    })
                }
            }
            if (!SoundHelper.hasVoiceFiles(context)) Text(t("voice_missing"), style = MaterialTheme.typography.bodySmall, color = AppColors.Skipped)
        }

        SectionTitle(t("upd_section"))
        BigOutlineButton(t("upd_check"), Modifier.fillMaxWidth(), icon = Icons.Filled.SystemUpdate) { vm.checkForUpdates(manual = true) }

        SectionTitle(t("phone_setup"))
        BigButton(t("perm_title"), Modifier.fillMaxWidth(), icon = Icons.Filled.PhonelinkSetup) { nav.navigate("permissions") }
        Spacer(Modifier.height(10.dp))
        BigOutlineButton(t("my_info"), Modifier.fillMaxWidth(), icon = Icons.Filled.Badge) { nav.navigate("profile") }
        Spacer(Modifier.height(10.dp))
        Disclaimer()
        Text("${t("app_name")} v${com.niramoy.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }

    editTime?.let { key ->
        val current = when (key) { "wake" -> s.wakeTime; "breakfast" -> s.breakfastTime; "lunch" -> s.lunchTime; "dinner" -> s.dinnerTime; else -> s.sleepTime }
        var value by remember(key) { mutableIntStateOf(current) }
        FormDialog(t("rt_$key"), onDismiss = { editTime = null }, onSave = {
            vm.updateSettings {
                when (key) {
                    "wake" -> it.copy(wakeTime = value); "breakfast" -> it.copy(breakfastTime = value); "lunch" -> it.copy(lunchTime = value)
                    "dinner" -> it.copy(dinnerTime = value); else -> it.copy(sleepTime = value)
                }
            }
            editTime = null
        }) { TimeChooser(value, { value = it }) }
    }
    if (confirmSignOut) ConfirmDialog(t("sign_out_q"), t("sign_out_msg"), danger = true,
        onYes = { confirmSignOut = false; vm.signOut { onSignedOut() } }, onNo = { confirmSignOut = false })
    if (confirmDelete) ConfirmDialog(t("delete_account_q"), t("delete_account_msg"), danger = true, onYes = {
        confirmDelete = false
        scope.launch { val err = vm.deleteAccount(); if (err == null) onSignedOut() else message = Strings.t(err, lang) }
    }, onNo = { confirmDelete = false })
}
