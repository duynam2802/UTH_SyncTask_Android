package com.duynd.uthsynctask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.duynd.uthsynctask.domain.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Xử lý nút "Đã biết / Hoàn thành" ngay trên thông báo, không cần mở app.
 * Dùng `goAsync()` vì việc đánh dấu hoàn thành có thể cần gọi mạng (cập nhật Google Calendar).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val appContext = context.applicationContext

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SyncRepository(appContext).setCompleted(eventId, true)
                if (notificationId != -1) {
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.duynd.uthsynctask.ACTION_MARK_DONE"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
