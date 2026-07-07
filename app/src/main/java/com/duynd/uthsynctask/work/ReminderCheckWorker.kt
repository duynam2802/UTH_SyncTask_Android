package com.duynd.uthsynctask.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duynd.uthsynctask.data.local.EventStore
import com.duynd.uthsynctask.data.local.NotificationSettingsStore
import com.duynd.uthsynctask.domain.ReminderPolicy
import com.duynd.uthsynctask.notification.ReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Kiểm tra deadline mỗi 15 phút - CHỈ đọc dữ liệu cục bộ (không gọi mạng) nên rất nhẹ,
 * không ảnh hưởng pin/hiệu năng máy. Việc lấy dữ liệu mới vẫn do [ScheduleSyncWorker]
 * (chạy mỗi giờ) đảm nhiệm; worker này chỉ quyết định CÓ CẦN nhắc nhở dựa trên dữ liệu
 * đã có sẵn hay không.
 */
class ReminderCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settingsStore = NotificationSettingsStore(applicationContext)
            val settings = settingsStore.getCurrent()
            if (!settings.enabled) return@withContext Result.success()

            val eventStore = EventStore(applicationContext)
            val notifier = ReminderNotifier(applicationContext)
            val now = System.currentTimeMillis()

            for (event in eventStore.getAll()) {
                val tier = ReminderPolicy.evaluateTier(event, now)
                if (ReminderPolicy.shouldNotifyNow(event, tier, now)) {
                    notifier.notify(event, tier, settings)
                    eventStore.updateLastNotifiedAt(event.id, now)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "uth_reminder_check_work"

        /** Gọi 1 lần lúc khởi động app để đăng ký lịch kiểm tra định kỳ. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
