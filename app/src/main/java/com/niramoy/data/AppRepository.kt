package com.niramoy.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.niramoy.data.Mappers.toMap
import com.niramoy.data.local.*
import com.niramoy.model.DoseStatus
import com.niramoy.model.Slot
import com.niramoy.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class AppUser(val uid: String, val email: String, val displayName: String)

/** Error codes the UI turns into simple Bangla/English messages (see Strings "err_*"). */
class AuthError(val code: String) : Exception(code)

/**
 * Local-first data layer (same design as Ad Deen): Room is the source of truth for the UI and the
 * alarms; when signed in, Firestore users/{uid}/... mirrors every table. Writes go to Room first and
 * are queued by Firestore's offline cache; snapshot listeners pull other devices' edits.
 * Conflicts resolve per row by [Synced.updatedAt] (last writer wins).
 */
@OptIn(FlowPreview::class)
class AppRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "AppRepository"

        @Volatile private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }

        fun newId(): String = UUID.randomUUID().toString()
    }

    private val context: Context = context.applicationContext
    val db = AppDatabase.getInstance(context)
    private val medicineDao = db.medicineDao()
    private val doseDao = db.doseLogDao()
    private val healthDao = db.healthLogDao()
    private val visitDao = db.visitDao()
    private val doctorDao = db.doctorDao()
    private val recordDao = db.recordDao()
    private val contactDao = db.contactDao()
    private val settingsDao = db.settingsDao()

    private val authPrefs: SharedPreferences = context.getSharedPreferences("niramoy_auth_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private var listenersUid: String? = null

    val firebaseReady = MutableStateFlow(false)
    val currentUser = MutableStateFlow<AppUser?>(null)
    /** True while the first download after sign-in is running (restore). */
    val restoring = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)

    /** Emits whenever anything the alarms depend on changed (medicines, settings, visits). */
    private val scheduleInputs = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scheduleChanges: Flow<Unit> = scheduleInputs.debounce(400)

    private fun scheduleChanged() { scheduleInputs.tryEmit(Unit) }

    // ----------------- GENERIC SYNCED COLLECTIONS -----------------

    private inner class Coll<T : Synced>(
        val name: String,
        val toMap: (T) -> Map<String, Any?>,
        val fromDoc: suspend (String, Map<String, Any?>) -> T,
        val getLocal: suspend (String) -> T?,
        val upsertLocal: suspend (T) -> Unit,
        val allLocal: suspend () -> List<T>,
        val affectsSchedule: Boolean = false
    )

    private val medicines = Coll("medicines", { it.toMap() }, { id, m -> Mappers.medicine(id, m) },
        { medicineDao.get(it) }, { medicineDao.upsert(it) }, { medicineDao.getAllIncludingDeleted() }, affectsSchedule = true)
    private val doseLogs = Coll("doseLogs", { it.toMap() }, { id, m -> Mappers.doseLog(id, m) },
        { doseDao.get(it) }, { doseDao.upsert(it) }, { doseDao.getAll() })
    private val healthLogs = Coll("healthLogs", { it.toMap() }, { id, m -> Mappers.healthLog(id, m) },
        { healthDao.get(it) }, { healthDao.upsert(it) }, { healthDao.getAll() })
    private val visits = Coll("visits", { it.toMap() }, { id, m -> Mappers.visit(id, m) },
        { visitDao.get(it) }, { visitDao.upsert(it) }, { visitDao.getAll() }, affectsSchedule = true)
    private val doctors = Coll("doctors", { it.toMap() }, { id, m -> Mappers.doctor(id, m) },
        { doctorDao.get(it) }, { doctorDao.upsert(it) }, { doctorDao.getAll() })
    private val records = Coll("records", { it.toMap() }, { id, m -> Mappers.record(id, m, recordDao.get(id)?.localPath.orEmpty()) },
        { recordDao.get(it) }, { recordDao.upsert(it) }, { recordDao.getAll() })
    private val contacts = Coll("contacts", { it.toMap() }, { id, m -> Mappers.contact(id, m) },
        { contactDao.get(it) }, { contactDao.upsert(it) }, { contactDao.getAll() })

    private val allColls: List<Coll<*>> = listOf(medicines, doseLogs, healthLogs, visits, doctors, records, contacts)

    private suspend fun <T : Synced> save(c: Coll<T>, item: T) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock { c.upsertLocal(item) }
            push(c, item)
            if (c.affectsSchedule) scheduleChanged()
        }
    }

    private fun <T : Synced> push(c: Coll<T>, item: T) {
        val uid = currentUser.value?.uid ?: return
        val fs = firestore ?: return
        // Firestore queues this offline and retries; doc id = row id, so no duplicates.
        fs.collection("users").document(uid).collection(c.name).document(item.id)
            .set(c.toMap(item))
            .addOnFailureListener { reportError("${c.name} write", it) }
    }

    /** Applies cloud docs to Room when newer. Returns true if anything changed. */
    private suspend fun <T : Synced> mergeFromCloud(c: Coll<T>, docs: List<Pair<String, Map<String, Any?>>>): Boolean {
        var changed = false
        writeMutex.withLock {
            for ((id, data) in docs) {
                val cloud = runCatching { c.fromDoc(id, data) }.getOrNull() ?: continue
                val local = c.getLocal(id)
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    c.upsertLocal(cloud); changed = true
                }
            }
        }
        if (changed && c.affectsSchedule) scheduleChanged()
        return changed
    }

    // ----------------- FIREBASE SETUP -----------------

    init {
        initFirebase()
    }

    private fun initFirebase() {
        try {
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
                ?: throw IllegalStateException("google-services.json missing")
            val fs = FirebaseFirestore.getInstance(app)
            runCatching {
                fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
            firestore = fs
            val a = FirebaseAuth.getInstance(app)
            auth = a
            firebaseReady.value = true
            a.currentUser?.let { currentUser.value = AppUser(it.uid, it.email.orEmpty(), it.displayName.orEmpty()) }
            a.addAuthStateListener { onAuthChanged(it.currentUser) }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init error: ${e.message}")
            firebaseReady.value = false
        }
    }

    private fun onAuthChanged(fbUser: com.google.firebase.auth.FirebaseUser?) {
        if (fbUser == null) {
            detachListeners()
            currentUser.value = null
            return
        }
        currentUser.value = AppUser(fbUser.uid, fbUser.email.orEmpty(), fbUser.displayName.orEmpty())
        scope.launch {
            attachListeners(fbUser.uid)
            // Full two-way sync once per account per install (restores data after reinstall);
            // afterwards realtime listeners + the offline queue keep both sides in step.
            if (authPrefs.getString("full_synced_uid", null) != fbUser.uid) {
                restoring.value = true
                fullSync().onSuccess { authPrefs.edit().putString("full_synced_uid", fbUser.uid).apply() }
                restoring.value = false
            }
            com.niramoy.data.UploadWorker.enqueue(context)
        }
    }

    private fun reportError(where: String, e: Exception?) {
        if (e == null) return
        Log.w(TAG, "$where: ${e.message}")
        syncError.value = if (e is FirebaseNetworkException) "offline" else "sync"
    }

    @Synchronized
    private fun attachListeners(uid: String) {
        if (listenersUid == uid) return
        detachListeners()
        val fs = firestore ?: return
        listenersUid = uid
        val user = fs.collection("users").document(uid)
        for (c in allColls) {
            listeners += user.collection(c.name).addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener reportError("${c.name} listener", err)
                if (snap == null) return@addSnapshotListener
                val docs = snap.documentChanges
                    .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                    .map { it.document.id to it.document.data }
                if (docs.isEmpty()) return@addSnapshotListener
                scope.launch { mergeFromCloud(c, docs); syncError.value = null }
            }
        }
        listeners += user.collection("settings").document("main").addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener reportError("settings listener", err)
            if (snap == null || !snap.exists() || snap.metadata.hasPendingWrites()) return@addSnapshotListener
            val data = snap.data ?: return@addSnapshotListener
            scope.launch { mergeSettings(data) }
        }
    }

    @Synchronized
    private fun detachListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
        listenersUid = null
    }

    private suspend fun mergeSettings(data: Map<String, Any?>) {
        val cloud = Mappers.settings(data)
        var changed = false
        writeMutex.withLock {
            val local = settingsDao.get()
            if (local == null || cloud.updatedAt > local.updatedAt) {
                settingsDao.save(cloud); changed = true
            }
        }
        if (changed) {
            UiPrefs(context).language = cloud.language
            scheduleChanged()
        }
    }

    /** Pull everything, merge (newest wins), then push every local row the cloud lacks or has older. */
    suspend fun fullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = currentUser.value?.uid ?: throw IllegalStateException("not signed in")
            val fs = firestore ?: throw IllegalStateException("no firebase")
            val user = fs.collection("users").document(uid)

            user.collection("settings").document("main").get().await().data?.let { mergeSettings(it) }
            settingsDao.get()?.let { local ->
                user.collection("settings").document("main").set(local.toMap()).await()
            }

            for (c in allColls) syncColl(c, user)
            syncError.value = null
        }.onFailure { Log.w(TAG, "full sync failed: ${it.message}"); syncError.value = "sync" }
    }

    private suspend fun <T : Synced> syncColl(c: Coll<T>, user: com.google.firebase.firestore.DocumentReference) {
        val cloudDocs = user.collection(c.name).get().await().documents.map { it.id to (it.data ?: emptyMap()) }
        mergeFromCloud(c, cloudDocs)
        val cloudStamp = cloudDocs.associate { (id, d) -> id to ((d["updatedAt"] as? Number)?.toLong() ?: 0L) }
        val toPush = c.allLocal().filter { local -> (cloudStamp[local.id] ?: -1L) < local.updatedAt }
        val fs = firestore ?: return
        toPush.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { batch.set(user.collection(c.name).document(it.id), c.toMap(it)) }
            batch.commit().await()
        }
    }

    // ----------------- AUTH -----------------

    private fun authCode(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "err_no_account"
        is FirebaseAuthUserCollisionException -> "err_account_exists"
        is FirebaseAuthWeakPasswordException -> "err_weak_password"
        is FirebaseAuthInvalidCredentialsException -> "err_wrong_password"
        is FirebaseNetworkException -> "err_no_internet"
        else -> "err_generic"
    }

    suspend fun signIn(email: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        val a = auth ?: return@withContext Result.failure(AuthError("err_no_firebase"))
        runCatching { a.signInWithEmailAndPassword(email.trim(), pass).await(); Unit }
            .recoverCatching { throw AuthError(authCode(it as Exception)) }
    }

    suspend fun register(email: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        val a = auth ?: return@withContext Result.failure(AuthError("err_no_firebase"))
        runCatching { a.createUserWithEmailAndPassword(email.trim(), pass).await(); Unit }
            .recoverCatching { throw AuthError(authCode(it as Exception)) }
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        val a = auth ?: return@withContext Result.failure(AuthError("err_no_firebase"))
        runCatching { a.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await(); Unit }
            .recoverCatching { throw AuthError(authCode(it as Exception)) }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val a = auth ?: return@withContext Result.failure(AuthError("err_no_firebase"))
        runCatching { a.sendPasswordResetEmail(email.trim()).await(); Unit }
            .recoverCatching { throw AuthError(authCode(it as Exception)) }
    }

    /** Fresh Firebase ID token for Supabase Storage requests. */
    suspend fun idToken(): String? = runCatching { auth?.currentUser?.getIdToken(false)?.await()?.token }.getOrNull()

    /**
     * Signs out and wipes this phone's copy (the cloud keeps everything), so a different account
     * signing in on this phone never sees or merges the previous person's health data.
     */
    suspend fun signOutAndClear() = withContext(Dispatchers.IO) {
        detachListeners()
        runCatching { auth?.signOut() }
        clearLocal()
        authPrefs.edit().clear().apply()
        currentUser.value = null
    }

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUser.value?.uid ?: return@withContext Result.failure(AuthError("err_generic"))
        val fs = firestore ?: return@withContext Result.failure(AuthError("err_no_firebase"))
        try {
            detachListeners()
            val user = fs.collection("users").document(uid)
            for (name in allColls.map { it.name } + "settings") {
                user.collection(name).get().await().documents.chunked(400).forEach { chunk ->
                    val batch = fs.batch(); chunk.forEach { batch.delete(it.reference) }; batch.commit().await()
                }
            }
            auth?.currentUser?.delete()?.await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            return@withContext Result.failure(AuthError("err_recent_login"))
        } catch (e: Exception) {
            return@withContext Result.failure(AuthError(authCode(e)))
        }
        clearLocal()
        authPrefs.edit().clear().apply()
        currentUser.value = null
        Result.success(Unit)
    }

    private suspend fun clearLocal() {
        db.recordDao().getAll().forEach { runCatching { java.io.File(it.localPath).delete() } }
        medicineDao.clear(); doseDao.clear(); healthDao.clear(); visitDao.clear()
        doctorDao.clear(); recordDao.clear(); contactDao.clear(); settingsDao.clear()
        scheduleChanged()
    }

    // ----------------- SETTINGS -----------------

    fun observeSettings(): Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity(language = UiPrefs(context).language) }

    suspend fun getSettings(): SettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.get() ?: SettingsEntity(language = UiPrefs(context).language)
    }

    suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity): SettingsEntity {
        val updated = withContext(Dispatchers.IO) {
            writeMutex.withLock {
                transform(getSettings()).copy(updatedAt = System.currentTimeMillis()).also { settingsDao.save(it) }
            }
        }
        UiPrefs(context).language = updated.language
        val uid = currentUser.value?.uid
        if (uid != null) firestore?.collection("users")?.document(uid)?.collection("settings")?.document("main")
            ?.set(updated.toMap())?.addOnFailureListener { reportError("settings write", it) }
        scheduleChanged()
        return updated
    }

    // ----------------- MEDICINES & DOSES -----------------

    fun observeMedicines(): Flow<List<MedicineEntity>> = medicineDao.observeAll()
    suspend fun getMedicines(): List<MedicineEntity> = withContext(Dispatchers.IO) { medicineDao.getAll() }
    suspend fun getMedicine(id: String): MedicineEntity? = withContext(Dispatchers.IO) { medicineDao.get(id) }

    suspend fun saveMedicine(m: MedicineEntity) = save(medicines, m.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setMedicineActive(id: String, active: Boolean) {
        val m = getMedicine(id) ?: return
        saveMedicine(m.copy(active = active))
    }

    suspend fun deleteMedicine(id: String) {
        val m = getMedicine(id) ?: return
        saveMedicine(m.copy(deleted = true, active = false))
    }

    fun observeDoseLogsSince(from: String): Flow<List<DoseLogEntity>> = doseDao.observeSince(from)
    fun observeDoseLogsFor(medId: String): Flow<List<DoseLogEntity>> = doseDao.observeForMedicine(medId)
    suspend fun doseLogsFor(date: String): List<DoseLogEntity> = withContext(Dispatchers.IO) { doseDao.forDate(date) }

    /**
     * Logs a dose. An explicit user action (taken / skipped) always wins; the automatic "missed"
     * never overwrites a dose that was already marked.
     */
    suspend fun logDose(date: String, medicineId: String, slot: Slot, status: DoseStatus, auto: Boolean = false) {
        val id = DoseLogEntity.idOf(date, medicineId, slot)
        val existing = withContext(Dispatchers.IO) { doseDao.get(id) }
        if (auto && existing != null) return
        val now = System.currentTimeMillis()
        save(doseLogs, DoseLogEntity(id, medicineId, date, slot.id, status.id, now, now))
    }

    /** Undo a mark (e.g. tapped "taken" by mistake): the dose becomes pending again. */
    suspend fun clearDose(date: String, medicineId: String, slot: Slot) = withContext(Dispatchers.IO) {
        val id = DoseLogEntity.idOf(date, medicineId, slot)
        doseDao.delete(id)
        val uid = currentUser.value?.uid ?: return@withContext
        firestore?.collection("users")?.document(uid)?.collection("doseLogs")?.document(id)?.delete()
    }

    // ----------------- HEALTH LOG -----------------

    fun observeHealthLogs(): Flow<List<HealthLogEntity>> = healthDao.observeAll()

    suspend fun saveHealthLog(h: HealthLogEntity) = save(healthLogs, h.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteHealthLog(id: String) {
        val h = withContext(Dispatchers.IO) { healthDao.get(id) } ?: return
        saveHealthLog(h.copy(deleted = true))
    }

    /** Today's mood: one entry per day, updated in place. */
    suspend fun setMoodToday(mood: Int) {
        val today = Dates.today()
        val existing = withContext(Dispatchers.IO) {
            healthDao.getAll().firstOrNull { it.date == today && !it.deleted && it.mood != null && it.sugar == null && it.systolic == null }
        }
        val now = System.currentTimeMillis()
        saveHealthLog(existing?.copy(mood = mood, at = now) ?: HealthLogEntity(newId(), now, today, mood = mood, updatedAt = now))
    }

    // ----------------- RECORDS -----------------

    fun observeVisits(): Flow<List<VisitEntity>> = visitDao.observeAll()
    suspend fun activeVisits(): List<VisitEntity> = withContext(Dispatchers.IO) { visitDao.getActive() }
    suspend fun saveVisit(v: VisitEntity) = save(visits, v.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteVisit(v: VisitEntity) = saveVisit(v.copy(deleted = true))

    fun observeDoctors(): Flow<List<DoctorEntity>> = doctorDao.observeAll()
    suspend fun saveDoctor(d: DoctorEntity) = save(doctors, d.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteDoctor(d: DoctorEntity) = saveDoctor(d.copy(deleted = true))

    fun observeContacts(): Flow<List<ContactEntity>> = contactDao.observeAll()
    suspend fun saveContact(c: ContactEntity) = save(contacts, c.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteContact(c: ContactEntity) = saveContact(c.copy(deleted = true))

    fun observeRecords(): Flow<List<RecordFileEntity>> = recordDao.observeAll()
    suspend fun getRecord(id: String): RecordFileEntity? = withContext(Dispatchers.IO) { recordDao.get(id) }
    suspend fun pendingUploads(): List<RecordFileEntity> = withContext(Dispatchers.IO) { recordDao.pendingUploads() }

    suspend fun saveRecord(r: RecordFileEntity) {
        save(records, r.copy(updatedAt = System.currentTimeMillis()))
        com.niramoy.data.UploadWorker.enqueue(context)
    }

    /** Stores a device-only field (localPath) without bumping updatedAt or pushing. */
    suspend fun setRecordLocalPath(id: String, path: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock { recordDao.get(id)?.let { recordDao.upsert(it.copy(localPath = path)) } }
    }

    suspend fun markUploaded(id: String, remotePath: String) {
        val r = getRecord(id) ?: return
        save(records, r.copy(remotePath = remotePath, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRecord(r: RecordFileEntity) {
        save(records, r.copy(deleted = true, updatedAt = System.currentTimeMillis()))
        runCatching { if (r.localPath.isNotBlank()) java.io.File(r.localPath).delete() }
        if (r.remotePath.isNotBlank()) scope.launch { FileStorage.delete(r.remotePath, idToken()) }
    }
}
