package com.duynd.uthsynctask.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationChannels {
    const val CHANNEL_NORMAL = "uth_reminder_normal"
    const val CHANNEL_URGENT = "uth_reminder_urgent"

    /** Gọi 1 lần lúc khởi động app - tạo channel không có tác dụng gì nếu gọi lại nhiều lần. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        val normalChannel = NotificationChannel(
            CHANNEL_NORMAL,
            "Nhắc nhở deadline",
            NotificationManager.IMPORTANCE_HIGH // Đổi sang HIGH để có thể rung/popup
        ).apply {
            description = "Nhắc deadline sắp tới (từ 12 giờ trước), lặp lại mỗi giờ nếu chưa xác nhận."
            enableVibration(true)
        }

        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            "Deadline khẩn cấp",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Deadline sắp hết hạn trong 1 giờ tới mà chưa được đánh dấu hoàn thành."
            enableVibration(true)
        }

        manager.createNotificationChannel(normalChannel)
        manager.createNotificationChannel(urgentChannel)
    }
}
