package com.niramoy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.niramoy.data.AppRepository
import com.niramoy.data.local.*
import com.niramoy.i18n.Strings
import com.niramoy.model.RecordKind
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Num
import java.io.File

// ----------------------------------------------------------------- hub

@Composable
fun RecordsScreen(vm: MainViewModel, nav: NavController) {
    val visits by vm.visits.collectAsState()
    val today by vm.today.collectAsState()
    val lang = LocalLang.current
    val upcoming = visits.mapNotNull { v -> v.nextDate?.takeIf { it >= today }?.let { v to it } }.minByOrNull { it.second }

    Page(vm, t("tab_records")) {
        upcoming?.let { (v, date) ->
            AppCard(color = AppColors.BrandSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t("next_visit"), style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
                        Text(Num.date(date, lang), style = MaterialTheme.typography.titleLarge, color = AppColors.Brand)
                        if (v.doctorName.isNotBlank()) Text(v.doctorName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("visits"), Icons.Filled.LocalHospital, Modifier.weight(1f)) { nav.navigate("visits") }
            IconTile(t("prescriptions"), Icons.Filled.Description, Modifier.weight(1f)) { nav.navigate("files/prescription") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("reports"), Icons.Filled.Science, Modifier.weight(1f)) { nav.navigate("files/report") }
            IconTile(t("doctors"), Icons.Filled.ContactPhone, Modifier.weight(1f)) { nav.navigate("doctors") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("my_info"), Icons.Filled.Badge, Modifier.weight(1f)) { nav.navigate("profile") }
            IconTile(t("emergency"), Icons.Filled.Sos, Modifier.weight(1f), tint = AppColors.Emergency) { nav.navigate("emergency") }
        }
        Disclaimer()
    }
}

/** Full-width form sheet used by the small editors below. */
@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AppColors.Background)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigOutlineButton(t("cancel"), Modifier.weight(1f), color = AppColors.TextMuted, onClick = onDismiss)
                BigButton(t("save"), Modifier.weight(1f), icon = Icons.Filled.Check, color = AppColors.Taken, enabled = saveEnabled, onClick = onSave)
            }
        }
    }
}

/** A date row with a "change" button. */
@Composable
fun DateField(label: String, date: String?, onChange: (String?) -> Unit, allowClear: Boolean = false, defaultDate: String) {
    val lang = LocalLang.current
    var pick by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(date?.let { Num.date(it, lang) } ?: t("not_set"), style = MaterialTheme.typography.titleLarge, color = AppColors.Brand, modifier = Modifier.weight(1f))
        TextButton(onClick = { pick = true }, modifier = Modifier.heightIn(min = TouchMin)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(t("change"), style = MaterialTheme.typography.labelLarge)
        }
        if (allowClear && date != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.heightIn(min = TouchMin)) {
            Text(t("clear"), style = MaterialTheme.typography.labelLarge, color = AppColors.Missed)
        }
    }
    if (pick) DatePick(date ?: defaultDate, onPick = { onChange(it); pick = false }, onDismiss = { pick = false })
}

// ----------------------------------------------------------------- doctor visits

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitsScreen(vm: MainViewModel, nav: NavController) {
    val visits by vm.visits.collectAsState()
    val doctors by vm.doctors.collectAsState()
    val today by vm.today.collectAsState()
    val lang = LocalLang.current
    var editing by remember { mutableStateOf<VisitEntity?>(null) }
    var deleting by remember { mutableStateOf<VisitEntity?>(null) }

    Page(vm, t("visits"), onBack = { nav.popBackStack() }) {
        BigButton(t("add_visit"), Modifier.fillMaxWidth(), icon = Icons.Filled.Add) {
            editing = VisitEntity(AppRepository.newId(), today, "", updatedAt = 0)
        }
        Spacer(Modifier.height(8.dp))
        if (visits.isEmpty()) EmptyState(Icons.Filled.LocalHospital, t("no_visits"))
        visits.forEach { v ->
            AppCard(Modifier.padding(vertical = 6.dp), onClick = { editing = v }) {
                Text(Num.date(v.date, lang), style = MaterialTheme.typography.titleLarge)
                if (v.doctorName.isNotBlank()) Text(v.doctorName, style = MaterialTheme.typography.bodyLarge, color = AppColors.Brand)
                if (v.notes.isNotBlank()) Text(v.notes, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                v.nextDate?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(if (v.remindNext) Icons.Filled.NotificationsActive else Icons.Filled.Event, contentDescription = null, tint = AppColors.Brand)
                        Spacer(Modifier.width(6.dp))
                        Text(tf("next_visit_on", Num.date(it, lang)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    editing?.let { v0 ->
        var v by remember(v0.id) { mutableStateOf(v0) }
        FormDialog(t("visit"), onDismiss = { editing = null }, onSave = { vm.saveVisit(v); editing = null }) {
            DateField(t("visit_date"), v.date, { it?.let { d -> v = v.copy(date = d) } }, defaultDate = today)
            Spacer(Modifier.height(8.dp))
            BigField(t("doctor"), v.doctorName, { v = v.copy(doctorName = it) })
            if (doctors.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                doctors.forEach { d -> ChoiceChip(d.name, v.doctorName == d.name) { v = v.copy(doctorName = d.name) } }
            }
            Spacer(Modifier.height(8.dp))
            BigField(t("notes"), v.notes, { v = v.copy(notes = it) }, singleLine = false, placeholder = t("visit_notes_hint"))
            Spacer(Modifier.height(10.dp))
            DateField(t("next_visit"), v.nextDate, { v = v.copy(nextDate = it) }, allowClear = true, defaultDate = today)
            if (v.nextDate != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(v.remindNext, { v = v.copy(remindNext = it) })
                Spacer(Modifier.width(8.dp))
                Text(t("remind_visit"), style = MaterialTheme.typography.bodyLarge)
            }
            if (v0.updatedAt != 0L) TextButton(onClick = { deleting = v0; editing = null }, modifier = Modifier.heightIn(min = TouchMin)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = AppColors.Missed)
                Text(t("delete"), color = AppColors.Missed, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    deleting?.let { v -> ConfirmDialog(t("delete_entry_q"), danger = true, onYes = { vm.deleteVisit(v); deleting = null }, onNo = { deleting = null }) }
}

// ----------------------------------------------------------------- prescriptions / reports

@Composable
fun FilesScreen(vm: MainViewModel, nav: NavController, kind: RecordKind) {
    val all by vm.records.collectAsState()
    val today by vm.today.collectAsState()
    val doctors by vm.doctors.collectAsState()
    val context = LocalContext.current
    val lang = LocalLang.current
    val list = all.filter { it.kind == kind.id }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewing by remember { mutableStateOf<RecordFileEntity?>(null) }
    var deleting by remember { mutableStateOf<RecordFileEntity?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) picked = cameraUri }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked = it }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }
    val isRx = kind == RecordKind.PRESCRIPTION

    Page(vm, t(if (isRx) "prescriptions" else "reports"), onBack = { nav.popBackStack() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(t("take_photo"), Icons.Filled.PhotoCamera, Modifier.weight(1f)) {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "rec_${System.currentTimeMillis()}.jpg"))
                cameraUri = uri
                runCatching { camera.launch(uri) }
            }
            IconTile(t("from_gallery"), Icons.Filled.PhotoLibrary, Modifier.weight(1f)) { gallery.launch("image/*") }
            if (!isRx) IconTile("PDF", Icons.Filled.PictureAsPdf, Modifier.weight(1f)) { pdf.launch(arrayOf("application/pdf")) }
        }
        if (!com.niramoy.data.FileStorage.isConfigured) {
            Text(t("files_local_only"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) EmptyState(Icons.Filled.Description, t("no_files"))
        list.forEach { r ->
            AppCard(Modifier.padding(vertical = 6.dp), onClick = { viewing = r }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordThumb(vm, r)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Num.date(r.date, lang), style = MaterialTheme.typography.titleMedium)
                        val sub = listOf(r.title, r.doctorName, r.reportType).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (r.remotePath.isNotBlank()) Icons.Filled.CloudDone else Icons.Filled.CloudUpload, contentDescription = null,
                                tint = if (r.remotePath.isNotBlank()) AppColors.Taken else AppColors.TextMuted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t(if (r.remotePath.isNotBlank()) "saved_cloud" else "waiting_upload"), style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                        }
                    }
                }
            }
        }
    }

    picked?.let { uri ->
        var date by remember(uri) { mutableStateOf(today) }
        var title by remember(uri) { mutableStateOf("") }
        var doctor by remember(uri) { mutableStateOf("") }
        var busy by remember(uri) { mutableStateOf(false) }
        FormDialog(t(if (isRx) "new_prescription" else "new_report"), onDismiss = { picked = null }, saveEnabled = !busy, onSave = {
            busy = true
            vm.addRecord(uri, kind, date, title, if (isRx) doctor else "", if (isRx) "" else title) { ok ->
                busy = false; picked = null
                if (!ok) android.widget.Toast.makeText(context, Strings.t("file_failed", lang), android.widget.Toast.LENGTH_LONG).show()
            }
        }) {
            DateField(t("date"), date, { it?.let { d -> date = d } }, defaultDate = today)
            Spacer(Modifier.height(8.dp))
            if (isRx) {
                BigField(t("doctor"), doctor, { doctor = it })
                if (doctors.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    doctors.take(3).forEach { d -> ChoiceChip(d.name, doctor == d.name) { doctor = d.name } }
                }
            } else {
                BigField(t("report_type"), title, { title = it }, placeholder = t("report_type_hint"))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }

    viewing?.let { r -> RecordViewer(vm, r, onClose = { viewing = null }, onDelete = { deleting = r; viewing = null }) }
    deleting?.let { r -> ConfirmDialog(t("delete_file_q"), danger = true, onYes = { vm.deleteRecord(r); deleting = null }, onNo = { deleting = null }) }
}

@Composable
private fun RecordThumb(vm: MainViewModel, r: RecordFileEntity) {
    var file by remember(r.id, r.localPath) { mutableStateOf<File?>(null) }
    LaunchedEffect(r.id, r.localPath) { if (r.mime != "application/pdf") file = vm.fileFor(r) }
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.SurfaceAlt), contentAlignment = Alignment.Center) {
        if (file != null) AsyncImage(file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(if (r.mime == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Image, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun RecordViewer(vm: MainViewModel, r: RecordFileEntity, onClose: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(r.id) { file = vm.fileFor(r); loading = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppColors.Background).padding(12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(64.dp))
                    file == null -> Text(t("file_unavailable"), style = MaterialTheme.typography.bodyLarge)
                    r.mime == "application/pdf" -> BigButton(t("open_pdf"), icon = Icons.Filled.PictureAsPdf) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file!!)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    }
                    else -> ZoomableImage(file!!)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.navigationBarsPadding()) {
                BigOutlineButton(t("delete"), Modifier.weight(1f), icon = Icons.Filled.Delete, color = AppColors.Missed, onClick = onDelete)
                BigButton(t("close"), Modifier.weight(1f), icon = Icons.Filled.Close, onClick = onClose)
            }
        }
    }
}

/** Pinch to zoom is optional; the image fits the screen by default and the + button zooms (no gesture-only actions). */
@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(file, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale))
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(Icons.Filled.ZoomOut, t("zoom_out"), { scale = (scale - 0.5f).coerceAtLeast(1f) })
            RoundIconButton(Icons.Filled.ZoomIn, t("zoom_in"), { scale = (scale + 0.5f).coerceAtMost(4f) })
        }
    }
}


// ----------------------------------------------------------------- doctor phone book

@Composable
fun DoctorsScreen(vm: MainViewModel, nav: NavController) {
    val doctors by vm.doctors.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<DoctorEntity?>(null) }
    var deleting by remember { mutableStateOf<DoctorEntity?>(null) }

    Page(vm, t("doctors"), onBack = { nav.popBackStack() }) {
        BigButton(t("add_doctor"), Modifier.fillMaxWidth(), icon = Icons.Filled.PersonAdd) { editing = DoctorEntity(AppRepository.newId(), "", updatedAt = 0) }
        Spacer(Modifier.height(8.dp))
        if (doctors.isEmpty()) EmptyState(Icons.Filled.ContactPhone, t("no_doctors"))
        doctors.forEach { d ->
            AppCard(Modifier.padding(vertical = 6.dp)) {
                Text(d.name, style = MaterialTheme.typography.titleLarge)
                if (d.speciality.isNotBlank()) Text(d.speciality, style = MaterialTheme.typography.bodyLarge, color = AppColors.Brand)
                if (d.address.isNotBlank()) Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = AppColors.TextMuted)
                    Text(d.address, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.phone.isNotBlank()) BigButton(t("call"), Modifier.weight(1f), icon = Icons.Filled.Call, color = AppColors.Taken) { dial(context, d.phone) }
                    BigOutlineButton(t("edit"), Modifier.weight(1f), icon = Icons.Filled.Edit) { editing = d }
                }
            }
        }
    }
    editing?.let { d0 ->
        var d by remember(d0.id) { mutableStateOf(d0) }
        FormDialog(t("doctor"), onDismiss = { editing = null }, saveEnabled = d.name.isNotBlank(), onSave = { vm.saveDoctor(d); editing = null }) {
            BigField(t("name"), d.name, { d = d.copy(name = it) })
            Spacer(Modifier.height(8.dp))
            BigField(t("speciality"), d.speciality, { d = d.copy(speciality = it) }, placeholder = t("speciality_hint"))
            Spacer(Modifier.height(8.dp))
            BigField(t("phone"), d.phone, { d = d.copy(phone = it) }, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(8.dp))
            BigField(t("chamber"), d.address, { d = d.copy(address = it) }, singleLine = false)
            if (d0.updatedAt != 0L) TextButton(onClick = { deleting = d0; editing = null }, modifier = Modifier.heightIn(min = TouchMin)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = AppColors.Missed)
                Text(t("delete"), color = AppColors.Missed, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    deleting?.let { d -> ConfirmDialog(Strings.f("delete_q", LocalLang.current, d.name), danger = true, onYes = { vm.deleteDoctor(d); deleting = null }, onNo = { deleting = null }) }
}

// ----------------------------------------------------------------- health profile + emergency contacts

@Composable
fun ProfileScreen(vm: MainViewModel, nav: NavController) {
    val s by vm.settings.collectAsState()
    val contacts by vm.contacts.collectAsState()
    var name by remember(s.name) { mutableStateOf(s.name) }
    var allergies by remember(s.allergies) { mutableStateOf(s.allergies) }
    var editing by remember { mutableStateOf<ContactEntity?>(null) }
    var deleting by remember { mutableStateOf<ContactEntity?>(null) }

    Page(vm, t("my_info"), onBack = { nav.popBackStack() }) {
        AppCard {
            BigField(t("name"), name, { name = it })
            Spacer(Modifier.height(12.dp))
            Text(t("blood_group"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            BloodGroupPicker(s.bloodGroup) { g -> vm.updateSettings { it.copy(bloodGroup = g) } }
            Spacer(Modifier.height(12.dp))
            BigField(t("allergies"), allergies, { allergies = it }, singleLine = false, placeholder = t("allergies_hint"))
            Spacer(Modifier.height(12.dp))
            BigButton(t("save"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken,
                enabled = name != s.name || allergies != s.allergies) {
                vm.updateSettings { it.copy(name = name.trim(), allergies = allergies.trim()) }
            }
        }
        SectionTitle(t("emergency_contacts"))
        BigButton(t("add_contact"), Modifier.fillMaxWidth(), icon = Icons.Filled.PersonAdd) {
            editing = ContactEntity(AppRepository.newId(), "", phone = "", sortOrder = contacts.size, updatedAt = 0)
        }
        if (contacts.isEmpty()) EmptyState(Icons.Filled.People, t("no_contacts"))
        contacts.forEach { c ->
            AppCard(Modifier.padding(vertical = 6.dp), onClick = { editing = c }) {
                Text(c.name, style = MaterialTheme.typography.titleLarge)
                Text(listOf(c.relation, c.phone).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
            }
        }
    }
    editing?.let { c0 ->
        var c by remember(c0.id) { mutableStateOf(c0) }
        FormDialog(t("contact"), onDismiss = { editing = null }, saveEnabled = c.name.isNotBlank() && c.phone.isNotBlank(), onSave = { vm.saveContact(c); editing = null }) {
            BigField(t("name"), c.name, { c = c.copy(name = it) })
            Spacer(Modifier.height(8.dp))
            BigField(t("relation"), c.relation, { c = c.copy(relation = it) }, placeholder = t("relation_hint"))
            Spacer(Modifier.height(8.dp))
            BigField(t("phone"), c.phone, { c = c.copy(phone = it) }, keyboard = KeyboardType.Phone, placeholder = "01XXXXXXXXX")
            if (c0.updatedAt != 0L) TextButton(onClick = { deleting = c0; editing = null }, modifier = Modifier.heightIn(min = TouchMin)) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = AppColors.Missed)
                Text(t("delete"), color = AppColors.Missed, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    deleting?.let { c -> ConfirmDialog(Strings.f("delete_q", LocalLang.current, c.name), danger = true, onYes = { vm.deleteContact(c); deleting = null }, onNo = { deleting = null }) }
}
