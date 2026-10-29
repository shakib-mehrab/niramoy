package com.niramoy.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.niramoy.data.AppRepository
import com.niramoy.data.local.HealthLogEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.Level
import com.niramoy.model.Ranges
import com.niramoy.model.SugarTag
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Dates
import com.niramoy.util.Num

/** Very good -> very bad, each with a face and a word. */
val MOODS = listOf(5 to "😄", 4 to "🙂", 3 to "😐", 2 to "🙁", 1 to "😫")

@Composable
fun LevelBadge(level: Level) {
    val (c, icon, key) = when (level) {
        Level.HIGH -> Triple(AppColors.Missed, Icons.Filled.ArrowUpward, "lvl_high")
        Level.LOW -> Triple(AppColors.Skipped, Icons.Filled.ArrowDownward, "lvl_low")
        Level.NORMAL -> Triple(AppColors.Taken, Icons.Filled.Check, "lvl_normal")
    }
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(20.dp))
        Text(t(key), color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun levelColor(l: Level) = when (l) { Level.HIGH -> AppColors.Missed; Level.LOW -> AppColors.Skipped; Level.NORMAL -> AppColors.Taken }

@Composable
fun HealthScreen(vm: MainViewModel, nav: NavController) {
    val logs by vm.healthLogs.collectAsState()
    val today by vm.today.collectAsState()
    val lang = LocalLang.current
    val todayMood = logs.firstOrNull { it.date == today && it.mood != null }?.mood
    val context = LocalContext.current
    var noRecognizer by remember { mutableStateOf(false) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            vm.onSpeech(text)
            nav.navigate("health_entry?voice=true")
        }
    }

    Page(vm, t("tab_health"), speak = t("how_are_you")) {
        AppCard {
            Text(t("how_are_you"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MOODS.forEach { (v, face) ->
                    val on = todayMood == v
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .background(if (on) AppColors.BrandSoft else AppColors.Surface)
                            .border(2.dp, if (on) AppColors.Brand else AppColors.Border, RoundedCornerShape(18.dp))
                            .clickable(role = Role.RadioButton) { vm.setMood(v) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(face, fontSize = 34.sp)
                        Text(t("mood_$v"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Big mic: speak in Bangla, we fill the form, you confirm.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(AppColors.HeroBrush).clickable(role = Role.Button) {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "bn") "bn-BD" else "en-US")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, Strings.t("mic_prompt", lang))
                    }
                    try { speech.launch(i) } catch (_: Exception) { noRecognizer = true }
                },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Mic, contentDescription = t("speak"), tint = Color.White, modifier = Modifier.size(64.dp)) }
            Spacer(Modifier.height(6.dp))
            Text(t("speak"), style = MaterialTheme.typography.titleMedium, color = AppColors.Brand)
            Text(t("mic_example"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, textAlign = TextAlign.Center)
            if (noRecognizer) Text(t("mic_missing"), color = AppColors.Missed, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("sugar"), Icons.Filled.Bloodtype, Modifier.weight(1f), tint = AppColors.Missed) { nav.navigate("health_entry") }
            IconTile(t("pressure"), Icons.Filled.MonitorHeart, Modifier.weight(1f)) { nav.navigate("health_entry") }
            IconTile(t("other"), Icons.Filled.Add, Modifier.weight(1f), tint = AppColors.Taken) { nav.navigate("health_entry") }
        }

        val sugar = logs.filter { it.sugar != null }.take(14).reversed()
        if (sugar.size >= 2) {
            SectionTitle(t("sugar_trend"))
            AppCard {
                TrendChart(listOf(sugar.map { it.sugar!!.toFloat() }), sugar.map { levelColor(Ranges.sugar(it.sugar!!, SugarTag.of(it.sugarTag))) }, listOf(70f, 180f))
                Text(t("chart_hint"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }
        val bp = logs.filter { it.systolic != null && it.diastolic != null }.take(14).reversed()
        if (bp.size >= 2) {
            SectionTitle(t("bp_trend"))
            AppCard {
                TrendChart(listOf(bp.map { it.systolic!!.toFloat() }, bp.map { it.diastolic!!.toFloat() }),
                    bp.map { levelColor(Ranges.pressure(it.systolic!!, it.diastolic!!)) }, listOf(90f, 140f))
                Text(t("bp_chart_hint"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }

        SectionTitle(t("history"))
        if (logs.isEmpty()) EmptyState(Icons.Filled.MonitorHeart, t("no_health"))
        logs.take(50).forEach { h -> HealthCard(h, lang) { nav.navigate("health_entry?id=${h.id}") } }
        Disclaimer()
    }
}

/** Minimal line chart: dashed guide lines at [guides], one line per series, points coloured by level. */
@Composable
private fun TrendChart(series: List<List<Float>>, pointColors: List<Color>, guides: List<Float>) {
    val all = series.flatten() + guides
    val min = (all.minOrNull() ?: 0f) * 0.9f
    val max = (all.maxOrNull() ?: 1f) * 1.05f
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)) {
        fun y(v: Float) = size.height - (v - min) / (max - min) * size.height
        guides.forEach { g ->
            drawLine(AppColors.Border, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        series.forEachIndexed { si, values ->
            if (values.size < 2) return@forEachIndexed
            val step = size.width / (values.size - 1)
            val lineColor = if (si == 0) AppColors.Brand else AppColors.BrandLight
            for (i in 1 until values.size) drawLine(lineColor, Offset((i - 1) * step, y(values[i - 1])), Offset(i * step, y(values[i])), strokeWidth = 6f)
            values.forEachIndexed { i, v ->
                drawCircle(Color.White, 13f, Offset(i * step, y(v)))
                drawCircle(pointColors.getOrElse(i) { lineColor }, 10f, Offset(i * step, y(v)))
            }
        }
    }
}

@Composable
private fun HealthCard(h: HealthLogEntity, lang: String, onClick: () -> Unit) {
    AppCard(Modifier.padding(vertical = 6.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Num.date(h.date, lang, withYear = false) + " · " + Num.time(Dates.minuteOfDay(h.at), lang), style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextMuted, modifier = Modifier.weight(1f))
            h.mood?.let { m -> Text(MOODS.firstOrNull { it.first == m }?.second.orEmpty() + " " + t("mood_$m"), style = MaterialTheme.typography.bodyMedium) }
        }
        h.sugar?.let { v ->
            ValueRow(Icons.Filled.Bloodtype, t("sugar"), "${Num.loc(v, lang)} mg/dL" + (SugarTag.of(h.sugarTag)?.let { " · " + t("tag_${it.id}") } ?: ""),
                Ranges.sugar(v, SugarTag.of(h.sugarTag)))
        }
        if (h.systolic != null && h.diastolic != null) {
            ValueRow(Icons.Filled.MonitorHeart, t("pressure"), "${Num.loc(h.systolic, lang)}/${Num.loc(h.diastolic, lang)}", Ranges.pressure(h.systolic, h.diastolic))
        }
        h.pulse?.let { ValueRow(Icons.Filled.Favorite, t("pulse"), Num.loc(it, lang), Ranges.pulse(it)) }
        h.weight?.let { ValueRow(Icons.Filled.MonitorWeight, t("weight"), "${Num.loc(it, lang)} ${t("kg")}", null) }
        h.temperature?.let { ValueRow(Icons.Filled.Thermostat, t("temperature"), "${Num.loc(it, lang)}°F", Ranges.temperature(it)) }
        if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ValueRow(icon: ImageVector, label: String, value: String, level: Level?) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        level?.let { LevelBadge(it) }
    }
}

// ----------------------------------------------------------------- add / edit / voice confirm

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthEntryScreen(vm: MainViewModel, nav: NavController, id: String?, fromVoice: Boolean) {
    val logs by vm.healthLogs.collectAsState()
    val voice by vm.voiceResult.collectAsState()
    val lang = LocalLang.current
    val existing = id?.let { hid -> logs.firstOrNull { it.id == hid } }
    val v = if (fromVoice) voice else null
    fun str(d: Double?) = d?.let { Num.loc(it, lang) }.orEmpty()
    fun str(i: Int?) = i?.let { Num.loc(it, lang) }.orEmpty()

    var mood by remember { mutableStateOf(v?.mood ?: existing?.mood) }
    var sugar by remember { mutableStateOf(str(v?.sugar ?: existing?.sugar)) }
    var tag by remember { mutableStateOf(v?.sugarTag ?: SugarTag.of(existing?.sugarTag)) }
    var sys by remember { mutableStateOf(str(v?.systolic ?: existing?.systolic)) }
    var dia by remember { mutableStateOf(str(v?.diastolic ?: existing?.diastolic)) }
    var pulse by remember { mutableStateOf(str(v?.pulse ?: existing?.pulse)) }
    var weight by remember { mutableStateOf(str(v?.weight ?: existing?.weight)) }
    var temp by remember { mutableStateOf(str(v?.temperature ?: existing?.temperature)) }
    var note by remember { mutableStateOf(v?.note ?: existing?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    Page(vm, t(if (fromVoice) "voice_confirm_title" else if (existing != null) "edit_entry" else "new_entry"), onBack = { nav.popBackStack() }) {
        if (v != null) {
            AppCard(color = AppColors.SkippedSoft) {
                Text(t("you_said"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                Text("“${v.note}”", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(t(if (v.isEmpty) "voice_nothing" else "voice_check"), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        AppCard {
            Text(t("how_are_you"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                MOODS.forEach { (m, face) -> ChoiceChip("$face ${t("mood_$m")}", mood == m) { mood = if (mood == m) null else m } }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(t("sugar") + " (mg/dL)", style = MaterialTheme.typography.titleMedium)
            BigField(t("sugar_value"), sugar, { sugar = it }, keyboard = KeyboardType.Decimal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SugarTag.entries.forEach { st -> ChoiceChip(t("tag_${st.id}"), tag == st) { tag = if (tag == st) null else st } }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(t("pressure"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigField(t("bp_upper"), sys, { sys = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                BigField(t("bp_lower"), dia, { dia = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            Spacer(Modifier.height(8.dp))
            BigField(t("pulse"), pulse, { pulse = it }, keyboard = KeyboardType.Number)
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigField(t("weight") + " (${t("kg")})", weight, { weight = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                BigField(t("temperature") + " (°F)", temp, { temp = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            Spacer(Modifier.height(8.dp))
            BigField(t("note"), note, { note = it }, singleLine = false)
        }
        Spacer(Modifier.height(16.dp))
        BigButton(t("save"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken) {
            val now = System.currentTimeMillis()
            val sysV = Num.parseInt(sys); val diaV = Num.parseInt(dia)
            val entry = (existing ?: HealthLogEntity(AppRepository.newId(), now, Dates.today(now), updatedAt = now)).copy(
                mood = mood,
                sugar = Num.parseDouble(sugar),
                sugarTag = if (Num.parseDouble(sugar) != null) tag?.id else null,
                systolic = if (sysV != null && diaV != null) sysV else null,
                diastolic = if (sysV != null && diaV != null) diaV else null,
                pulse = Num.parseInt(pulse),
                weight = Num.parseDouble(weight),
                temperature = Num.parseDouble(temp),
                note = note.trim()
            )
            vm.saveHealth(entry)
            vm.voiceResult.value = null
            nav.popBackStack()
        }
        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            BigOutlineButton(t("delete"), Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, color = AppColors.Missed) { confirmDelete = true }
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog(t("delete_entry_q"), danger = true,
        onYes = { confirmDelete = false; vm.deleteHealth(existing.id); nav.popBackStack() }, onNo = { confirmDelete = false })
}
