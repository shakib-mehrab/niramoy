package com.niramoy.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.niramoy.data.UiPrefs
import com.niramoy.data.local.ContactEntity
import com.niramoy.i18n.Strings
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.util.Num

/** Opens the phone's own dialer with the number filled in (no in-app calling). */
fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Num.toAscii(phone).filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Bangladeshi numbers to the international digits wa.me needs: "01711-000000" -> "8801711000000". */
fun waNumber(phone: String): String {
    val d = Num.toAscii(phone).filter { it.isDigit() }
    return when {
        d.startsWith("880") -> d
        d.startsWith("0") && d.length == 11 -> "88$d"
        d.length == 10 && d.startsWith("1") -> "880$d"
        else -> d
    }
}

@SuppressLint("MissingPermission")
private fun lastLocation(context: Context): Location? {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()
}

/** WhatsApp chat to the contact with a prefilled Bangla message (+ map link when allowed). */
fun openWhatsApp(context: Context, contact: ContactEntity, myName: String, lang: String, withLocation: Boolean) {
    val loc = if (withLocation) lastLocation(context) else null
    val msg = buildString {
        append(Strings.f("sos_message", lang, myName.ifBlank { Strings.t("sos_me", lang) }))
        loc?.let { append("\n").append(Strings.t("sos_location", lang)).append(" https://maps.google.com/?q=${it.latitude},${it.longitude}") }
    }
    val uri = Uri.parse("https://wa.me/${waNumber(contact.phone)}?text=" + Uri.encode(msg))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun EmergencyScreen(vm: MainViewModel, nav: NavController) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val s by vm.settings.collectAsState()
    val lang = LocalLang.current
    val prefs = remember { UiPrefs(context) }
    var shareLoc by remember { mutableStateOf(prefs.shareLocation) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    Page(vm, t("emergency"), onBack = { nav.popBackStack() }, speak = t("emergency_help")) {
        Text(t("emergency_help"), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        if (contacts.isEmpty()) {
            EmptyState(Icons.Filled.People, t("no_contacts"))
            BigButton(t("add_contact"), Modifier.fillMaxWidth(), icon = Icons.Filled.PersonAdd) { nav.navigate("profile") }
        }
        contacts.forEach { c ->
            AppCard(Modifier.padding(vertical = 8.dp)) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall)
                Text(listOf(c.relation, c.phone).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                BigButton(t("call"), Modifier.fillMaxWidth(), icon = Icons.Filled.Call, color = AppColors.Taken, height = 72.dp) { dial(context, c.phone) }
                Spacer(Modifier.height(10.dp))
                BigButton(t("whatsapp_help"), Modifier.fillMaxWidth(), icon = Icons.Filled.Chat, color = AppColors.WhatsApp, height = 72.dp) {
                    openWhatsApp(context, c, s.name, lang, shareLoc)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton(t("call_999"), Modifier.fillMaxWidth(), icon = Icons.Filled.LocalPolice, color = AppColors.Emergency, height = 72.dp) { dial(context, "999") }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TouchMin)) {
            Switch(shareLoc, {
                shareLoc = it; prefs.shareLocation = it
                if (it) locPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            })
            Spacer(Modifier.width(10.dp))
            Text(t("send_location"), style = MaterialTheme.typography.bodyLarge)
        }
        if (s.bloodGroup.isNotBlank() || s.allergies.isNotBlank()) {
            SectionTitle(t("my_info"))
            AppCard {
                if (s.bloodGroup.isNotBlank()) Text("${t("blood_group")}: ${s.bloodGroup}", style = MaterialTheme.typography.titleLarge)
                if (s.allergies.isNotBlank()) Text("${t("allergies")}: ${s.allergies}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
