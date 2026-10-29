package com.niramoy.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.niramoy.ui.MainViewModel
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import kotlinx.coroutines.launch

/** Purple header with the app name, like the reference screens. */
@Composable
private fun BrandHeader(subtitle: String) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.HeroBrush).statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(t("app_name"), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
    }
}

@Composable
fun AuthScreen(vm: MainViewModel, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSkip by remember { mutableStateOf(false) }

    fun run(block: suspend () -> String?) {
        busy = true; message = null
        scope.launch { message = block()?.let { com.niramoy.i18n.Strings.t(it, lang) }; busy = false }
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background).verticalScroll(rememberScrollState())) {
        BrandHeader(t("auth_subtitle"))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            LanguageToggle(lang) { vm.toggleLanguage() }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(t(if (register) "auth_register_title" else "auth_signin_title"), style = MaterialTheme.typography.headlineSmall)
            Text(t("auth_why"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            Spacer(Modifier.height(14.dp))
            BigField(t("email"), email, { email = it }, keyboard = KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text(t("password"), style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchMin)
            )
            Spacer(Modifier.height(16.dp))
            BigButton(
                t(if (register) "auth_register" else "auth_signin"), Modifier.fillMaxWidth(), icon = Icons.Filled.Login,
                enabled = !busy && email.contains('@') && pass.length >= 6
            ) { run { if (register) vm.register(email, pass) else vm.signIn(email, pass) } }
            Spacer(Modifier.height(10.dp))
            BigOutlineButton(t("auth_google"), Modifier.fillMaxWidth(), icon = Icons.Filled.AccountCircle) {
                val activity = context as? Activity ?: return@BigOutlineButton
                val webIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (webIdRes == 0) { message = com.niramoy.i18n.Strings.t("err_google_setup", lang); return@BigOutlineButton }
                run {
                    try {
                        val option = GetSignInWithGoogleOption.Builder(context.getString(webIdRes)).build()
                        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                        val cred = result.credential
                        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            vm.signInWithGoogle(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                        } else "err_generic"
                    } catch (e: Exception) {
                        if (e is androidx.credentials.exceptions.GetCredentialCancellationException) null else "err_generic"
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = AppColors.Missed, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { register = !register; message = null }, modifier = Modifier.heightIn(min = TouchMin)) {
                Text(t(if (register) "auth_have_account" else "auth_new_account"), style = MaterialTheme.typography.bodyLarge)
            }
            if (!register) TextButton(
                onClick = { if (email.contains('@')) run { vm.resetPassword(email) ?: "auth_reset_sent" } else message = com.niramoy.i18n.Strings.t("auth_enter_email", lang) },
                modifier = Modifier.heightIn(min = TouchMin)
            ) { Text(t("auth_forgot"), style = MaterialTheme.typography.bodyLarge) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            BigOutlineButton(t("auth_skip"), Modifier.fillMaxWidth(), icon = Icons.Filled.PhoneAndroid, color = AppColors.TextMuted) { confirmSkip = true }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmSkip) ConfirmDialog(t("auth_skip_q"), t("auth_skip_msg"), onYes = { confirmSkip = false; onSkip() }, onNo = { confirmSkip = false })
}

@Composable
fun RestoringScreen() {
    Column(Modifier.fillMaxSize().background(AppColors.Background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
        Spacer(Modifier.height(20.dp))
        Text(t("restoring"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

/**
 * First-run questions. Every step can be skipped (sensible defaults); everything stays editable in Settings.
 * Steps: language, name, wake, breakfast, lunch, dinner, sleep, blood group.
 */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val lang = LocalLang.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(s.name) }
    var wake by rememberSaveable { mutableIntStateOf(s.wakeTime) }
    var breakfast by rememberSaveable { mutableIntStateOf(s.breakfastTime) }
    var lunch by rememberSaveable { mutableIntStateOf(s.lunchTime) }
    var dinner by rememberSaveable { mutableIntStateOf(s.dinnerTime) }
    var sleep by rememberSaveable { mutableIntStateOf(s.sleepTime) }
    var blood by rememberSaveable { mutableStateOf(s.bloodGroup) }
    val last = 7

    fun finish() = vm.updateSettings {
        it.copy(name = name.trim(), wakeTime = wake, breakfastTime = breakfast, lunchTime = lunch, dinnerTime = dinner,
            sleepTime = sleep, bloodGroup = blood, onboarded = true)
    }
    fun next() { if (step >= last) finish() else step++ }

    val (titleKey, icon) = when (step) {
        0 -> "ob_language" to Icons.Filled.Translate
        1 -> "ob_name" to Icons.Filled.Person
        2 -> "ob_wake" to Icons.Filled.WbTwilight
        3 -> "ob_breakfast" to Icons.Filled.FreeBreakfast
        4 -> "ob_lunch" to Icons.Filled.LunchDining
        5 -> "ob_dinner" to Icons.Filled.DinnerDining
        6 -> "ob_sleep" to Icons.Filled.Bedtime
        else -> "ob_blood" to Icons.Filled.Bloodtype
    }
    val title = t(titleKey)

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        TopBar(tf("ob_step", com.niramoy.util.Num.loc(step + 1, lang), com.niramoy.util.Num.loc(last + 1, lang)), lang,
            onToggleLang = { vm.toggleLanguage() }, onBack = if (step > 0) ({ step-- }) else null, speakText = title)
        LinearProgressIndicator(progress = { (step + 1f) / (last + 1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(8.dp).clip(CircleShape))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(AppColors.BrandSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (step in 2..6) Text(t("ob_time_hint"), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            when (step) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("বাংলা", Modifier.fillMaxWidth(), color = if (lang == "bn") AppColors.Brand else AppColors.BrandLight.copy(alpha = 0.5f)) {
                        vm.updateSettings { it.copy(language = "bn") }; step++
                    }
                    BigButton("English", Modifier.fillMaxWidth(), color = if (lang == "en") AppColors.Brand else AppColors.BrandLight.copy(alpha = 0.5f)) {
                        vm.updateSettings { it.copy(language = "en") }; step++
                    }
                }
                1 -> BigField(t("name"), name, { name = it })
                2 -> TimeChooser(wake, { wake = it })
                3 -> TimeChooser(breakfast, { breakfast = it })
                4 -> TimeChooser(lunch, { lunch = it })
                5 -> TimeChooser(dinner, { dinner = it })
                6 -> TimeChooser(sleep, { sleep = it })
                else -> BloodGroupPicker(blood) { blood = it }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigOutlineButton(t("skip"), Modifier.weight(1f), color = AppColors.TextMuted) { next() }
            BigButton(t(if (step == last) "finish" else "next"), Modifier.weight(1f), icon = Icons.Filled.ArrowForward) { next() }
        }
        TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).heightIn(min = TouchMin)) {
            Text(t("ob_skip_all"), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BloodGroupPicker(value: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BLOOD_GROUPS.forEach { g -> ChoiceChip(g, value == g, Modifier.widthIn(min = 88.dp)) { onPick(if (value == g) "" else g) } }
        ChoiceChip(t("dont_know"), value.isBlank()) { onPick("") }
    }
}
