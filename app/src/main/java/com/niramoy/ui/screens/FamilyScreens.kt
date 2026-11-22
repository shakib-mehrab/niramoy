package com.niramoy.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.niramoy.data.FamilyMember
import com.niramoy.data.MemberData
import com.niramoy.data.local.MedicineEntity
import com.niramoy.i18n.Strings
import com.niramoy.model.DoseSchedule
import com.niramoy.model.DoseStatus
import com.niramoy.model.Level
import com.niramoy.model.Ranges
import com.niramoy.model.SugarTag
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Dates
import com.niramoy.util.Num
import kotlinx.coroutines.launch

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/** Google ID token via Credential Manager (null when cancelled or not configured). */
suspend fun googleIdToken(context: Context): String? {
    val activity = context as? Activity ?: return null
    val res = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (res == 0) return null
    return runCatching {
        val option = GetSignInWithGoogleOption.Builder(context.getString(res)).build()
        val cred = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            GoogleIdTokenCredential.createFrom(cred.data).idToken else null
    }.getOrNull()
}

// ----------------------------------------------------------------- Family tab

@Composable
fun FamilyScreen(vm: MainViewModel, nav: NavController) {
    val family by vm.family.collectAsState()
    val members by vm.familyMembers.collectAsState()
    val loading by vm.familyLoading.collectAsState()
    val s by vm.settings.collectAsState()
    val user by vm.user.collectAsState()
    val firebaseReady by vm.firebaseReady.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var createOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FamilyMember?>(null) }
    val me = vm.myUid
    val deletes = family?.ownerUid == me && members.size <= 1

    Page(vm, t("tab_family")) {
        when {
            !firebaseReady -> EmptyState(Icons.Filled.CloudOff, t("cloud_not_setup"))
            loading && user != null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            family == null -> {
                AppCard {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(AppColors.BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(t("family_title"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("family_intro"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                    Spacer(Modifier.height(16.dp))
                    BigButton(t("family_create"), Modifier.fillMaxWidth(), icon = Icons.Filled.GroupAdd) {
                        if (user == null) toast(context, Strings.t("family_need_account", lang)) else createOpen = true
                    }
                    Spacer(Modifier.height(10.dp))
                    BigOutlineButton(t("family_join"), Modifier.fillMaxWidth(), icon = Icons.Filled.Key) { nav.navigate("family_join") }
                }
                Spacer(Modifier.height(12.dp))
                FamilyHowItWorks()
            }
            else -> {
                val f = family!!
                val isOwner = f.ownerUid == me
                val mine = members.firstOrNull { it.uid == me }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconSquare(Icons.Filled.Groups)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleLarge)
                            Text(tf("seats", Num.loc(members.size, lang), Num.loc(f.seatLimit, lang)), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(t("family_code"), style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
                    Text(f.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace,
                        color = AppColors.Brand, letterSpacing = 4.sp)
                    Text(t("family_code_hint"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigOutlineButton(t("copy"), Modifier.weight(1f), icon = Icons.Filled.ContentCopy) {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", f.code))
                            toast(context, Strings.t("copied", lang))
                        }
                        BigButton(t("share"), Modifier.weight(1f), icon = Icons.Filled.Share) {
                            val text = Strings.f("family_invite_text", lang, s.name.ifBlank { "" }, f.code)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        }
                    }
                    if (members.size >= f.seatLimit) {
                        Spacer(Modifier.height(8.dp))
                        Text(t("seats_more_soon"), style = MaterialTheme.typography.bodySmall, color = AppColors.Skipped)
                    }
                }
                Spacer(Modifier.height(12.dp))
                mine?.let { m ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("share_my_data"), style = MaterialTheme.typography.titleSmall)
                                Text(t("share_my_data_hint"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                            }
                            Switch(m.sharing, { on -> scope.launch { vm.setFamilySharing(on)?.let { toast(context, Strings.t(it, lang)) } } })
                        }
                    }
                }

                SectionTitle(t("people"))
                members.forEach { m ->
                    MemberRow(vm, m, isMe = m.uid == me, isOwner = m.uid == f.ownerUid, canRemove = isOwner && m.uid != me,
                        onOpen = { nav.navigate("member/${m.uid}") }, onRemove = { removing = m })
                    Spacer(Modifier.height(10.dp))
                }

                if (vm.isAnonymous) {
                    SectionTitle(t("keep_access"))
                    AppCard {
                        Text(t("link_google_hint"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                        Spacer(Modifier.height(10.dp))
                        BigOutlineButton(t("link_google"), Modifier.fillMaxWidth(), icon = Icons.Filled.AccountCircle) {
                            scope.launch {
                                val token = googleIdToken(context) ?: return@launch
                                toast(context, Strings.t(vm.linkGoogle(token) ?: "linked", lang))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                BigOutlineButton(t(if (isOwner && members.size <= 1) "family_delete" else "leave_family"), Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Logout, color = AppColors.Missed) { confirmLeave = true }
            }
        }
    }

    if (createOpen) CreateFamilyDialog(defaultMyName = s.name, onDismiss = { createOpen = false }) { fam, my, done ->
        scope.launch {
            val err = vm.createFamily(fam, my)
            done(err)
            if (err == null) createOpen = false
        }
    }
    if (confirmLeave) ConfirmDialog(t(if (deletes) "family_delete_q" else "leave_family_q"),
        t(if (deletes) "family_delete_msg" else "leave_family_msg"), danger = true, onYes = {
        confirmLeave = false
        scope.launch { vm.leaveFamily()?.let { toast(context, Strings.t(it, lang)) } }
    }, onNo = { confirmLeave = false })
    removing?.let { m ->
        ConfirmDialog(Strings.f("remove_member_q", lang, m.name), t("remove_member_msg"), danger = true, onYes = {
            removing = null
            scope.launch { vm.removeFamilyMember(m.uid)?.let { toast(context, Strings.t(it, lang)) } }
        }, onNo = { removing = null })
    }
}

@Composable
private fun FamilyHowItWorks() {
    AppCard {
        Text(t("family_how"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        listOf(
            Icons.Filled.Key to "family_how_1",
            Icons.Filled.Visibility to "family_how_2",
            Icons.Filled.Inventory2 to "family_how_3",
            Icons.Filled.NotificationsActive to "family_how_4"
        ).forEach { (icon, key) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                IconSquare(icon, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(t(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One person: name, badges and — for people who share — today's progress and low-stock count. */
@Composable
private fun MemberRow(vm: MainViewModel, m: FamilyMember, isMe: Boolean, isOwner: Boolean, canRemove: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val lang = LocalLang.current
    val canOpen = !isMe && m.sharing
    AppCard(onClick = if (canOpen) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name.ifBlank { t("someone") }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (isMe) { Spacer(Modifier.width(6.dp)); Tag(t("you")) }
                    if (isOwner) { Spacer(Modifier.width(6.dp)); Tag(t("owner")) }
                }
                Text(t(if (m.sharing) "shares" else "not_sharing"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            if (canRemove) IconButton(onClick = onRemove) { Icon(Icons.Filled.PersonRemove, contentDescription = t("remove"), tint = AppColors.Missed) }
            if (canOpen) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppColors.TextMuted)
        }
        if (canOpen) {
            val data by remember(m.uid) { vm.observeMember(m.uid) }.collectAsState(initial = null)
            data?.let { d ->
                val today = Dates.today()
                val items = DoseSchedule.itemsOn(today, d.medicines, d.doseLogs, d.settings)
                val taken = items.count { it.status == DoseStatus.TAKEN }
                val low = d.medicines.count { it.active && it.isLowStock }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.isNotEmpty()) Pill(Icons.Filled.CheckCircle, tf("today_taken", Num.loc(taken, lang), Num.loc(items.size, lang)),
                        if (taken == items.size) AppColors.Taken else AppColors.Brand)
                    if (low > 0) Pill(Icons.Filled.Warning, tf("low_stock_n", Num.loc(low, lang)), AppColors.Missed)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = AppColors.Brand,
        modifier = Modifier.clip(CircleShape).background(AppColors.BrandSoft).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreateFamilyDialog(defaultMyName: String, onDismiss: () -> Unit, onCreate: (String, String, (String?) -> Unit) -> Unit) {
    val lang = LocalLang.current
    var my by remember { mutableStateOf(defaultMyName) }
    var fam by remember { mutableStateOf(if (defaultMyName.isNotBlank()) Strings.f("family_name_default", lang, defaultMyName.split(" ").first()) else "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormDialog(t("family_create"), onDismiss = onDismiss, saveEnabled = !busy && my.isNotBlank() && fam.isNotBlank(), onSave = {
        busy = true; error = null
        onCreate(fam, my) { err -> busy = false; error = err }
    }) {
        BigField(t("your_name"), my, { my = it }, placeholder = t("your_name_hint"))
        Spacer(Modifier.height(10.dp))
        BigField(t("family_name"), fam, { fam = it })
        // Error stays visible in the dialog (and Save works again) instead of a toast that disappears.
        error?.let { Text(t(it), color = AppColors.Missed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

// ----------------------------------------------------------------- Join

/** Join with a code. [followerOnly] = this phone only follows family (no own medicines / onboarding). */
@Composable
fun JoinFamilyForm(vm: MainViewModel, followerOnly: Boolean, onJoined: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    val s by vm.settings.collectAsState()
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(s.name) }
    var share by remember { mutableStateOf(!followerOnly) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AppCard {
        Text(t("family_join"), style = MaterialTheme.typography.headlineSmall)
        Text(t(if (followerOnly) "join_no_account" else "join_hint"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(6); error = null },
            label = { Text(t("family_code")) },
            placeholder = { Text("ABC 123") },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = AppColors.Surface, focusedContainerColor = AppColors.Surface),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        BigField(t("your_name"), name, { name = it }, placeholder = t("your_name_hint"))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("share_my_data"), style = MaterialTheme.typography.titleSmall)
                Text(t("join_share_hint"), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            Switch(share, { share = it })
        }
        error?.let { Text(t(it), color = AppColors.Missed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        Spacer(Modifier.height(14.dp))
        BigButton(t("join"), Modifier.fillMaxWidth(), icon = Icons.Filled.GroupAdd, enabled = !busy && code.length == 6 && name.isNotBlank()) {
            busy = true; error = null
            scope.launch {
                val err = vm.joinFamily(code, name, share, followerOnly)
                busy = false
                if (err == null) { toast(context, Strings.t("joined", lang)); onJoined() } else error = err
            }
        }
        if (onCancel != null) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = TouchMin)) {
            Text(t("cancel"), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun JoinFamilyScreen(vm: MainViewModel, nav: NavController) {
    Page(vm, t("family_join"), onBack = { nav.popBackStack() }) {
        JoinFamilyForm(vm, followerOnly = false, onJoined = { nav.popBackStack() }, onCancel = null)
    }
}

// ----------------------------------------------------------------- One person (read-only)

@Composable
fun MemberScreen(vm: MainViewModel, nav: NavController, uid: String) {
    val members by vm.familyMembers.collectAsState()
    val member = members.firstOrNull { it.uid == uid }
    val data by remember(uid) { vm.observeMember(uid) }.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var addFor by remember { mutableStateOf<MedicineEntity?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val name = member?.name?.ifBlank { null } ?: Strings.t("someone", lang)

    Page(vm, name, onBack = { nav.popBackStack() }) {
        val d = data
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Page
        }
        val today = Dates.today()
        val items = DoseSchedule.itemsOn(today, d.medicines, d.doseLogs, d.settings)
        val taken = items.count { it.status == DoseStatus.TAKEN }
        val pct = DoseSchedule.adherence(d.doseLogs, Dates.lastDays(7, today))

        // Summary
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(if (items.isEmpty()) t("no_doses_day") else tf("today_taken", Num.loc(taken, lang), Num.loc(items.size, lang)),
                        style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                }
                pct?.let { RingProgress(it / 100f, Modifier.size(64.dp)) { Text("${Num.loc(it, lang)}%", style = MaterialTheme.typography.labelLarge) } }
            }
        }

        // Today
        SectionTitle(t("member_today"))
        if (items.isEmpty()) Text(t("no_doses_day"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        else AppCard(padding = 10.dp) {
            items.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider(color = AppColors.Border.copy(alpha = 0.5f))
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.medicine.name, style = MaterialTheme.typography.titleSmall)
                        Text(Num.time(d.settings.doseTime(it.group.slot, it.group.timing), lang) + " · " + t("when_${it.group.slot.id}_${it.group.timing.id}"),
                            style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                    }
                    StatusBadge(it.status)
                }
            }
        }

        // Medicines + stock + nudges
        SectionTitle(t("member_meds"))
        val meds = d.medicines.filter { it.active }.sortedByDescending { it.isLowStock }
        if (meds.isEmpty()) Text(t("no_meds"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        meds.forEach { m ->
            AppCard(Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillIcon(40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(m.dose, scheduleText(m)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                        if (m.tracksStock) { Spacer(Modifier.height(4.dp)); StockLine(m) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.isLowStock || !m.tracksStock) {
                        BigOutlineButton(t("remind_buy"), Modifier.weight(1f)) {
                            scope.launch { toast(context, Strings.t(vm.nudge(uid, m, "buy") ?: "nudge_sent", lang)) }
                        }
                        BigOutlineButton(t("will_buy"), Modifier.weight(1f)) {
                            scope.launch { toast(context, Strings.t(vm.nudge(uid, m, "buying") ?: "nudge_sent", lang)) }
                        }
                    }
                }
                if (m.tracksStock) {
                    Spacer(Modifier.height(8.dp))
                    BigButton(t("stock_add"), Modifier.fillMaxWidth(), icon = Icons.Filled.AddShoppingCart, height = 48.dp) { addFor = m }
                }
            }
        }

        // Health
        SectionTitle(t("member_health"))
        MemberHealth(d)

        // Prescriptions & reports
        SectionTitle(t("member_records"))
        if (d.records.isEmpty()) Text(t("no_files"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        d.records.take(12).forEach { r ->
            AppCard(Modifier.padding(bottom = 8.dp), onClick = if (r.shareUrl.isNotBlank()) ({
                if (r.mime == "application/pdf") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.shareUrl))) }
                else viewing = r.shareUrl
            }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.SurfaceAlt), contentAlignment = Alignment.Center) {
                        if (r.shareUrl.isNotBlank() && r.mime != "application/pdf")
                            AsyncImage(r.shareUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(if (r.mime == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description, contentDescription = null, tint = AppColors.Brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(if (r.kind == "report") "reports" else "prescription"), style = MaterialTheme.typography.titleSmall)
                        Text(listOf(Num.date(r.date, lang), r.doctorName, r.title).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
                        if (r.shareUrl.isBlank()) Text(t("record_not_shared_yet"), style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                    }
                }
            }
        }

        d.visits.firstOrNull { (it.nextDate ?: "") >= Dates.today() }?.let { v ->
            SectionTitle(t("next_visit"))
            AppCard {
                Text(Num.date(v.nextDate!!, lang), style = MaterialTheme.typography.titleLarge, color = AppColors.Brand)
                if (v.doctorName.isNotBlank()) Text(v.doctorName, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    addFor?.let { m ->
        AddStockDialog(m.name, onAdd = { n ->
            addFor = null
            scope.launch { toast(context, Strings.t(vm.addStockFor(uid, m, n) ?: "stock_added", lang)) }
        }, onDismiss = { addFor = null })
    }
    viewing?.let { url ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewing = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp)) {
                AsyncImage(url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth())
                BigButton(t("close"), Modifier.fillMaxWidth().navigationBarsPadding(), icon = Icons.Filled.Close) { viewing = null }
            }
        }
    }
}

@Composable
private fun MemberHealth(d: MemberData) {
    val lang = LocalLang.current
    val sugar = d.healthLogs.firstOrNull { it.sugar != null }
    val bp = d.healthLogs.firstOrNull { it.systolic != null && it.diastolic != null }
    val pulse = d.healthLogs.firstOrNull { it.pulse != null }
    val mood = d.healthLogs.firstOrNull { it.mood != null }
    if (sugar == null && bp == null && pulse == null && mood == null) {
        Text(t("no_health"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted); return
    }
    AppCard {
        mood?.mood?.let { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(MOODS.first { it.first == m }.second, contentDescription = null, tint = moodColor(m), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("mood_$m"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Num.date(mood.date, lang, withYear = false), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
        }
        sugar?.let { h -> HealthLine(Icons.Filled.Bloodtype, t("sugar"), "${Num.loc(h.sugar!!, lang)} mg/dL", Ranges.sugar(h.sugar, SugarTag.of(h.sugarTag)), h.date) }
        bp?.let { h -> HealthLine(Icons.Filled.MonitorHeart, t("pressure"), "${Num.loc(h.systolic!!, lang)}/${Num.loc(h.diastolic!!, lang)}", Ranges.pressure(h.systolic, h.diastolic), h.date) }
        pulse?.let { h -> HealthLine(Icons.Filled.Favorite, t("pulse"), "${Num.loc(h.pulse!!, lang)} bpm", Ranges.pulse(h.pulse), h.date) }
    }
}

@Composable
private fun HealthLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, level: Level, date: String) {
    val lang = LocalLang.current
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconSquare(icon, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("$label · $value", style = MaterialTheme.typography.titleSmall)
            Text(Num.date(date, lang, withYear = false), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
        LevelBadge(level)
    }
}
