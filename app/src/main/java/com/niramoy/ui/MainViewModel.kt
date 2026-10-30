package com.niramoy.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.data.AppRepository
import com.niramoy.data.AuthError
import com.niramoy.data.FileStorage
import com.niramoy.data.local.*
import com.niramoy.model.*
import com.niramoy.ocr.OcrEngine
import com.niramoy.parse.MedicineDraft
import com.niramoy.parse.PrescriptionParser
import com.niramoy.parse.VoiceParser
import com.niramoy.parse.VoiceResult
import com.niramoy.receiver.ReminderReceiver
import com.niramoy.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** All doses of one reminder group today, in time order. */
data class GroupView(val group: DoseGroup, val time: Long, val items: List<DoseItem>) {
    val pending get() = items.filter { it.status == null }
}

/** In-app update flow (see update/UpdateManager). */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val manifest: com.niramoy.update.UpdateManager.Manifest) : UpdateUiState
    data class Downloading(val manifest: com.niramoy.update.UpdateManager.Manifest, val progress: Float) : UpdateUiState
    data class Ready(val manifest: com.niramoy.update.UpdateManager.Manifest, val file: File) : UpdateUiState
    data class Failed(val manifest: com.niramoy.update.UpdateManager.Manifest?) : UpdateUiState
}

sealed interface OcrState {
    data object Idle : OcrState
    data object Working : OcrState
    data object NoModels : OcrState
    data object NothingFound : OcrState
    data object Failed : OcrState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository.getInstance(application)
    private val ctx get() = getApplication<Application>()

    val settings: StateFlow<SettingsEntity> = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity(language = com.niramoy.data.UiPrefs(application).language))

    val user = repo.currentUser
    val firebaseReady = repo.firebaseReady
    val restoring = repo.restoring
    val syncError = repo.syncError

    /** Clock that advances every 30 s, so "today" rolls over at midnight and statuses refresh. */
    val now: StateFlow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())
    val today: StateFlow<String> = now.map { Dates.today(it) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())

    val medicines = repo.observeMedicines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val doseLogs = repo.observeDoseLogsSince(Dates.addDays(Dates.today(), -120)).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val healthLogs = repo.observeHealthLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val visits = repo.observeVisits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val doctors = repo.observeDoctors().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records = repo.observeRecords().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val contacts = repo.observeContacts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayGroups: StateFlow<List<GroupView>> = combine(today, medicines, doseLogs, settings) { d, meds, logs, s ->
        DoseSchedule.itemsOn(d, meds, logs, s).groupBy { it.group }
            .map { (g, items) -> GroupView(g, DoseSchedule.timeOf(g, s), items) }
            .sortedBy { it.time }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Day shown on Home (date strip): today by default, past days can be reviewed and corrected. */
    val selectedDate = MutableStateFlow(Dates.today())

    val dayGroups: StateFlow<List<GroupView>> = combine(selectedDate, medicines, doseLogs, settings) { d, meds, logs, s ->
        DoseSchedule.itemsOn(d, meds, logs, s).groupBy { it.group }
            .map { (g, items) -> GroupView(g, DoseSchedule.timeOf(g, s), items) }
            .sortedBy { it.time }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The next group with something still to take: today's first pending group, else tomorrow's first. */
    val nextGroup: StateFlow<GroupView?> = combine(todayGroups, medicines, settings, now) { groups, meds, s, t ->
        groups.firstOrNull { it.pending.isNotEmpty() && DoseSchedule.deadline(it.group, s) > t }
            ?: run {
                val tomorrow = Dates.addDays(Dates.today(t), 1)
                DoseSchedule.itemsOn(tomorrow, meds, emptyList(), s).groupBy { it.group }
                    .map { (g, items) -> GroupView(g, DoseSchedule.timeOf(g, s), items) }
                    .minByOrNull { it.time }
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Overall adherence over the last 7 / 30 days. */
    val adherence7: StateFlow<Int?> = combine(doseLogs, today) { logs, d -> DoseSchedule.adherence(logs, Dates.lastDays(7, d)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val adherence30: StateFlow<Int?> = combine(doseLogs, today) { logs, d -> DoseSchedule.adherence(logs, Dates.lastDays(30, d)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // App opened: catch up on missed doses and make sure every alarm exists.
        viewModelScope.launch(Dispatchers.IO) { ReminderScheduler.rescheduleAll(ctx) }
    }

    /** Per-day status of one medicine for the last [days] days (null = nothing logged / not due). */
    fun weekStatuses(medicineId: String, days: Int = 7): List<DoseStatus?> {
        val logs = doseLogs.value.filter { it.medicineId == medicineId }
        return Dates.lastDays(days, today.value).map { d ->
            val dayLogs = logs.filter { it.date == d }
            when {
                dayLogs.isEmpty() -> null
                dayLogs.any { it.status == DoseStatus.MISSED.id } -> DoseStatus.MISSED
                dayLogs.all { it.status == DoseStatus.TAKEN.id } -> DoseStatus.TAKEN
                else -> DoseStatus.SKIPPED
            }
        }
    }

    // ----------------- SETTINGS -----------------

    /** Device-only "Larger text" preference (Settings). */
    val largeText = MutableStateFlow(com.niramoy.data.UiPrefs(application).largeText)
    fun setLargeText(v: Boolean) { com.niramoy.data.UiPrefs(ctx).largeText = v; largeText.value = v }

    fun toggleLanguage() = updateSettings { it.copy(language = if (it.language == "bn") "en" else "bn") }

    fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    // ----------------- AUTH -----------------

    suspend fun signIn(email: String, pass: String) = repo.signIn(email, pass).errorCode()
    suspend fun register(email: String, pass: String) = repo.register(email, pass).errorCode()
    suspend fun signInWithGoogle(idToken: String) = repo.signInWithGoogle(idToken).errorCode()
    suspend fun resetPassword(email: String) = repo.sendPasswordReset(email).errorCode()
    suspend fun deleteAccount() = repo.deleteAccount().errorCode()
    fun signOut(onDone: () -> Unit) = viewModelScope.launch { repo.signOutAndClear(); onDone() }
    fun syncNow() = viewModelScope.launch { repo.fullSync() }

    /** null = success, otherwise an "err_*" string key. */
    private fun Result<Unit>.errorCode(): String? = exceptionOrNull()?.let { (it as? AuthError)?.code ?: "err_generic" }

    // ----------------- MEDICINES -----------------

    fun saveDraft(d: MedicineDraft, editingId: String? = null, startDate: String = today.value) {
        viewModelScope.launch {
            val existing = editingId?.let { repo.getMedicine(it) }
            val now = System.currentTimeMillis()
            repo.saveMedicine(
                MedicineEntity(
                    id = existing?.id ?: AppRepository.newId(),
                    name = d.name.trim(),
                    dose = d.dose.trim(),
                    mealTiming = d.timing.id,
                    slots = Slot.toCsv(d.slots),
                    durationType = d.durationType.id,
                    courseDays = d.courseDays,
                    startDate = startDate,
                    notes = d.notes.trim(),
                    active = existing?.active ?: true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    fun saveDrafts(list: List<MedicineDraft>) = list.filter { it.name.isNotBlank() && it.slots.isNotEmpty() }.forEach { saveDraft(it) }

    fun setMedicineActive(id: String, active: Boolean) = viewModelScope.launch { repo.setMedicineActive(id, active) }
    fun deleteMedicine(id: String) = viewModelScope.launch { repo.deleteMedicine(id) }

    fun draftOf(m: MedicineEntity) = MedicineDraft(m.name, m.dose, m.slotSet, m.timing, m.duration, if (m.courseDays > 0) m.courseDays else 7, m.notes)

    // ----------------- DOSES -----------------

    fun markDose(item: DoseItem, status: DoseStatus) = viewModelScope.launch {
        repo.logDose(item.group.date, item.medicine.id, item.group.slot, status)
        // Everything in that group answered? Stop its alarm sequence and notification.
        val left = DoseSchedule.pending(item.group, DoseSchedule.groupsOn(item.group.date, repo.getMedicines())[item.group].orEmpty(),
            repo.doseLogsFor(item.group.date))
        if (left.isEmpty()) ReminderReceiver.finishGroup(ctx, item.group.key)
    }

    fun undoDose(item: DoseItem) = viewModelScope.launch {
        repo.clearDose(item.group.date, item.medicine.id, item.group.slot)
        ReminderScheduler.rescheduleAll(ctx)
    }

    /**
     * Change a dose to any state at any time (a mis-tap on "Taken" must be fixable):
     * null = not marked yet. Today's alarms are rebuilt so a re-opened dose reminds again.
     */
    fun setDoseStatus(item: DoseItem, status: DoseStatus?) {
        if (status == null) undoDose(item) else markDose(item, status)
    }

    /** Same, from a medicine's history list. */
    fun setLogStatus(log: com.niramoy.data.local.DoseLogEntity, status: DoseStatus) = viewModelScope.launch {
        val slot = Slot.of(log.slot) ?: return@launch
        repo.logDose(log.date, log.medicineId, slot, status)
    }

    fun markGroupTaken(key: String) = viewModelScope.launch { ReminderReceiver.markGroup(ctx, key, DoseStatus.TAKEN) }

    // ----------------- HEALTH -----------------

    fun setMood(mood: Int) = viewModelScope.launch { repo.setMoodToday(mood) }
    fun saveHealth(h: HealthLogEntity) = viewModelScope.launch { repo.saveHealthLog(h) }
    fun deleteHealth(id: String) = viewModelScope.launch { repo.deleteHealthLog(id) }

    /** Last spoken sentence, parsed and waiting for confirmation. */
    val voiceResult = MutableStateFlow<VoiceResult?>(null)
    fun onSpeech(text: String) { voiceResult.value = VoiceParser.parse(text) }

    // ----------------- RECORDS -----------------

    fun saveVisit(v: VisitEntity) = viewModelScope.launch { repo.saveVisit(v) }
    fun deleteVisit(v: VisitEntity) = viewModelScope.launch { repo.deleteVisit(v) }
    fun saveDoctor(d: DoctorEntity) = viewModelScope.launch { repo.saveDoctor(d) }
    fun deleteDoctor(d: DoctorEntity) = viewModelScope.launch { repo.deleteDoctor(d) }
    fun saveContact(c: ContactEntity) = viewModelScope.launch { repo.saveContact(c) }
    fun deleteContact(c: ContactEntity) = viewModelScope.launch { repo.deleteContact(c) }
    fun deleteRecord(r: RecordFileEntity) = viewModelScope.launch { repo.deleteRecord(r) }

    /** Imports (compresses) a picked file and saves it as a record; it uploads in the background when online. */
    fun addRecord(uri: Uri, kind: RecordKind, date: String, title: String, doctor: String, reportType: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = AppRepository.newId()
            val imported = withContext(Dispatchers.IO) { FileStorage.importFile(ctx, uri, id) }
            if (imported == null) { onDone(false); return@launch }
            repo.saveRecord(RecordFileEntity(id, kind.id, date, title.trim(), doctor.trim(), reportType.trim(),
                imported.first.absolutePath, "", imported.second, System.currentTimeMillis()))
            onDone(true)
        }
    }

    fun updateRecordInfo(r: RecordFileEntity) = viewModelScope.launch { repo.saveRecord(r) }

    /** Local file for a record, downloading it once on a new phone. */
    suspend fun fileFor(r: RecordFileEntity): File? = withContext(Dispatchers.IO) {
        File(r.localPath).takeIf { r.localPath.isNotBlank() && it.exists() }?.let { return@withContext it }
        if (r.remotePath.isBlank()) return@withContext null
        val token = repo.idToken() ?: return@withContext null
        val dest = File(FileStorage.recordsDir(ctx), r.remotePath.substringAfterLast('/'))
        if (FileStorage.download(r.remotePath, dest, token)) { repo.setRecordLocalPath(r.id, dest.absolutePath); dest } else null
    }

    // ----------------- UPDATES -----------------

    val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    /** Silent on launch (only shows when a newer version exists); [manual] shows every result. */
    fun checkForUpdates(manual: Boolean) {
        val um = com.niramoy.update.UpdateManager
        if (!manual && !um.shouldAutoCheck(ctx)) return
        if (manual) updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val r = um.check(ctx)
            val m = r.getOrNull()
            updateState.value = when {
                m != null && (manual || !um.isSnoozed(ctx, m.versionCode) || m.isMandatoryFor(um.currentVersionCode)) -> UpdateUiState.Available(m)
                !manual -> UpdateUiState.Idle
                r.isFailure -> UpdateUiState.Failed(null)
                else -> UpdateUiState.UpToDate
            }
        }
    }

    fun downloadUpdate() {
        val m = when (val s = updateState.value) {
            is UpdateUiState.Available -> s.manifest
            is UpdateUiState.Failed -> s.manifest
            else -> null
        } ?: return
        updateState.value = UpdateUiState.Downloading(m, 0f)
        viewModelScope.launch {
            com.niramoy.update.UpdateManager.download(ctx, m) { p -> updateState.value = UpdateUiState.Downloading(m, p) }
                .onSuccess { f -> updateState.value = UpdateUiState.Ready(m, f); if (com.niramoy.update.UpdateManager.canInstall(ctx)) com.niramoy.update.UpdateManager.install(ctx, f) }
                .onFailure { updateState.value = UpdateUiState.Failed(m) }
        }
    }

    fun installUpdate() {
        (updateState.value as? UpdateUiState.Ready)?.let { com.niramoy.update.UpdateManager.install(ctx, it.file) }
    }

    fun dismissUpdate() {
        when (val s = updateState.value) {
            is UpdateUiState.Available -> com.niramoy.update.UpdateManager.snooze(ctx, s.manifest.versionCode)
            is UpdateUiState.Failed -> s.manifest?.let { com.niramoy.update.UpdateManager.snooze(ctx, it.versionCode) }
            is UpdateUiState.Ready -> com.niramoy.update.UpdateManager.snooze(ctx, s.manifest.versionCode)
            else -> Unit
        }
        updateState.value = UpdateUiState.Idle
    }

    // ----------------- OCR -----------------

    val ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    /** Rows waiting on the review screen (bulk add or OCR result). */
    val drafts = MutableStateFlow<List<MedicineDraft>>(emptyList())
    /** Prescription photo to keep as a record after the review is saved. */
    var ocrImage: Uri? = null

    fun runOcr(uri: Uri, onResult: (Boolean) -> Unit) {
        ocrImage = uri
        ocrState.value = OcrState.Working
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { FileStorage.decodeScaled(ctx, uri, 2200) }
            val text = bmp?.let { OcrEngine.recognize(ctx, it) }
            when {
                bmp == null -> { ocrState.value = OcrState.Failed; onResult(false) }
                text == null -> { ocrState.value = if (OcrEngine.modelsReady(ctx)) OcrState.Failed else OcrState.NoModels; onResult(false) }
                else -> {
                    val found = PrescriptionParser.parse(text, settings.value.language)
                    if (found.isEmpty()) {
                        ocrState.value = OcrState.NothingFound
                        drafts.value = listOf(MedicineDraft())
                    } else {
                        ocrState.value = OcrState.Idle
                        drafts.value = found
                    }
                    onResult(true)
                }
            }
        }
    }
}
