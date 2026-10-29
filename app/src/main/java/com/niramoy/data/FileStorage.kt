package com.niramoy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.*
import com.niramoy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Prescription / report files in Supabase Storage (free tier, no card). Firebase Storage needs the
 * paid Blaze plan for new projects since Oct 2024, so it is not used.
 * Requests carry the user's Firebase ID token (Supabase "third-party auth: Firebase"); bucket
 * policies only allow paths that start with that token's uid (see supabase/storage-policies.sql).
 * Plain HttpURLConnection keeps the APK small (no extra HTTP/Supabase SDK).
 */
object FileStorage {
    private const val TAG = "FileStorage"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && !BuildConfig.SUPABASE_URL.contains("YOUR-PROJECT")

    private fun objectUrl(path: String, authenticated: Boolean = false): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val mid = if (authenticated) "object/authenticated" else "object"
        return "$base/storage/v1/$mid/${BuildConfig.SUPABASE_BUCKET}/$path"
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }

    suspend fun upload(file: File, path: String, mime: String, token: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        runCatching {
            val c = open(objectUrl(path), "POST", token).apply {
                doOutput = true
                setRequestProperty("Content-Type", mime)
                setRequestProperty("x-upsert", "true")
                setFixedLengthStreamingMode(file.length())
            }
            file.inputStream().use { input -> c.outputStream.use { input.copyTo(it) } }
            val code = c.responseCode
            if (code !in 200..299) Log.w(TAG, "upload $code: ${c.errorStream?.bufferedReader()?.readText()}")
            c.disconnect()
            code in 200..299
        }.getOrElse { Log.w(TAG, "upload failed: ${it.message}"); false }
    }

    suspend fun download(path: String, dest: File, token: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        runCatching {
            val c = open(objectUrl(path, authenticated = true), "GET", token)
            val ok = c.responseCode in 200..299
            if (ok) {
                dest.parentFile?.mkdirs()
                c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            c.disconnect()
            ok
        }.getOrElse { Log.w(TAG, "download failed: ${it.message}"); false }
    }

    suspend fun delete(path: String, token: String?) = withContext(Dispatchers.IO) {
        if (!isConfigured || token == null) return@withContext
        runCatching { open(objectUrl(path), "DELETE", token).apply { responseCode; disconnect() } }
    }

    fun recordsDir(context: Context) = File(context.filesDir, "records").apply { mkdirs() }

    /**
     * Copies a picked image into app storage, scaled to max 1600 px and JPEG q=75 (~150-350 KB),
     * honouring EXIF rotation. PDFs are copied as-is.
     */
    fun importFile(context: Context, uri: Uri, id: String): Pair<File, String>? {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        return runCatching {
            if (mime == "application/pdf") {
                val dest = File(recordsDir(context), "$id.pdf")
                context.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { i.copyTo(it) } }
                dest to mime
            } else {
                val bmp = decodeScaled(context, uri, 1600) ?: return null
                val dest = File(recordsDir(context), "$id.jpg")
                dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }
                bmp.recycle()
                dest to "image/jpeg"
            }
        }.getOrNull()
    }

    fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > maxSide) {
            val f = maxSide.toFloat() / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        }
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true) else bmp
    }.getOrNull()
}

/** Uploads every record that has a local file but no remote copy yet. Runs when online; retries with backoff. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = AppRepository.getInstance(applicationContext)
        val uid = repo.currentUser.value?.uid ?: return Result.success()
        if (!FileStorage.isConfigured) return Result.success()
        val token = repo.idToken() ?: return Result.retry()
        var failed = false
        for (r in repo.pendingUploads()) {
            val f = File(r.localPath)
            if (!f.exists()) continue
            val ext = if (r.mime == "application/pdf") "pdf" else "jpg"
            val path = "$uid/${r.id}.$ext"
            if (FileStorage.upload(f, path, r.mime, token)) repo.markUploaded(r.id, path) else failed = true
        }
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("record_uploads", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
