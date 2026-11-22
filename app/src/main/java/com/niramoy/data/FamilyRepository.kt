package com.niramoy.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.niramoy.alarm.StockNotifier
import com.niramoy.data.local.DoseLogEntity
import com.niramoy.data.local.HealthLogEntity
import com.niramoy.data.local.MedicineEntity
import com.niramoy.data.local.RecordFileEntity
import com.niramoy.data.local.SettingsEntity
import com.niramoy.data.local.VisitEntity
import com.niramoy.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class Family(
    val id: String,
    val name: String,
    val ownerUid: String,
    val code: String,
    val seatLimit: Int,
    val memberCount: Int
)

data class FamilyMember(val uid: String, val name: String, val sharing: Boolean, val joinedAt: Long)

/** Everything a family member may see about one person (read-only, live). */
data class MemberData(
    val settings: SettingsEntity = SettingsEntity(),
    val medicines: List<MedicineEntity> = emptyList(),
    val doseLogs: List<DoseLogEntity> = emptyList(),
    val healthLogs: List<HealthLogEntity> = emptyList(),
    val records: List<RecordFileEntity> = emptyList(),
    val visits: List<VisitEntity> = emptyList()
)

/**
 * Family space (up to [DEFAULT_SEATS] people, free): one person creates it and shares a 6-letter code;
 * others join with it — without making an account (a silent anonymous identity is used).
 *
 * Firestore layout (see firestore.rules, which enforce all of this server-side):
 *   families/{fid}                 name, ownerUid, code, seatLimit, memberCount
 *   families/{fid}/members/{uid}   name, sharing, joinedAt, code
 *   inviteCodes/{CODE}             familyId
 *   users/{uid}/meta/family        familyId          (which family this account is in)
 *   users/{uid}/viewers/{viewer}   familyId          (lets `viewer` read uid's health data)
 *   users/{uid}/nudges/{id}        fromUid, fromName, medicineId, medicineName, type, at
 *
 * Each phone keeps the viewer links in step with the member list, so access appears when someone
 * joins and disappears (server-side) when they leave or stop sharing.
 */
class FamilyRepository private constructor(context: Context) {

    companion object {
        const val DEFAULT_SEATS = 5
        private const val TAG = "FamilyRepository"
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I
        private const val PREFS = "niramoy_family"

        @Volatile private var INSTANCE: FamilyRepository? = null
        fun getInstance(context: Context): FamilyRepository =
            INSTANCE ?: synchronized(this) { INSTANCE ?: FamilyRepository(context.applicationContext).also { INSTANCE = it } }

        fun newCode(): String = (1..6).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
    }

    private val context = context.applicationContext
    private val repo = AppRepository.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val fs: FirebaseFirestore? get() = repo.cloud

    val family = MutableStateFlow<Family?>(null)
    val members = MutableStateFlow<List<FamilyMember>>(emptyList())
    /** True until the first read of the family pointer after sign-in. */
    val loading = MutableStateFlow(true)

    private val listeners = mutableListOf<ListenerRegistration>()
    private var pointerListener: ListenerRegistration? = null
    private var nudgeListener: ListenerRegistration? = null
    private var boundUid: String? = null

    val myUid: String? get() = repo.currentUser.value?.uid

    init {
        scope.launch {
            repo.currentUser.map { it?.uid }.distinctUntilChanged().collect { uid -> bind(uid) }
        }
    }

    // ----------------------------------------------------------------- live state

    @Synchronized
    private fun bind(uid: String?) {
        if (uid == boundUid) return
        boundUid = uid
        pointerListener?.remove(); pointerListener = null
        nudgeListener?.remove(); nudgeListener = null
        detachFamily()
        family.value = null; members.value = emptyList()
        val db = fs
        if (uid == null || db == null) { loading.value = false; return }
        loading.value = true
        pointerListener = db.collection("users").document(uid).collection("meta").document("family")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "pointer: ${err.message}"); loading.value = false; return@addSnapshotListener }
                val fid = snap?.getString("familyId")
                attachFamily(fid)
                loading.value = false
            }
        // Messages from family members ("please buy", "I'll bring it") while the app is running.
        nudgeListener = db.collection("users").document(uid).collection("nudges")
            .whereGreaterThan("at", lastNudgeSeen())
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }?.forEach { c ->
                    showNudge(c.document)
                }
            }
    }

    private var familyId: String? = null

    @Synchronized
    private fun attachFamily(fid: String?) {
        if (fid == familyId) return
        detachFamily()
        familyId = fid
        val db = fs ?: return
        if (fid == null) { family.value = null; members.value = emptyList(); return }
        val ref = db.collection("families").document(fid)
        listeners += ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) {
                // Deleted, or we were removed: forget the pointer.
                if (err?.let { (it as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED } != false) {
                    family.value = null; members.value = emptyList()
                    myUid?.let { u -> db.collection("users").document(u).collection("meta").document("family").delete() }
                }
                return@addSnapshotListener
            }
            family.value = Family(
                id = fid, name = snap.getString("name").orEmpty(), ownerUid = snap.getString("ownerUid").orEmpty(),
                code = snap.getString("code").orEmpty(), seatLimit = (snap.getLong("seatLimit") ?: DEFAULT_SEATS.toLong()).toInt(),
                memberCount = (snap.getLong("memberCount") ?: 1L).toInt()
            )
        }
        listeners += ref.collection("members").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.map {
                FamilyMember(it.id, it.getString("name").orEmpty(), it.getBoolean("sharing") == true, it.getLong("joinedAt") ?: 0L)
            }?.sortedBy { it.joinedAt } ?: return@addSnapshotListener
            members.value = list
            scope.launch { syncViewerLinks(fid, list) }
        }
    }

    @Synchronized
    private fun detachFamily() {
        listeners.forEach { it.remove() }
        listeners.clear()
        familyId = null
    }

    /**
     * Viewer links follow the member list: I may read everyone who shares, and everyone may read me
     * if I share. Missing links are created; links to people who left are removed.
     */
    private suspend fun syncViewerLinks(fid: String, list: List<FamilyMember>) {
        val db = fs ?: return
        val me = myUid ?: return
        val mine = list.firstOrNull { it.uid == me } ?: return
        val others = list.filter { it.uid != me }
        runCatching {
            val users = db.collection("users")
            for (o in others) {
                if (o.sharing) users.document(o.uid).collection("viewers").document(me).set(mapOf("familyId" to fid)).await()
                if (mine.sharing) users.document(me).collection("viewers").document(o.uid).set(mapOf("familyId" to fid)).await()
            }
            // Remove links on my data for people no longer in the family, or everyone if I stopped sharing.
            val keep = if (mine.sharing) others.map { it.uid }.toSet() else emptySet()
            users.document(me).collection("viewers").get().await().documents
                .filter { it.id !in keep }
                .forEach { it.reference.delete() }
        }.onFailure { Log.w(TAG, "viewer links: ${it.message}") }
    }

    // ----------------------------------------------------------------- actions

    /** Error codes are string keys ("family_*", "err_*") the UI translates. */
    class FamilyError(val code: String) : Exception(code)

    suspend fun create(familyName: String, myName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = fs ?: throw FamilyError("err_no_firebase")
            val uid = repo.ensureSignedIn().getOrElse { throw FamilyError("err_no_internet") }
            if (family.value != null) throw FamilyError("family_already")
            var code = newCode()
            repeat(4) { if (db.collection("inviteCodes").document(code).get().await().exists()) code = newCode() }
            val ref = db.collection("families").document()
            val now = System.currentTimeMillis()
            db.batch().apply {
                set(ref, mapOf("name" to familyName.trim(), "ownerUid" to uid, "code" to code,
                    "seatLimit" to DEFAULT_SEATS, "memberCount" to 1, "createdAt" to now))
                set(ref.collection("members").document(uid), mapOf("name" to myName.trim(), "sharing" to true, "joinedAt" to now, "code" to code))
                set(db.collection("inviteCodes").document(code), mapOf("familyId" to ref.id))
                set(db.collection("users").document(uid).collection("meta").document("family"), mapOf("familyId" to ref.id))
            }.commit().await()
            Unit
        }.recoverCatching { throw mapError(it) }
    }

    suspend fun join(rawCode: String, myName: String, share: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = fs ?: throw FamilyError("err_no_firebase")
            val code = rawCode.trim().uppercase().replace(" ", "")
            if (code.length != 6) throw FamilyError("family_code_invalid")
            val uid = repo.ensureSignedIn().getOrElse { throw FamilyError("err_no_internet") }
            if (family.value != null) throw FamilyError("family_already")
            val invite = db.collection("inviteCodes").document(code).get().await()
            val fid = invite.getString("familyId") ?: throw FamilyError("family_code_invalid")
            val ref = db.collection("families").document(fid)
            db.batch().apply {
                set(ref.collection("members").document(uid),
                    mapOf("name" to myName.trim(), "sharing" to share, "joinedAt" to System.currentTimeMillis(), "code" to code))
                update(ref, "memberCount", FieldValue.increment(1))
                set(db.collection("users").document(uid).collection("meta").document("family"), mapOf("familyId" to fid))
            }.commit().await()
            Unit
        }.recoverCatching {
            // The rules reject a join when the family is full (or the code changed).
            if ((it as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw FamilyError("family_full")
            throw mapError(it)
        }
    }

    suspend fun setSharing(on: Boolean): Result<Unit> = action { db, fid, uid ->
        db.collection("families").document(fid).collection("members").document(uid).update("sharing", on).await()
    }

    suspend fun rename(name: String): Result<Unit> = action { db, fid, uid ->
        db.collection("families").document(fid).collection("members").document(uid).update("name", name.trim()).await()
    }

    /** Leave (anyone) — the owner can only leave once they are the last member. */
    suspend fun leave(): Result<Unit> = action { db, fid, uid ->
        val f = family.value
        if (f != null && f.ownerUid == uid && members.value.size > 1) throw FamilyError("family_owner_leave")
        if (f != null && f.ownerUid == uid) { deleteFamily(db, f); return@action }
        removeLinks(db, uid)
        db.batch().apply {
            delete(db.collection("families").document(fid).collection("members").document(uid))
            update(db.collection("families").document(fid), "memberCount", FieldValue.increment(-1))
            delete(db.collection("users").document(uid).collection("meta").document("family"))
        }.commit().await()
    }

    /** Owner removes someone (their access ends immediately: the rules check membership on every read). */
    suspend fun remove(memberUid: String): Result<Unit> = action { db, fid, _ ->
        db.batch().apply {
            delete(db.collection("families").document(fid).collection("members").document(memberUid))
            update(db.collection("families").document(fid), "memberCount", FieldValue.increment(-1))
        }.commit().await()
    }

    private suspend fun deleteFamily(db: FirebaseFirestore, f: Family) {
        val ref = db.collection("families").document(f.id)
        myUid?.let { removeLinks(db, it) }
        val batch = db.batch()
        ref.collection("members").get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(db.collection("inviteCodes").document(f.code))
        batch.delete(ref)
        myUid?.let { batch.delete(db.collection("users").document(it).collection("meta").document("family")) }
        batch.commit().await()
    }

    private suspend fun removeLinks(db: FirebaseFirestore, uid: String) {
        runCatching {
            db.collection("users").document(uid).collection("viewers").get().await().documents.forEach { it.reference.delete() }
            members.value.filter { it.uid != uid }.forEach {
                db.collection("users").document(it.uid).collection("viewers").document(uid).delete()
            }
        }
    }

    /** "Please buy X" or "I'll bring X" to the person who takes the medicine. */
    suspend fun nudge(toUid: String, m: MedicineEntity, type: String): Result<Unit> = action { db, _, uid ->
        val me = members.value.firstOrNull { it.uid == uid }?.name.orEmpty()
        db.collection("users").document(toUid).collection("nudges").document().set(mapOf(
            "fromUid" to uid, "fromName" to me, "medicineId" to m.id, "medicineName" to m.name, "type" to type,
            "at" to System.currentTimeMillis()
        )).await()
    }

    /** A family member bought medicine for someone: add to their stock (the only field family may change). */
    suspend fun addStockFor(toUid: String, m: MedicineEntity, amount: Double): Result<Unit> = action { db, _, _ ->
        val newStock = (m.stock ?: 0.0) + amount
        db.collection("users").document(toUid).collection("medicines").document(m.id).set(mapOf(
            "stock" to newStock,
            "lowStockNotified" to (newStock <= m.lowStockAt),
            "updatedAt" to System.currentTimeMillis()
        ), SetOptions.merge()).await()
    }

    private suspend fun action(block: suspend (FirebaseFirestore, String, String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = fs ?: throw FamilyError("err_no_firebase")
            val fid = family.value?.id ?: throw FamilyError("family_none")
            val uid = myUid ?: throw FamilyError("err_generic")
            block(db, fid, uid)
        }.recoverCatching { throw mapError(it) }
    }

    private fun mapError(t: Throwable): Throwable = when {
        t is FamilyError -> t
        t is com.google.firebase.FirebaseNetworkException -> FamilyError("err_no_internet")
        (t as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE -> FamilyError("err_no_internet")
        else -> { Log.w(TAG, "family op failed", t); FamilyError("err_generic") }
    }

    // ----------------------------------------------------------------- one person's data (read-only)

    private fun <T> live(q: Query, map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.mapNotNull { runCatching { map(it) }.getOrNull() }) }
        awaitClose { reg.remove() }
    }

    fun observeMember(uid: String): Flow<MemberData> {
        val db = fs ?: return flowOf(MemberData())
        val u = db.collection("users").document(uid)
        val since = Dates.addDays(Dates.today(), -14)
        val settings = callbackFlow {
            val reg = u.collection("settings").document("main").addSnapshotListener { s, _ ->
                trySend(s?.data?.let { Mappers.settings(it) } ?: SettingsEntity())
            }
            awaitClose { reg.remove() }
        }
        val meds = live(u.collection("medicines")) { Mappers.medicine(it.id, it.data ?: return@live null) }
            .map { l -> l.filter { !it.deleted } }
        val logs = live(u.collection("doseLogs").whereGreaterThanOrEqualTo("date", since)) { Mappers.doseLog(it.id, it.data ?: return@live null) }
        val health = live(u.collection("healthLogs").orderBy("at", Query.Direction.DESCENDING).limit(40)) {
            Mappers.healthLog(it.id, it.data ?: return@live null)
        }.map { l -> l.filter { !it.deleted } }
        val recs = live(u.collection("records")) { Mappers.record(it.id, it.data ?: return@live null, "") }
            .map { l -> l.filter { !it.deleted }.sortedByDescending { it.date } }
        val visits = live(u.collection("visits")) { Mappers.visit(it.id, it.data ?: return@live null) }
            .map { l -> l.filter { !it.deleted }.sortedByDescending { it.date } }
        return combine(settings, meds, logs, health, recs) { s, m, l, h, r -> MemberData(s, m, l, h, r) }
            .combine(visits) { d, v -> d.copy(visits = v) }
    }

    // ----------------------------------------------------------------- background checks (FamilyWorker)

    private fun lastNudgeSeen(): Long = prefs.getLong("last_nudge", System.currentTimeMillis())

    private fun showNudge(d: DocumentSnapshot) {
        val at = d.getLong("at") ?: return
        if (at <= prefs.getLong("last_nudge", 0L)) return
        prefs.edit().putLong("last_nudge", at).apply()
        StockNotifier.notifyNudge(context, d.id, d.getString("fromName").orEmpty(), d.getString("medicineName").orEmpty(), d.getString("type").orEmpty())
    }

    /**
     * Runs every ~15 min in the background (and once at app start): new nudges for me, and low stock for
     * anyone I follow — each low-stock alert once per run-down (re-armed when their stock goes up again).
     */
    suspend fun backgroundCheck() = withContext(Dispatchers.IO) {
        val db = fs ?: return@withContext
        val me = myUid ?: return@withContext
        runCatching {
            if (!prefs.contains("last_nudge")) prefs.edit().putLong("last_nudge", System.currentTimeMillis()).apply()
            db.collection("users").document(me).collection("nudges").whereGreaterThan("at", prefs.getLong("last_nudge", 0L))
                .get().await().documents.sortedBy { it.getLong("at") ?: 0L }.forEach { showNudge(it) }

            val fid = db.collection("users").document(me).collection("meta").document("family").get().await().getString("familyId")
                ?: return@runCatching
            val people = db.collection("families").document(fid).collection("members").get().await().documents
                .filter { it.id != me && it.getBoolean("sharing") == true }
            val alerted = prefs.getStringSet("low_alerted", emptySet())!!.toMutableSet()
            for (p in people) {
                val name = p.getString("name").orEmpty()
                val meds = db.collection("users").document(p.id).collection("medicines").get().await().documents
                    .mapNotNull { Mappers.medicine(it.id, it.data ?: return@mapNotNull null) }.filter { !it.deleted && it.active }
                for (m in meds) {
                    val key = "${p.id}:${m.id}"
                    if (m.isLowStock) {
                        if (key !in alerted) { StockNotifier.notifyFamily(context, name, m); alerted += key }
                    } else alerted -= key
                }
            }
            prefs.edit().putStringSet("low_alerted", alerted).apply()
        }.onFailure { Log.w(TAG, "background check: ${it.message}") }
    }
}
