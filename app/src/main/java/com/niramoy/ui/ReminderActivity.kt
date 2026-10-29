package com.niramoy.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.niramoy.alarm.ReminderNotifier
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.data.AppRepository
import com.niramoy.data.local.MedicineEntity
import com.niramoy.model.DoseGroup
import com.niramoy.model.DoseSchedule
import com.niramoy.model.DoseStatus
import com.niramoy.receiver.ReminderReceiver
import com.niramoy.ui.components.*
import com.niramoy.ui.theme.AppColors
import com.niramoy.ui.theme.NiramoyTheme
import com.niramoy.util.Dates
import com.niramoy.util.Num
import kotlinx.coroutines.launch

/**
 * Full-screen dose alarm over the lock screen: what to take, one huge "খেয়েছি" button and
 * "পরে বলো" (snooze). Opening the app is one more big button.
 */
class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_GROUP)
        val group = key?.let { DoseGroup.parse(it) }
        if (group == null) { finish(); return }
        val repo = AppRepository.getInstance(this)
        setContent {
            var lang by remember { mutableStateOf("bn") }
            var meds by remember { mutableStateOf<List<MedicineEntity>?>(null) }
            var time by remember { mutableIntStateOf(0) }
            LaunchedEffect(key) {
                val s = repo.getSettings()
                lang = s.language
                time = s.doseTime(group.slot, group.timing)
                val all = DoseSchedule.groupsOn(group.date, repo.getMedicines())[group].orEmpty()
                meds = DoseSchedule.pending(group, all, repo.doseLogsFor(group.date))
                if (meds!!.isEmpty()) finish()
            }
            NiramoyTheme(lang) {
                CompositionLocalProvider(LocalLang provides lang) {
                    Column(
                        Modifier.fillMaxSize().background(AppColors.HeroBrush).systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(110.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Medication, contentDescription = null, tint = Color.White, modifier = Modifier.size(70.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(Num.time(time, lang), style = MaterialTheme.typography.displayMedium, color = Color.White)
                        Text(ReminderNotifier.title(group, lang), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                            meds.orEmpty().forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Icon(Icons.Filled.Medication, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(m.name, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                                        if (m.dose.isNotBlank()) Text(m.dose, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SpeakButtonOnDark(ReminderNotifier.title(group, lang) + ". " + meds.orEmpty().joinToString(", ") { it.name })
                        Spacer(Modifier.height(10.dp))
                        BigButton(t("taken"), Modifier.fillMaxWidth(), icon = Icons.Filled.Check, color = AppColors.Taken, height = 96.dp) {
                            lifecycleScope.launch { ReminderReceiver.markGroup(this@ReminderActivity, key, DoseStatus.TAKEN); finish() }
                        }
                        Spacer(Modifier.height(12.dp))
                        BigButton(t("act_snooze"), Modifier.fillMaxWidth(), icon = Icons.Filled.Snooze,
                            color = Color.White, contentColor = AppColors.Brand, height = 80.dp) {
                            lifecycleScope.launch { ReminderReceiver.snooze(this@ReminderActivity, key); finish() }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            com.niramoy.service.ReminderSoundService.stop(this@ReminderActivity)
                            startActivity(android.content.Intent(this@ReminderActivity, com.niramoy.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        }, modifier = Modifier.heightIn(min = TouchMin)) {
                            Text(t("open_app"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(Num.date(group.date, lang) + " · " + Num.dayName(Dates.dayOfWeek(group.date), lang), color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakButtonOnDark(text: String) {
    Box(Modifier.clip(CircleShape).background(Color.White)) { SpeakButton(text) }
}
