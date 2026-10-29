package com.niramoy.data

import android.content.Context
import androidx.work.*
import com.niramoy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Free Supabase projects pause after 7 days with no activity, which would stop file uploads for
 * every user of the public APK. Each install calls the tiny `public.ping()` SQL function once a day
 * (a few bytes, reads no data), so the shared project stays awake as long as anyone uses the app.
 * A GitHub Action (.github/workflows/supabase-keepalive.yml) does the same every 3 days as a backup.
 */
class KeepAliveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!FileStorage.isConfigured) return@withContext Result.success()
        runCatching {
            val c = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/ping").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000; readTimeout = 15_000
                doOutput = true
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Content-Type", "application/json")
            }
            c.outputStream.use { it.write("{}".toByteArray()) }
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("supabase_keepalive", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
