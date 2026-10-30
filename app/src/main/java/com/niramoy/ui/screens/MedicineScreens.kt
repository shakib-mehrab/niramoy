package com.niramoy.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.niramoy.data.local.DoseLogEntity
import com.niramoy.data.local.MedicineEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.*
import com.niramoy.parse.MedicineDraft
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.OcrState
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Dates
import com.niramoy.util.Num
import java.io.File

// ----------------------------------------------------------------- list

@Composable
fun MedicinesScreen(vm: MainViewModel, nav: NavController) {
    val meds by vm.medicines.collectAsState()
    val today by vm.today.collectAsState()
    val adherence by vm.adherence30.collectAsState()
    @Suppress("UNUSED_VARIABLE") val logs by vm.doseLogs.collectAsState() // recompose dots when logs change
    val lang = LocalLang.current

    Page(vm, t("tab_medicines")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("add_one"), Icons.Filled.Add, Modifier.weight(1f)) { nav.navigate("med_edit") }
            IconTile(t("add_many"), Icons.Filled.PlaylistAdd, Modifier.weight(1f)) { vm.drafts.value = listOf(MedicineDraft(), MedicineDraft()); nav.navigate("med_bulk") }
            IconTile(t("add_photo"), Icons.Filled.PhotoCamera, Modifier.weight(1f)) { nav.navigate("med_ocr") }
        }
        adherence?.let {
            Spacer(Modifier.height(12.dp))
            AppCard {
                Text(t("adherence_30"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                Text("${Num.loc(it, lang)}%", style = MaterialTheme.typography.headlineLarge, color = AppColors.Brand)
                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)))
            }
        }
        SectionTitle(t("my_medicines"))
        if (meds.isEmpty()) EmptyState(Icons.Filled.Medication, t("no_meds"))
        meds.forEach { m -> MedicineCard(m, today, vm.weekStatuses(m.id)) { nav.navigate("med/${m.id}") } }
        Disclaimer()
    }
}

@Composable
fun MedStateBadge(state: MedState) {
    val (c, icon, key) = when (state) {
        MedState.ACTIVE -> Triple(AppColors.Taken, Icons.Filled.PlayCircle, "state_active")
        MedState.UPCOMING -> Triple(AppColors.Upcoming, Icons.Filled.Schedule, "state_upcoming")
        MedState.STOPPED -> Triple(AppColors.Missed, Icons.Filled.PauseCircle, "state_stopped")
        MedState.COMPLETED -> Triple(AppColors.Brand, Icons.Filled.TaskAlt, "state_completed")
    }
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(4.dp))
        Text(t(key), color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** "সকাল, রাত · খাবারের পরে" */
@Composable
fun scheduleText(m: MedicineEntity): String {
    val lang = LocalLang.current
    return m.slotSet.joinToString(", ") { Strings.t("slot_${it.id}", lang) } + " · " + Strings.t("timing_${m.timing.id}", lang)
}

@Composable
private fun MedicineCard(m: MedicineEntity, today: String, week: List<DoseStatus?>, onClick: () -> Unit) {
    AppCard(Modifier.padding(vertical = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.headlineSmall)
                Text(listOf(m.dose, scheduleText(m)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            }
            PillIcon(52)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedStateBadge(m.state(today))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(t("last_week"), style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                WeekDots(week, dot = 18.dp)
            }
        }
    }
}

// ----------------------------------------------------------------- form

private fun dosePresets(lang: String) = if (lang == "bn") listOf("১টা", "আধা", "২টা", "৫ মিলি", "১ চামচ") else listOf("1 tablet", "½ tablet", "2 tablets", "5 ml", "1 spoon")

/** Shared editor for one medicine (manual add, bulk rows, OCR review). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineForm(d: MedicineDraft, onChange: (MedicineDraft) -> Unit) {
    val lang = LocalLang.current
    BigField(t("med_name"), d.name, { onChange(d.copy(name = it)) }, placeholder = t("med_name_hint"))
    Spacer(Modifier.height(12.dp))
    Text(t("dose"), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        dosePresets(lang).forEach { p -> ChoiceChip(p, d.dose == p) { onChange(d.copy(dose = p)) } }
    }
    BigField(t("dose_other"), d.dose, { onChange(d.copy(dose = it)) })

    Spacer(Modifier.height(14.dp))
    Text(t("when_take"), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Slot.entries.forEach { slot ->
            ChoiceChip(t("slot_${slot.id}"), slot in d.slots, icon = slotIcon(slot)) {
                onChange(d.copy(slots = if (slot in d.slots) d.slots - slot else d.slots + slot))
            }
        }
    }
    if (d.slots.isEmpty()) Text(t("pick_one_slot"), color = AppColors.Missed, style = MaterialTheme.typography.bodyMedium)

    Spacer(Modifier.height(10.dp))
    Text(t("meal"), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        MealTiming.entries.forEach { mt -> ChoiceChip(t("timing_${mt.id}"), d.timing == mt, Modifier.weight(1f)) { onChange(d.copy(timing = mt)) } }
    }

    Spacer(Modifier.height(10.dp))
    Text(t("how_long"), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        ChoiceChip(t("continuous"), d.durationType == DurationType.CONTINUOUS, Modifier.weight(1f)) { onChange(d.copy(durationType = DurationType.CONTINUOUS)) }
        ChoiceChip(t("course"), d.durationType == DurationType.COURSE, Modifier.weight(1f)) { onChange(d.copy(durationType = DurationType.COURSE)) }
    }
    if (d.durationType == DurationType.COURSE) {
        Stepper(t("days"), tf("n_days", Num.loc(d.courseDays, lang)),
            onMinus = { onChange(d.copy(courseDays = (d.courseDays - 1).coerceAtLeast(1))) },
            onPlus = { onChange(d.copy(courseDays = (d.courseDays + 1).coerceAtMost(365))) })
    }
}

// ----------------------------------------------------------------- add / edit one

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditScreen(vm: MainViewModel, nav: NavController, id: String?) {
    val meds by vm.medicines.collectAsState()
    val today by vm.today.collectAsState()
    val existing = id?.let { mid -> meds.firstOrNull { it.id == mid } }
    var d by remember(existing?.id) { mutableStateOf(existing?.let { vm.draftOf(it) } ?: MedicineDraft()) }
    var start by remember(existing?.id) { mutableStateOf(existing?.startDate ?: today) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    val lang = LocalLang.current

    Page(vm, t(if (existing == null) "add_medicine" else "edit_medicine"), onBack = { nav.popBackStack() }) {
        AppCard { MedicineForm(d) { d = it } }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(t("start_date"), style = MaterialTheme.typography.titleSmall)
            Text(Num.date(start, lang), style = MaterialTheme.typography.headlineSmall, color = AppColors.Brand)
            if (d.durationType == DurationType.COURSE) {
                Text(tf("ends_on", Num.date(Dates.addDays(start, d.courseDays - 1), lang)), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            BigOutlineButton(t("change_date"), Modifier.fillMaxWidth(), icon = Icons.Filled.CalendarMonth) { pickDate = true }
            Spacer(Modifier.height(12.dp))
            BigField(t("notes"), notes, { notes = it }, singleLine = false, placeholder = t("notes_hint"))
        }
        Spacer(Modifier.height(16.dp))
        BigButton(t("save"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken, enabled = d.name.isNotBlank() && d.slots.isNotEmpty()) {
            vm.saveDraft(d.copy(notes = notes), existing?.id, start)
            nav.popBackStack()
        }
    }
    if (pickDate) DatePick(start, onPick = { start = it; pickDate = false }, onDismiss = { pickDate = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(date: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val utcMillis = remember(date) {
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(Dates.year(date), Dates.month(date), Dates.dayOfMonth(date))
        }.timeInMillis
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                    onPick("%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH)))
                } ?: onDismiss()
            }, modifier = Modifier.heightIn(min = TouchMin)) { Text(t("ok"), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchMin)) { Text(t("cancel"), style = MaterialTheme.typography.labelLarge) } }
    ) { DatePicker(state) }
}

// ----------------------------------------------------------------- bulk add + OCR review

@Composable
fun BulkAddScreen(vm: MainViewModel, nav: NavController, fromOcr: Boolean) {
    val drafts by vm.drafts.collectAsState()
    val ocr by vm.ocrState.collectAsState()
    val today by vm.today.collectAsState()
    var keepPhoto by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    val lang = LocalLang.current
    val valid = drafts.filter { it.name.isNotBlank() && it.slots.isNotEmpty() }

    Page(vm, t(if (fromOcr) "ocr_review_title" else "add_many"), onBack = { nav.popBackStack() }) {
        if (fromOcr) {
            AppCard(color = AppColors.SkippedSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FactCheck, contentDescription = null, tint = AppColors.Skipped, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t(if (ocr == OcrState.NothingFound) "ocr_nothing" else "ocr_check"), style = MaterialTheme.typography.bodyLarge)
                }
            }
            vm.ocrImage?.let { img ->
                Spacer(Modifier.height(10.dp))
                AsyncImage(img, contentDescription = t("prescription"), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceAlt))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TouchMin)) {
                    Checkbox(keepPhoto, { keepPhoto = it }, modifier = Modifier.size(TouchMin))
                    Text(t("keep_photo"), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        drafts.forEachIndexed { i, d ->
            Spacer(Modifier.height(12.dp))
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tf("med_no", Num.loc(i + 1, lang)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.drafts.value = drafts.filterIndexed { j, _ -> j != i } }, modifier = Modifier.heightIn(min = TouchMin)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = AppColors.Missed)
                        Text(t("remove"), color = AppColors.Missed, style = MaterialTheme.typography.labelLarge)
                    }
                }
                MedicineForm(d) { nd -> vm.drafts.value = drafts.toMutableList().also { it[i] = nd } }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigOutlineButton(t("add_another"), Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { vm.drafts.value = drafts + MedicineDraft() }
        Spacer(Modifier.height(12.dp))
        BigButton(tf("save_n", Num.loc(valid.size, lang)), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken, enabled = valid.isNotEmpty()) { confirm = true }
    }
    if (confirm) {
        ConfirmDialog(t("save_all_q"), valid.joinToString("\n") { it.name }, onYes = {
            confirm = false
            vm.saveDrafts(valid)
            val img = vm.ocrImage
            if (fromOcr && keepPhoto && img != null) {
                vm.addRecord(img, RecordKind.PRESCRIPTION, today, Strings.t("prescription", lang), "", "") { }
            }
            vm.ocrImage = null
            vm.drafts.value = emptyList()
            nav.popBackStack("medicines", inclusive = false)
        }, onNo = { confirm = false })
    }
}

@Composable
fun OcrScreen(vm: MainViewModel, nav: NavController) {
    val context = LocalContext.current
    val state by vm.ocrState.collectAsState()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val go: (Uri) -> Unit = { uri -> vm.runOcr(uri) { ok -> if (ok) nav.navigate("med_bulk?review=true") { popUpTo("med_ocr") { inclusive = true } } } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(go) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(go) }

    Page(vm, t("add_photo"), onBack = { nav.popBackStack() }, speak = t("ocr_tip")) {
        AppCard {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = AppColors.Skipped, modifier = Modifier.size(36.dp))
            Text(t("ocr_tip"), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        if (state == OcrState.Working) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
                Spacer(Modifier.height(12.dp))
                Text(t("ocr_reading"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            return@Page
        }
        BigButton(t("take_photo"), Modifier.fillMaxWidth(), icon = Icons.Filled.PhotoCamera, height = 80.dp) {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "rx_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri = uri
            runCatching { camera.launch(uri) }
        }
        Spacer(Modifier.height(12.dp))
        BigOutlineButton(t("from_gallery"), Modifier.fillMaxWidth(), icon = Icons.Filled.PhotoLibrary) { gallery.launch("image/*") }
        val err = when (state) {
            OcrState.NoModels -> "ocr_need_internet"
            OcrState.Failed -> "ocr_failed"
            else -> null
        }
        err?.let {
            Spacer(Modifier.height(14.dp))
            Text(t(it), color = AppColors.Missed, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            BigOutlineButton(t("type_instead"), Modifier.fillMaxWidth(), icon = Icons.Filled.Keyboard) {
                vm.drafts.value = listOf(MedicineDraft()); vm.ocrState.value = OcrState.Idle; nav.navigate("med_bulk")
            }
        }
    }
}

// ----------------------------------------------------------------- detail

@Composable
fun MedicineDetailScreen(vm: MainViewModel, nav: NavController, id: String) {
    val meds by vm.medicines.collectAsState()
    val logs by vm.doseLogs.collectAsState()
    val today by vm.today.collectAsState()
    val m = meds.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var editLog by remember { mutableStateOf<DoseLogEntity?>(null) }
    val lang = LocalLang.current
    if (m == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val myLogs = logs.filter { it.medicineId == id }.sortedWith(compareByDescending<DoseLogEntity> { it.date }.thenByDescending { it.at })
    val pct = DoseSchedule.adherence(myLogs, Dates.lastDays(30, today))
    val schedule = scheduleText(m)

    Page(vm, m.name, onBack = { nav.popBackStack() }, speak = "${m.name}. ${m.dose}. $schedule") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AppColors.HeroBrush).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillIcon(72)
            Spacer(Modifier.height(8.dp))
            Text(m.name, style = MaterialTheme.typography.displaySmall, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
            Text(listOf(m.dose, schedule).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                m.endDate?.let { Strings.f("course_until", lang, Num.date(it, lang)) } ?: Strings.t("continuous_long", lang),
                style = MaterialTheme.typography.bodyLarge, color = androidx.compose.ui.graphics.Color.White
            )
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedStateBadge(m.state(today))
                Spacer(Modifier.weight(1f))
                pct?.let { Text("${Num.loc(it, lang)}% ${t("taken_pct")}", style = MaterialTheme.typography.titleMedium, color = AppColors.Brand) }
            }
            Spacer(Modifier.height(10.dp))
            Text(t("last_2_weeks"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            val twoWeeks = vm.weekStatuses(id, 14) // oldest first; second row ends today
            WeekDots(twoWeeks.take(7))
            Spacer(Modifier.height(6.dp))
            WeekDots(twoWeeks.drop(7))
            if (m.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(t("notes"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                Text(m.notes, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("edit"), Modifier.weight(1f), icon = Icons.Filled.Edit) { nav.navigate("med_edit?id=${m.id}") }
            if (m.active) BigOutlineButton(t("stop"), Modifier.weight(1f), icon = Icons.Filled.Pause, color = AppColors.Missed) { confirmStop = true }
            else BigButton(t("resume"), Modifier.weight(1f), icon = Icons.Filled.PlayArrow, color = AppColors.Taken) { vm.setMedicineActive(m.id, true) }
        }
        Spacer(Modifier.height(10.dp))
        BigOutlineButton(t("delete"), Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = AppColors.Missed) { confirmDelete = true }

        SectionTitle(t("history"))
        if (myLogs.isEmpty()) Text(t("no_history"), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        if (myLogs.isNotEmpty()) Text(t("tap_to_change"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        AppCard(Modifier.padding(top = 8.dp), padding = 8.dp) {
            myLogs.take(60).forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = AppColors.Border.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { editLog = l }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Num.date(l.date, lang, withYear = false) + " · " + t("slot_${l.slot}"), style = MaterialTheme.typography.bodyLarge)
                        if (l.status != DoseStatus.MISSED.id) Text(Num.time(Dates.minuteOfDay(l.at), lang), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                    }
                    StatusBadge(DoseStatus.of(l.status), onClick = { editLog = l })
                }
            }
        }
    }
    editLog?.let { l ->
        DoseStatusDialog(m.name, DoseStatus.of(l.status), allowNotYet = false,
            onPick = { st -> if (st != null) vm.setLogStatus(l, st); editLog = null }, onDismiss = { editLog = null })
    }
    if (confirmStop) ConfirmDialog(Strings.f("stop_q", lang, m.name), t("stop_msg"), danger = true,
        onYes = { vm.setMedicineActive(m.id, false); confirmStop = false }, onNo = { confirmStop = false })
    if (confirmDelete) ConfirmDialog(Strings.f("delete_q", lang, m.name), t("delete_msg"), danger = true,
        onYes = { confirmDelete = false; vm.deleteMedicine(m.id); nav.popBackStack() }, onNo = { confirmDelete = false })
}
