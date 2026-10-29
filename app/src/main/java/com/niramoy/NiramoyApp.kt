package com.niramoy

import android.app.Application
import com.niramoy.alarm.NotificationChannels
import com.niramoy.alarm.ReminderScheduler
import com.niramoy.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NiramoyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        com.niramoy.data.KeepAliveWorker.schedule(this)
        val repo = AppRepository.getInstance(this)
        // Any change to medicines, routine/reminder settings or visits (local edit or synced from the
        // cloud, e.g. restore after reinstall) rebuilds the alarms.
        appScope.launch { repo.scheduleChanges.collect { runCatching { ReminderScheduler.rescheduleAll(this@NiramoyApp) } } }
    }
}
