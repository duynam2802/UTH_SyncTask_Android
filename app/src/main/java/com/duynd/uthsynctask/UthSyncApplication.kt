package com.duynd.uthsynctask

import android.app.Application
import com.duynd.uthsynctask.notification.NotificationChannels
import com.duynd.uthsynctask.work.ReminderCheckWorker
import com.duynd.uthsynctask.work.ScheduleSyncWorker

class UthSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        ScheduleSyncWorker.schedule(this)
        ReminderCheckWorker.schedule(this)
    }
}
