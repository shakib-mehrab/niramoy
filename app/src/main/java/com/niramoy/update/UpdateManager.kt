package com.niramoy.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.niramoy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-updates from GitHub Releases (same design as Ad Deen). The release workflow attaches
 * `version.json` to every release; the app reads
 * https://github.com/<repo>/releases/latest/download/version.json and, when a newer versionCode is
 * published, downloads the APK and hands it to Android's installer. The user always confirms.
 *
 * Before installing: https + allow-listed GitHub hosts only, SHA-256 must match the manifest, and the
 * APK must be this package, newer, and signed with the SAME key as the installed app.
 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val PREFS = "niramoy_update_prefs"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val ALLOWED_HOSTS = setOf(
        "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com", "raw.githubusercontent.com"
    )

    data class Manifest(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val notes: String,
        val minSupportedVersionCode: Int
    ) {
        /** Installed version too old to keep using: the dialog can't be dismissed. */
        fun isMandatoryFor(current: Int) = current < minSupportedVersionCode
    }

    val isEnabled: Boolean get() = BuildConfig.UPDATE_MANIFEST_URL.startsWith("https://")
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldAutoCheck(context: Context): Boolean =
        isEnabled && System.currentTimeMillis() - prefs(context).getLong("last_check", 0L) > CHECK_INTERVAL_MS

    /** "পরে" hides that version for a day; it comes back so updates are never missed for long. */
    fun isSnoozed(context: Context, versionCode: Int): Boolean {
        val p = prefs(context)
        return p.getInt("snoozed_version", -1) == versionCode && System.currentTimeMillis() - p.getLong("snoozed_at", 0L) < 24 * 60 * 60 * 1000L
    }

    fun snooze(context: Context, versionCode: Int) =
        prefs(context).edit().putInt("snoozed_version", versionCode).putLong("snoozed_at", System.currentTimeMillis()).apply()

    internal fun isAllowedUrl(url: String): Boolean = runCatching {
        val u = Uri.parse(url)
        u.scheme == "https" && (u.host ?: "") in ALLOWED_HOSTS
    }.getOrDefault(false)

    internal fun parseManifest(json: String): Manifest? = runCatching {
        val o = JSONObject(json)
        val m = Manifest(
            versionCode = o.getInt("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            sha256 = o.getString("sha256").lowercase().trim(),
            notes = o.optString("notes", ""),
            minSupportedVersionCode = o.optInt("minSupportedVersionCode", 0)
        )
        require(isAllowedUrl(m.apkUrl)) { "APK URL not allowed" }
        require(Regex("^[0-9a-f]{64}$").matches(m.sha256)) { "Invalid sha256" }
        m
    }.onFailure { Log.w(TAG, "Bad manifest: ${it.message}") }.getOrNull()

    /** Returns the manifest only if it is newer than the installed version. */
    suspend fun check(context: Context): Result<Manifest?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isEnabled) return@runCatching null
            val conn = (URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            val body = try {
                when (conn.responseCode) {
                    200 -> conn.inputStream.bufferedReader().use { it.readText() }
                    404 -> null // no release published yet
                    else -> throw IllegalStateException("HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
            prefs(context).edit().putLong("last_check", System.currentTimeMillis()).apply()
            val m = body?.let { parseManifest(it) ?: throw IllegalStateException("bad manifest") }
            m?.takeIf { it.versionCode > currentVersionCode }
        }
    }

    private fun apkFile(context: Context, m: Manifest): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name != "niramoy-${m.versionCode}.apk") it.delete() }
        return File(dir, "niramoy-${m.versionCode}.apk")
    }

    /** Downloads with DownloadManager (shows a system progress notification) and verifies the file. */
    suspend fun download(context: Context, m: Manifest, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = apkFile(context, m)
            if (file.exists() && verify(context, file, m).isSuccess) return@runCatching file
            file.delete()
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(
                DownloadManager.Request(Uri.parse(m.apkUrl))
                    .setTitle("নিরাময় ${m.versionName}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(file))
            )
            while (true) {
                delay(400)
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) throw IllegalStateException("cancelled")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            verify(context, file, m).getOrThrow()
                            return@runCatching file
                        }
                        DownloadManager.STATUS_FAILED -> throw IllegalStateException("download failed")
                    }
                } ?: throw IllegalStateException("download unavailable")
            }
            @Suppress("UNREACHABLE_CODE") file
        }
    }

    fun verify(context: Context, file: File, m: Manifest): Result<Unit> = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != m.sha256) { file.delete(); throw SecurityException("checksum mismatch") }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw SecurityException("not an APK")
        if (archive.packageName != context.packageName) throw SecurityException("different app")
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (archiveCode <= currentVersionCode) throw SecurityException("not newer")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (signatures(archive) != signatures(installed)) throw SecurityException("different signing key")
    }

    private fun signatures(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: emptyArray()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** Android 8+: "Install unknown apps" must be allowed for NIRAMOY once. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
