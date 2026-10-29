package com.niramoy.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseStatus
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Num
import java.util.Locale

/** Current UI language ("bn" / "en"). */
val LocalLang = staticCompositionLocalOf { "bn" }

@Composable
fun t(key: String): String = Strings.t(key, LocalLang.current)

@Composable
fun tf(key: String, vararg args: Any): String = Strings.f(key, LocalLang.current, *args)

/** Number with Bangla digits in Bangla mode. */
@Composable
fun n(value: Any): String = when (value) {
    is Double -> Num.loc(value, LocalLang.current)
    else -> Num.loc(value.toString(), LocalLang.current)
}

/** Minimum touch target for every control (elderly users). */
val TouchMin: Dp = 56.dp

@Composable
fun AppCard(modifier: Modifier = Modifier, color: Color = AppColors.Surface, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = AppColors.Brand.copy(alpha = 0.15f), spotColor = AppColors.Brand.copy(alpha = 0.18f))
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content
    )
}

/** Large filled button: icon + one/two-word label. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = AppColors.Brand,
    contentColor: Color = Color.White,
    height: Dp = 64.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlineButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = AppColors.Brand, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Big square tile with a picture-like icon and a short label (used for menus instead of lists). */
@Composable
fun IconTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppColors.Brand, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .heightIn(min = 130.dp)
            .shadow(4.dp, shape, spotColor = AppColors.Brand.copy(alpha = 0.15f))
            .clip(shape)
            .background(AppColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = AppColors.Text)
    }
}

/**
 * Top bar shown on every screen: back (if any), title, read-aloud, and the always-visible
 * language switch "বাংলা | EN".
 */
@Composable
fun TopBar(
    title: String,
    lang: String,
    onToggleLang: () -> Unit,
    onBack: (() -> Unit)? = null,
    speakText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(TouchMin)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.t("back", lang), tint = AppColors.Text, modifier = Modifier.size(32.dp))
            }
        } else Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.Text,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        if (speakText != null) SpeakButton(speakText)
        trailing?.invoke()
        LanguageToggle(lang, onToggleLang)
    }
}

@Composable
fun LanguageToggle(lang: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .padding(start = 4.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, AppColors.Brand, RoundedCornerShape(24.dp))
            .clickable(role = Role.Switch, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("bn" to "বাংলা", "en" to "EN").forEach { (code, label) ->
            val on = lang == code
            Box(
                Modifier.heightIn(min = 48.dp).background(if (on) AppColors.Brand else Color.Transparent).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else AppColors.Brand, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

/** Speaker icon that reads [text] aloud with the phone's free built-in TTS (Bangla when available). */
@Composable
fun SpeakButton(text: String) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }
    IconButton(
        onClick = {
            val speak: (TextToSpeech) -> Unit = { engine ->
                val loc = if (lang == "bn") Locale("bn", "BD") else Locale.US
                if (engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) engine.language = loc
                else if (lang == "bn") engine.language = Locale("bn", "IN")
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "niramoy")
            }
            val existing = tts
            if (existing != null) speak(existing)
            else {
                var created: TextToSpeech? = null
                created = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) created?.let(speak) }
                tts = created
            }
        },
        modifier = Modifier.size(TouchMin)
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = Strings.t("read_aloud", lang), tint = AppColors.Brand, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Taken / missed / skipped / waiting: colour + icon + word together, never colour alone. */
@Composable
fun StatusBadge(status: DoseStatus?, modifier: Modifier = Modifier) {
    val (bg, fg, icon, key) = when (status) {
        DoseStatus.TAKEN -> Quad(AppColors.TakenSoft, AppColors.Taken, Icons.Filled.CheckCircle, "st_taken")
        DoseStatus.MISSED -> Quad(AppColors.MissedSoft, AppColors.Missed, Icons.Filled.Cancel, "st_missed")
        DoseStatus.SKIPPED -> Quad(AppColors.SkippedSoft, AppColors.Skipped, Icons.Filled.RemoveCircle, "st_skipped")
        null -> Quad(AppColors.UpcomingSoft, AppColors.Upcoming, Icons.Filled.Schedule, "st_waiting")
    }
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(t(key), color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** Last 7 days as dots (like the reference): green ✓ taken, red ✕ missed, amber – skipped, grey = nothing. */
@Composable
fun WeekDots(statuses: List<DoseStatus?>, modifier: Modifier = Modifier, dot: Dp = 22.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        statuses.forEach { st ->
            val (c, icon) = when (st) {
                DoseStatus.TAKEN -> AppColors.Taken to Icons.Filled.Check
                DoseStatus.MISSED -> AppColors.Missed to Icons.Filled.Close
                DoseStatus.SKIPPED -> AppColors.Skipped to Icons.Filled.Remove
                null -> AppColors.Border to null
            }
            Box(Modifier.size(dot).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(dot * 0.7f))
            }
        }
    }
}

/** Two big buttons "হ্যাঁ" / "না". */
@Composable
fun ConfirmDialog(title: String, message: String? = null, yes: String = t("yes"), no: String = t("no"), danger: Boolean = false, onYes: () -> Unit, onNo: () -> Unit) {
    Dialog(onDismissRequest = onNo, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AppColors.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = AppColors.Text)
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = AppColors.TextMuted)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BigOutlineButton(no, Modifier.weight(1f), icon = Icons.Filled.Close, color = AppColors.TextMuted, onClick = onNo)
                BigButton(yes, Modifier.weight(1f), icon = Icons.Filled.Check, color = if (danger) AppColors.Missed else AppColors.Taken, onClick = onYes)
            }
        }
    }
}

/** Large labelled text field (20sp+). */
@Composable
fun BigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = TouchMin)
    )
}

/** [−] value [+] with big round buttons; used for times, counts and days. */
@Composable
fun Stepper(label: String?, display: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (label != null) Text(label, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RoundIconButton(Icons.Filled.Remove, t("less"), onMinus)
            Text(display, style = MaterialTheme.typography.headlineMedium, color = AppColors.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Add, t("more"), onPlus)
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, color: Color = AppColors.Brand) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color)
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(34.dp)) }
}

/** Big time chooser: hour and minute steppers, shown as "সকাল ৮:৩০". */
@Composable
fun TimeChooser(minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    Column(modifier) {
        Text(Num.time(minuteOfDay, lang), style = MaterialTheme.typography.displayMedium, color = AppColors.Brand,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Stepper(t("hour"), Num.loc((if (minuteOfDay / 60 % 12 == 0) 12 else minuteOfDay / 60 % 12).toString(), lang),
            onMinus = { onChange((minuteOfDay - 60 + 1440) % 1440) }, onPlus = { onChange((minuteOfDay + 60) % 1440) })
        Stepper(t("minute"), Num.loc((minuteOfDay % 60).toString().padStart(2, '0'), lang),
            onMinus = { onChange((minuteOfDay - 5 + 1440) % 1440) }, onPlus = { onChange((minuteOfDay + 5) % 1440) })
    }
}

/** Large selectable chip (slots, meal timing, dose presets, tones...). */
@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .heightIn(min = TouchMin)
            .clip(shape)
            .background(if (selected) AppColors.Brand else AppColors.Surface)
            .border(2.dp, if (selected) AppColors.Brand else AppColors.Border, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        else if (icon != null) Icon(icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else AppColors.Text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AppColors.Border, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted, textAlign = TextAlign.Center)
    }
}

/** "Reminder tool, not medical advice" line. */
@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(t("disclaimer"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

/** A scrollable screen body with the standard padding and bottom space. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        content = content
    )
}
