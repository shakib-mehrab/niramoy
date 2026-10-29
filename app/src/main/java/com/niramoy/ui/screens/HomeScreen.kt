package com.niramoy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.niramoy.data.local.SettingsEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseItem
import com.niramoy.model.DoseStatus
import com.niramoy.model.Slot
import com.niramoy.ui.GroupView
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Dates
import com.niramoy.util.Num

fun slotIcon(slot: Slot) = when (slot) {
    Slot.MORNING -> Icons.Filled.WbTwilight
    Slot.NOON -> Icons.Filled.WbSunny
    Slot.NIGHT -> Icons.Filled.Bedtime
}

/** Home shows only today: next dose, today's list with big "খেয়েছি" buttons, and the Emergency button. */
@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val s by vm.settings.collectAsState()
    val today by vm.today.collectAsState()
    val groups by vm.todayGroups.collectAsState()
    val next by vm.nextGroup.collectAsState()
    val adherence by vm.adherence7.collectAsState()
    val now by vm.now.collectAsState()
    val lang = s.language
    var skipItem by remember { mutableStateOf<DoseItem?>(null) }

    val title = if (s.name.isNotBlank()) Strings.f("hello_name", lang, s.name) else Strings.t("hello", lang)
    val speak = buildString {
        append(title).append(". ")
        next?.let { g -> append(Strings.t("next_dose", lang)).append(": ").append(Num.time(Dates.minuteOfDay(g.time), lang)).append(". ")
            append(Strings.t("phrase_${g.group.slot.id}_${g.group.timing.id}", lang)).append(". ")
            append(g.items.joinToString(", ") { it.medicine.name }) }
    }

    Page(vm, title, speak = speak, trailing = {
        IconButton(onClick = { nav.navigate("settings") }, modifier = Modifier.size(TouchMin)) {
            Icon(Icons.Filled.Settings, contentDescription = t("settings"), tint = AppColors.Brand, modifier = Modifier.size(32.dp))
        }
    }) {
        Text("${Num.dayName(Dates.dayOfWeek(today), lang)}, ${Num.date(today, lang)}", style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
        Spacer(Modifier.height(10.dp))

        NextDoseCard(next, today, s, now, hasDosesToday = groups.isNotEmpty(), onTaken = { vm.markGroupTaken(it) })
        Spacer(Modifier.height(14.dp))

        BigButton(t("emergency"), Modifier.fillMaxWidth(), icon = Icons.Filled.Sos, color = AppColors.Emergency, height = 72.dp) { nav.navigate("emergency") }

        SectionTitle(t("today_list"))
        if (groups.isEmpty()) {
            EmptyState(Icons.Filled.Medication, t("no_meds_today"))
            BigButton(t("add_medicine"), Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { nav.navigate("med_edit") }
        }
        groups.forEach { g -> GroupCard(g, lang, onTaken = { vm.markDose(it, DoseStatus.TAKEN) }, onSkip = { skipItem = it }, onUndo = { vm.undoDose(it) }) }

        adherence?.let { pct ->
            Spacer(Modifier.height(14.dp))
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("adherence_7"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                        Text("${Num.loc(pct, lang)}%", style = MaterialTheme.typography.headlineLarge,
                            color = if (pct >= 80) AppColors.Taken else if (pct >= 50) AppColors.Skipped else AppColors.Missed)
                    }
                }
            }
        }
        Disclaimer()
    }

    skipItem?.let { item ->
        ConfirmDialog(Strings.f("skip_q", lang, item.medicine.name), onYes = { vm.markDose(item, DoseStatus.SKIPPED); skipItem = null }, onNo = { skipItem = null })
    }
}

/** Purple hero card (reference style): pill icon, time, "সকালের খাবারের পরে", medicines, big ✓ button. */
@Composable
private fun NextDoseCard(next: GroupView?, today: String, s: SettingsEntity, now: Long, hasDosesToday: Boolean, onTaken: (String) -> Unit) {
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AppColors.HeroBrush).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t("next_dose"), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f))
        if (next == null) {
            Spacer(Modifier.height(10.dp))
            Icon(if (hasDosesToday) Icons.Filled.CheckCircle else Icons.Filled.Medication, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Text(t(if (hasDosesToday) "all_done" else "no_meds_today"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            return@Column
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(slotIcon(next.group.slot), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    (if (next.group.date != today) t("tomorrow") + " " else "") + Num.time(Dates.minuteOfDay(next.time), lang),
                    style = MaterialTheme.typography.displaySmall, color = Color.White
                )
                Text(t("phrase_${next.group.slot.id}_${next.group.timing.id}"), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
            next.items.filter { it.status == null || next.group.date != today }.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    PillIcon()
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(item.medicine.name, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                        if (item.medicine.dose.isNotBlank()) Text(item.medicine.dose, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                    }
                }
            }
        }
        // "Taken" for the whole group, from shortly before its time (taking a bit early is normal).
        if (next.group.date == today && now >= next.time - 60 * 60_000L) {
            Spacer(Modifier.height(14.dp))
            BigButton(t("taken_all"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = Color.White, contentColor = AppColors.Taken, height = 72.dp) {
                onTaken(next.group.key)
            }
        }
    }
}

@Composable
fun PillIcon(size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(AppColors.BrandSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Medication, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size((size * 0.62f).dp))
    }
}

@Composable
private fun GroupCard(g: GroupView, lang: String, onTaken: (DoseItem) -> Unit, onSkip: (DoseItem) -> Unit, onUndo: (DoseItem) -> Unit) {
    AppCard(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(slotIcon(g.group.slot), contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(Num.time(Dates.minuteOfDay(g.time), lang), style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                Text(t("when_${g.group.slot.id}_${g.group.timing.id}"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            }
        }
        g.items.forEach { item ->
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = AppColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillIcon()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.medicine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (item.medicine.dose.isNotBlank()) Text(item.medicine.dose, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                }
                StatusBadge(item.status)
            }
            Spacer(Modifier.height(10.dp))
            if (item.status == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(t("taken"), Modifier.weight(2f), icon = Icons.Filled.Check, color = AppColors.Taken) { onTaken(item) }
                    BigOutlineButton(t("skip_dose"), Modifier.weight(1f), color = AppColors.TextMuted) { onSkip(item) }
                }
            } else {
                TextButton(onClick = { onUndo(item) }, modifier = Modifier.heightIn(min = TouchMin)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("undo_mark"), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
