package com.niramoy.data

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Family alerts without a paid push server: every ~15 minutes (Android's minimum; it may be later in
 * deep sleep) each phone checks for new nudges and for low stock of the people it follows.
 * While the app is open, Firestore listeners deliver nudges immediately.
 */
class FamilyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        FamilyRepository.getInstance(applicationContext).backgroundCheck()
        return Result.success()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork("family_check",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FamilyWorker>(15, TimeUnit.MINUTES).setConstraints(online).build())
            wm.enqueueUniqueWork("family_check_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FamilyWorker>().setConstraints(online).build())
        }
    }
}
