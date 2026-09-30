package com.duynd.uthsynctask.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duynd.uthsynctask.data.local.EventStore
import com.duynd.uthsynctask.data.local.NotificationSettingsStore
import com.duynd.uthsynctask.data.model.EventSource
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
        Log.d("ReminderWorker", "Bắt đầu kiểm tra deadline chạy ngầm...")
        try {
            val settingsStore = NotificationSettingsStore(applicationContext)
            val settings = settingsStore.getCurrent()
            if (!settings.enabled) {
                Log.d("ReminderWorker", "Thông báo đã bị tắt trong cài đặt.")
                return@withContext Result.success()
            }

            val eventStore = EventStore(applicationContext)
            val notifier = ReminderNotifier(applicationContext)
            val now = System.currentTimeMillis()

            val events = eventStore.getAll()
            Log.d("ReminderWorker", "Tìm thấy ${events.size} deadline trong kho lưu trữ.")

            for (event in events) {
                if (event.isTamNgung && !event.notifiedTamNgung) {
                    Log.d("ReminderWorker", "Gửi thông báo tạm ngưng học cho: ${event.title}")
                    notifier.notifyClassPaused(event, settings)
                    eventStore.updateTamNgungNotifiedFlag(event.id, true)
                }

                if (event.source == EventSource.PORTAL && event.roomChanged && !event.isCompleted) {
                    val timeUntilStart = event.startTimeMillis - now
                    if (timeUntilStart <= 24 * 60 * 60 * 1000L && timeUntilStart > 0L && !event.notifiedRoom24h) {
                        Log.d("ReminderWorker", "Gửi thông báo đổi phòng (24h) cho: ${event.title}")
                        notifier.notifyRoomChange(event, 24, settings)
                        eventStore.updateRoomNotificationFlags(event.id, notified24h = true, notified1h = event.notifiedRoom1h)
                    }
                    if (timeUntilStart <= 1 * 60 * 60 * 1000L && timeUntilStart > 0L && !event.notifiedRoom1h) {
                        Log.d("ReminderWorker", "Gửi thông báo đổi phòng (1h) cho: ${event.title}")
                        notifier.notifyRoomChange(event, 1, settings)
                        eventStore.updateRoomNotificationFlags(event.id, notified24h = event.notifiedRoom24h, notified1h = true)
                    }
                }

                val tier = ReminderPolicy.evaluateTier(event, now)
                if (ReminderPolicy.shouldNotifyNow(event, tier, now)) {
                    Log.d("ReminderWorker", "Gửi thông báo cho: ${event.title} (Tier: $tier)")
                    notifier.notify(event, tier, settings)
                    eventStore.updateLastNotifiedAt(event.id, now)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Lỗi khi chạy worker: ${e.message}")
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
