package com.niramoy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.niramoy.ui.UpdateUiState
import com.niramoy.ui.theme.AppColors
import com.niramoy.update.UpdateManager
import com.niramoy.util.Num

/**
 * "New version" dialog in simple words: what's new, one big button that downloads, verifies and
 * opens Android's installer, plus the one-time "allow install" step. Mandatory updates can't be dismissed.
 */
@Composable
fun UpdateDialog(state: UpdateUiState, onDownload: () -> Unit, onInstall: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var canInstall by remember { mutableStateOf(UpdateManager.canInstall(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = UpdateManager.canInstall(context) }
    val manifest = when (state) {
        is UpdateUiState.Available -> state.manifest
        is UpdateUiState.Downloading -> state.manifest
        is UpdateUiState.Ready -> state.manifest
        is UpdateUiState.Failed -> state.manifest
        else -> null
    }
    val mandatory = manifest?.isMandatoryFor(UpdateManager.currentVersionCode) == true
    val dismiss = { if (!mandatory && state !is UpdateUiState.Downloading) onLater() }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !mandatory)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AppColors.Surface)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(AppColors.BrandSoft), contentAlignment = Alignment.Center) {
                Icon(
                    when (state) { UpdateUiState.UpToDate -> Icons.Filled.CheckCircle; is UpdateUiState.Failed -> Icons.Filled.ErrorOutline; else -> Icons.Filled.SystemUpdate },
                    contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                t(when (state) { UpdateUiState.Checking -> "upd_checking"; UpdateUiState.UpToDate -> "upd_latest"; is UpdateUiState.Failed -> "upd_failed"; else -> "upd_title" }),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center
            )
            manifest?.let {
                Text("${t("upd_version")} ${Num.loc(it.versionName, lang)}", style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                // Long notes scroll inside a capped box so the buttons always stay reachable.
                Text(it.notes.ifBlank { t("upd_default_notes") }, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
            }
            Spacer(Modifier.height(18.dp))
            when (state) {
                UpdateUiState.Checking -> CircularProgressIndicator(Modifier.size(56.dp))
                UpdateUiState.UpToDate -> BigButton(t("ok"), Modifier.fillMaxWidth(), onClick = onLater)
                is UpdateUiState.Available, is UpdateUiState.Failed -> {
                    if (manifest != null) BigButton(t("upd_now"), Modifier.fillMaxWidth(), icon = Icons.Filled.Download, color = AppColors.Taken, height = 72.dp, onClick = onDownload)
                    else BigButton(t("ok"), Modifier.fillMaxWidth(), onClick = onLater)
                }
                is UpdateUiState.Downloading -> {
                    Text(t("upd_downloading"), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)))
                    Text("${Num.loc((state.progress * 100).toInt(), lang)}%", style = MaterialTheme.typography.titleMedium)
                }
                is UpdateUiState.Ready -> {
                    if (!canInstall) {
                        Text(t("upd_allow_msg"), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        BigButton(t("upd_allow"), Modifier.fillMaxWidth(), icon = Icons.Filled.Security) { UpdateManager.openInstallPermissionSettings(context) }
                    } else {
                        BigButton(t("upd_install"), Modifier.fillMaxWidth(), icon = Icons.Filled.InstallMobile, color = AppColors.Taken, height = 72.dp, onClick = onInstall)
                    }
                }
                UpdateUiState.Idle -> Unit
            }
            if (!mandatory && state !is UpdateUiState.Downloading && state !is UpdateUiState.UpToDate && state !is UpdateUiState.Checking) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = TouchMin)) { Text(t("upd_later"), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
