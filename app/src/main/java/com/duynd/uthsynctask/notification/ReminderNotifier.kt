package com.duynd.uthsynctask.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duynd.uthsynctask.MainActivity
import com.duynd.uthsynctask.data.model.NotificationSettings
import com.duynd.uthsynctask.data.model.ReminderTier
import com.duynd.uthsynctask.data.model.SyncedEvent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Dựng và hiển thị thông báo nhắc deadline, độ "gắt" tăng dần khi càng gần hạn. */
class ReminderNotifier(private val context: Context) {

    private val timeFormat = SimpleDateFormat("HH:mm dd/MM", Locale("vi")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    @SuppressLint("MissingPermission")
    fun notify(event: SyncedEvent, tier: ReminderTier, settings: NotificationSettings) {
        if (tier == ReminderTier.NONE) return
        if (!hasNotificationPermission()) return

        val channelId = if (tier == ReminderTier.URGENT) {
            NotificationChannels.CHANNEL_URGENT
        } else {
            NotificationChannels.CHANNEL_NORMAL
        }
        val notificationId = event.id.hashCode()

        val isPortal = event.source == com.duynd.uthsynctask.data.model.EventSource.PORTAL
        val title = when {
            isPortal && tier == ReminderTier.URGENT -> "⏰ Sắp vào học: ${event.title}"
            isPortal -> "Lịch học sắp tới: ${event.title}"
            tier == ReminderTier.URGENT -> "⏰ Sắp hết hạn: ${event.title}"
            else -> "Nhắc deadline: ${event.title}"
        }

        val timeLabel = if (isPortal) "Bắt đầu" else "Hạn"
        val referenceTime = if (isPortal) event.startTimeMillis else event.endTimeMillis
        val contentText = "${event.source.displayName} · $timeLabel: ${timeFormat.format(java.util.Date(referenceTime))}"

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_DONE
                putExtra(NotificationActionReceiver.EXTRA_EVENT_ID, event.id)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(
                if (tier == ReminderTier.URGENT) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(0, if (isPortal) "Đã xem" else "Đã biết / Hoàn thành", markDoneIntent)

        // Thiết lập Full Screen Intent nếu là mức URGENT và được bật trong cài đặt
        if (tier == ReminderTier.URGENT && settings.fullScreenEnabled) {
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                Intent(context, FullScreenReminderActivity::class.java).apply {
                    putExtra("EXTRA_TITLE", title)
                    putExtra("EXTRA_CONTENT", contentText)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        if (!settings.soundEnabled) {
            builder.setSilent(true)
        } else {
            // Sử dụng âm thanh người dùng chọn, hoặc mặc định nếu chưa chọn
            val soundUri = settings.soundUri?.let { android.net.Uri.parse(it) }
                ?: if (tier == ReminderTier.URGENT) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            builder.setSound(soundUri)
        }
        
        if (settings.vibrationEnabled) {
            val pattern = if (tier == ReminderTier.URGENT) {
                longArrayOf(0, 1000, 500, 1000, 500, 1000) // Rung dài và mạnh
            } else {
                longArrayOf(0, 250, 100, 250)
            }
            builder.setVibrate(pattern)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(event: SyncedEvent) {
        NotificationManagerCompat.from(context).cancel(event.id.hashCode())
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
