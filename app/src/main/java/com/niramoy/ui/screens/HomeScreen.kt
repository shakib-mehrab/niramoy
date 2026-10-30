package com.niramoy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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

/** Initial-letter avatar (reference "Hi Lina" header). */
@Composable
fun Avatar(name: String, size: Int = 46) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(AppColors.HeroBrush), contentAlignment = Alignment.Center) {
        val ch = name.trim().firstOrNull()?.uppercase()
        if (ch != null) Text(ch, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        else Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val s by vm.settings.collectAsState()
    val today by vm.today.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val groups by vm.dayGroups.collectAsState()
    val next by vm.nextGroup.collectAsState()
    val adherence by vm.adherence7.collectAsState()
    val now by vm.now.collectAsState()
    val meds by vm.medicines.collectAsState()
    val lang = s.language
    var editing by remember { mutableStateOf<DoseItem?>(null) }

    // Keep the strip on today after midnight unless the user picked another day.
    LaunchedEffect(today) { vm.selectedDate.value = today }

    val greeting = if (s.name.isNotBlank()) Strings.f("hello_name", lang, s.name.trim().split(" ").first()) else Strings.t("hello", lang)

    Column(Modifier.fillMaxSize()) {
        // Header: avatar + greeting, read-aloud, settings, language.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Num.dayName(Dates.dayOfWeek(today), lang) + ", " + Num.date(today, lang, withYear = false),
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            CircleIconButton(Icons.Filled.Settings, t("settings"), { nav.navigate("settings") })
            Spacer(Modifier.width(8.dp))
            LanguageToggle(lang) { vm.toggleLanguage() }
        }

        ScreenColumn {
            next?.takeIf { selected == today }?.let { g ->
                NextDoseCard(g, today, now) { vm.markGroupTaken(g.group.key) }
                Spacer(Modifier.height(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(t("add_short"), Icons.Filled.AddCircle, Modifier.weight(1f)) { nav.navigate("med_edit") }
                IconTile(t("tab_health"), Icons.Filled.MonitorHeart, Modifier.weight(1f), tint = AppColors.Heart) { nav.navigate("health") }
                IconTile(t("records_short"), Icons.Filled.Description, Modifier.weight(1f)) { nav.navigate("records") }
                IconTile(t("sos"), Icons.Filled.Sos, Modifier.weight(1f), tint = AppColors.Emergency) { nav.navigate("emergency") }
            }

            SectionTitle(t("your_schedule"))
            DateStrip(selected, today, onSelect = { vm.selectedDate.value = it })
            Spacer(Modifier.height(12.dp))

            if (groups.isEmpty()) {
                AppCard {
                    EmptyState(Icons.Filled.Medication, t(if (meds.isEmpty()) "no_meds" else "no_doses_day"))
                    if (meds.isEmpty()) BigButton(t("add_medicine"), Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { nav.navigate("med_edit") }
                }
            }
            groups.forEach { g ->
                GroupCard(g, lang, editable = selected <= today, isToday = selected == today,
                    onTaken = { vm.markDose(it, DoseStatus.TAKEN) }, onEdit = { editing = it })
                Spacer(Modifier.height(12.dp))
            }

            SectionTitle(t("medication_tracked"), action = { SeeAll(t("view_all")) { nav.navigate("medicines") } })
            AdherenceCard(adherence, lang)
            Disclaimer()
        }
    }

    editing?.let { item -> DoseStatusDialog(item.medicine.name, item.status, onPick = { vm.setDoseStatus(item, it); editing = null }, onDismiss = { editing = null }) }
}

/** Change a dose to Taken / Skipped / Missed / Not yet. */
@Composable
fun DoseStatusDialog(title: String, current: DoseStatus?, allowNotYet: Boolean = true, onPick: (DoseStatus?) -> Unit, onDismiss: () -> Unit) {
    val opts = buildList<Triple<DoseStatus?, androidx.compose.ui.graphics.vector.ImageVector, String>> {
        add(Triple(DoseStatus.TAKEN, Icons.Filled.CheckCircle, t("st_taken")))
        add(Triple(DoseStatus.SKIPPED, Icons.Filled.RemoveCircle, t("st_skipped")))
        add(Triple(DoseStatus.MISSED, Icons.Filled.Cancel, t("st_missed")))
        if (allowNotYet) add(Triple(null, Icons.Filled.Schedule, t("st_not_yet")))
    }
    OptionDialog(tf("change_status", title), opts, current, onPick = onPick, onDismiss = onDismiss, tints = { statusStyle(it).b })
}

/** Hero card for the next dose (accent gradient, white text) with one "Take all" action. */
@Composable
private fun NextDoseCard(g: GroupView, today: String, now: Long, onTakeAll: () -> Unit) {
    val lang = LocalLang.current
    Column(Modifier.fillMaxWidth().clip(CardShape).background(AppColors.HeroBrush).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(slotIcon(g.group.slot), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("next_dose"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text((if (g.group.date != today) t("tomorrow") + " · " else "") + Num.time(Dates.minuteOfDay(g.time), lang),
                    style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1)
            }
            SpeakButton(t("phrase_${g.group.slot.id}_${g.group.timing.id}") + ". " + g.items.joinToString(", ") { it.medicine.name }, tint = Color.White)
        }
        Text(t("when_${g.group.slot.id}_${g.group.timing.id}"), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        val shown = g.items.filter { it.status == null || g.group.date != today }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            shown.take(3).forEach { item ->
                Row(Modifier.weight(1f, fill = false).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Medication, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.medicine.name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (shown.size > 3) Text("+${Num.loc(shown.size - 3, lang)}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
        // "Take all" from a little before the time (taking slightly early is normal).
        if (g.group.date == today && now >= g.time - 60 * 60_000L) {
            Spacer(Modifier.height(14.dp))
            BigButton(t("taken_all"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = Color.White, contentColor = AppColors.Brand, onClick = onTakeAll)
        }
    }
}

@Composable
fun PillIcon(size: Int = 44) = IconSquare(Icons.Filled.Medication, size = size.dp)

@Composable
private fun GroupCard(g: GroupView, lang: String, editable: Boolean, isToday: Boolean, onTaken: (DoseItem) -> Unit, onEdit: (DoseItem) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquare(slotIcon(g.group.slot), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Num.time(Dates.minuteOfDay(g.time), lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(t("when_${g.group.slot.id}_${g.group.timing.id}"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            val done = g.items.count { it.status == DoseStatus.TAKEN }
            Text("${Num.loc(done, lang)}/${Num.loc(g.items.size, lang)}", style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted)
        }
        g.items.forEach { item ->
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = AppColors.Border.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillIcon(40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.medicine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.medicine.dose.isNotBlank()) Text(item.medicine.dose, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                }
                // Tap the badge to change the status at any time (fixes a wrong tap).
                StatusBadge(item.status, onClick = if (editable) ({ onEdit(item) }) else null)
            }
            if (item.status == null && isToday) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(t("taken"), Modifier.weight(1f), icon = Icons.Filled.Check, color = AppColors.Taken, height = 48.dp) { onTaken(item) }
                    BigOutlineButton(t("more_options"), Modifier.weight(1f), color = AppColors.TextMuted) { onEdit(item) }
                }
            }
        }
    }
}

/** Reference "Medication Tracked" gauge: 7-day share of doses taken. */
@Composable
private fun AdherenceCard(pct: Int?, lang: String) {
    AppCard {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Gauge((pct ?: 0) / 100f, Modifier.fillMaxWidth(), showNeedle = pct != null)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (pct == null) t("no_history") else "${Num.loc(pct, lang)}%",
            style = MaterialTheme.typography.headlineMedium, color = AppColors.Text, modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(t("adherence_7"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
