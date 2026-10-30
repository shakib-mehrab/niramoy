package com.niramoy.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseStatus
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Dates
import com.niramoy.util.Num
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Current UI language ("en" default, "bn"). */
val LocalLang = staticCompositionLocalOf { "en" }

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

/** Minimum touch target for every control (comfortable for all ages). */
val TouchMin: Dp = 52.dp

val CardShape = RoundedCornerShape(26.dp)

/** Soft, airy card (the main building block of every screen). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Surface,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(3.dp, CardShape, ambientColor = AppColors.Brand.copy(alpha = 0.10f), spotColor = AppColors.Brand.copy(alpha = 0.12f))
            .clip(CardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** Primary pill button. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = AppColors.Brand,
    contentColor: Color = Color.White,
    height: Dp = 56.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor,
            disabledContainerColor = AppColors.SurfaceAlt, disabledContentColor = AppColors.TextMuted.copy(alpha = 0.6f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Secondary pill button: light surface with a hairline border (like "View all logs" in the reference). */
@Composable
fun BigOutlineButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = AppColors.Brand, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.Surface, contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Round icon button on a light disc (bell / menu / add in the reference). */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                     size: Dp = 48.dp, container: Color = AppColors.Surface, tint: Color = AppColors.Text) {
    Box(
        modifier
            .size(size)
            .shadow(2.dp, CircleShape, spotColor = AppColors.Brand.copy(alpha = 0.12f))
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

/** Shortcut tile: icon in a soft square + short label ("Documents / Plans / Download" in the reference). */
@Composable
fun IconTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppColors.Brand, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 104.dp)
            .shadow(3.dp, CardShape, spotColor = AppColors.Brand.copy(alpha = 0.12f))
            .clip(CardShape)
            .background(AppColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Top bar: back disc, title, optional read-aloud and trailing actions, and the compact
 * EN / বাং language pill (always reachable).
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
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, Strings.t("back", lang), onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, modifier = Modifier.weight(1f), maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (speakText != null) SpeakButton(speakText)
        trailing?.invoke()
        Spacer(Modifier.width(6.dp))
        LanguageToggle(lang, onToggleLang)
    }
}

@Composable
fun LanguageToggle(lang: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(AppColors.Surface)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("en" to "EN", "bn" to "বাং").forEach { (code, label) ->
            val on = lang == code
            Box(
                Modifier.fillMaxHeight().clip(CircleShape).background(if (on) AppColors.Brand else Color.Transparent).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else AppColors.TextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Reads [text] aloud with the phone's free built-in TTS (Bangla when available). */
@Composable
fun SpeakButton(text: String, tint: Color = AppColors.Brand) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tts?.stop(); tts?.shutdown() } }
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
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = Strings.t("read_aloud", lang), tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Small "See all ›" link for section titles. */
@Composable
fun SeeAll(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 40.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = AppColors.Brand)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(20.dp))
    }
}

data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

fun statusStyle(status: DoseStatus?) = when (status) {
    DoseStatus.TAKEN -> Quad(AppColors.TakenSoft, AppColors.Taken, Icons.Filled.CheckCircle, "st_taken")
    DoseStatus.MISSED -> Quad(AppColors.MissedSoft, AppColors.Missed, Icons.Filled.Cancel, "st_missed")
    DoseStatus.SKIPPED -> Quad(AppColors.SkippedSoft, AppColors.Skipped, Icons.Filled.RemoveCircle, "st_skipped")
    null -> Quad(AppColors.UpcomingSoft, AppColors.Upcoming, Icons.Filled.Schedule, "st_waiting")
}

/** Taken / missed / skipped / waiting: colour + icon + word together, never colour alone. Tappable when [onClick] is set. */
@Composable
fun StatusBadge(status: DoseStatus?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (bg, fg, icon, key) = statusStyle(status)
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(t(key), color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (onClick != null) Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
    }
}

/** Last N days as dots: ✓ taken, ✕ missed, – skipped, empty = nothing logged. */
@Composable
fun WeekDots(statuses: List<DoseStatus?>, modifier: Modifier = Modifier, dot: Dp = 20.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        statuses.forEach { st ->
            val (c, icon) = when (st) {
                DoseStatus.TAKEN -> AppColors.Taken to Icons.Filled.Check
                DoseStatus.MISSED -> AppColors.Missed to Icons.Filled.Close
                DoseStatus.SKIPPED -> AppColors.Skipped to Icons.Filled.Remove
                null -> AppColors.Border to null
            }
            Box(Modifier.size(dot).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(dot * 0.68f))
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String? = null, yes: String = t("yes"), no: String = t("no"), danger: Boolean = false, onYes: () -> Unit, onNo: () -> Unit) {
    Dialog(onDismissRequest = onNo, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(AppColors.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = AppColors.Text)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = AppColors.TextMuted)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigOutlineButton(no, Modifier.weight(1f), color = AppColors.TextMuted, onClick = onNo)
                BigButton(yes, Modifier.weight(1f), color = if (danger) AppColors.Missed else AppColors.Brand, onClick = onYes)
            }
        }
    }
}

/** Bottom sheet-like chooser shown as a dialog: big rows with icon + label. */
@Composable
fun <T> OptionDialog(title: String, options: List<Triple<T, ImageVector, String>>, selected: T?, onPick: (T) -> Unit, onDismiss: () -> Unit, tints: (T) -> Color = { AppColors.Brand }) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(AppColors.Surface).padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
            Spacer(Modifier.height(12.dp))
            options.forEach { (value, icon, label) ->
                val on = value == selected
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (on) AppColors.BrandSoft else AppColors.SurfaceAlt.copy(alpha = 0.6f))
                        .clickable(role = Role.RadioButton) { onPick(value) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = tints(value), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, color = AppColors.Text, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.Brand)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(t("cancel"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Filled rounded text field. */
@Composable
fun BigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = AppColors.TextMuted.copy(alpha = 0.7f)) } },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = AppColors.Surface, focusedContainerColor = AppColors.Surface,
            unfocusedBorderColor = AppColors.Border, focusedBorderColor = AppColors.Brand
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = TouchMin)
    )
}

/** [−] value [+] stepper. */
@Composable
fun Stepper(label: String?, display: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            RoundIconButton(Icons.Filled.Remove, t("less"), onMinus)
            Text(display, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Add, t("more"), onPlus)
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, color: Color = AppColors.Brand) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color)
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp)) }
}

/** Time chooser: big time + hour / minute steppers. */
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

/** Pill chip (slots, meal timing, dose presets, tones...). */
@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 46.dp)
            .clip(CircleShape)
            .background(if (selected) AppColors.Brand else AppColors.Surface)
            .border(1.dp, if (selected) AppColors.Brand else AppColors.Border, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        else if (icon != null) Icon(icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(18.dp))
        if (selected || icon != null) Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else AppColors.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(AppColors.Surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AppColors.BrandLight, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted, textAlign = TextAlign.Center)
    }
}

/** "Reminder tool, not medical advice" line. */
@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(t("disclaimer"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

/** Bottom space so content clears the floating navigation bar. */
val LocalBottomSpace = staticCompositionLocalOf { 24.dp }

/** Scrollable screen body with the standard padding. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = LocalBottomSpace.current),
        content = content
    )
}

/** Horizontal week strip (reference: "3 Sun · 4 Mon · [5 Tue] ..."), centred on today. */
@Composable
fun DateStrip(selected: String, today: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val days = remember(today) { (-3..3).map { Dates.addDays(today, it) } }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            val on = d == selected
            Column(
                Modifier
                    .width(50.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (on) AppColors.Brand else AppColors.Surface.copy(alpha = 0.7f))
                    .border(1.dp, if (d == today && !on) AppColors.BrandLight else Color.Transparent, RoundedCornerShape(26.dp))
                    .clickable(role = Role.Tab) { onSelect(d) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(Num.loc(Dates.dayOfMonth(d), lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else AppColors.Text)
                Text(Num.shortDayName(Dates.dayOfWeek(d), lang), style = MaterialTheme.typography.labelSmall,
                    color = if (on) Color.White.copy(alpha = 0.9f) else AppColors.TextMuted, maxLines = 1)
                if (d == today) Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(if (on) Color.White else AppColors.Brand))
            }
        }
    }
}

/** Semicircle gauge with a needle (reference "Medication Tracked"). [value] 0..1. */
@Composable
fun Gauge(value: Float, modifier: Modifier = Modifier, track: Color = AppColors.SurfaceAlt, fill: Color = AppColors.BrandLight, needle: Color = AppColors.Text, showNeedle: Boolean = true) {
    val v by animateFloatAsState(value.coerceIn(0f, 1f), tween(900), label = "gauge")
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val stroke = size.width * 0.12f
        val d = size.width - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arc = Size(d, d)
        drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(fill, 180f, 180f * v, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        if (!showNeedle) return@Canvas
        val c = Offset(size.width / 2, stroke / 2 + d / 2)
        val angle = Math.toRadians((180.0 + 180.0 * v))
        val len = d / 2 * 0.62f
        val tip = Offset(c.x + (len * cos(angle)).toFloat(), c.y + (len * sin(angle)).toFloat())
        drawLine(needle, c, tip, strokeWidth = stroke * 0.28f, cap = StrokeCap.Round)
        drawCircle(needle, stroke * 0.42f, c)
        drawCircle(Color.White, stroke * 0.16f, c)
    }
}

/** Ring progress with content in the middle (reference heart-health ring). */
@Composable
fun RingProgress(value: Float, modifier: Modifier = Modifier, fill: Color = AppColors.BrandLight, track: Color = AppColors.SurfaceAlt,
                 content: @Composable BoxScope.() -> Unit) {
    val v by animateFloatAsState(value.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.10f
            val r = Size(size.minDimension - stroke, size.minDimension - stroke)
            val tl = Offset((size.width - r.width) / 2, (size.height - r.height) / 2)
            drawArc(track, 135f, 270f, false, tl, r, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(fill, 135f, 270f * v, false, tl, r, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Small rounded icon square used at the start of list rows. */
@Composable
fun IconSquare(icon: ImageVector, tint: Color = AppColors.Brand, size: Dp = 44.dp, container: Color = tint.copy(alpha = 0.12f)) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
