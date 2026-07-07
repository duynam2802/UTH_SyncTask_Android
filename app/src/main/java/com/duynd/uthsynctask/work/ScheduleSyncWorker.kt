package com.duynd.uthsynctask.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duynd.uthsynctask.data.model.SyncOutcome
import com.duynd.uthsynctask.domain.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Đồng bộ nền chạy mỗi giờ: đăng nhập Courses + thnn, lấy deadline, đẩy lên Google Calendar.
 * Chỉ chạy khi có mạng ([NetworkType.CONNECTED]) để tránh tốn pin/lỗi lặp vô ích.
 */
class ScheduleSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val syncRepository = SyncRepository(applicationContext)
            when (syncRepository.sync()) {
                is SyncOutcome.Success -> Result.success()
                // Chưa đăng nhập/chưa kết nối Google/chưa chọn lịch -> không phải lỗi tạm thời,
                // thử lại liên tục không ích gì, để lần chạy định kỳ sau tự kiểm tra lại.
                SyncOutcome.NeedsCalendarSelection,
                SyncOutcome.NeedsGoogleAuthorization -> Result.success()
                is SyncOutcome.UthLoginFailed -> Result.success()
                is SyncOutcome.Error -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "uth_sync_work"

        /** Gọi 1 lần lúc khởi động app (Application.onCreate) để đăng ký lịch chạy nền. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Dùng nếu cần huỷ đồng bộ nền (VD: khi người dùng tắt tính năng ở Cài đặt sau này). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
