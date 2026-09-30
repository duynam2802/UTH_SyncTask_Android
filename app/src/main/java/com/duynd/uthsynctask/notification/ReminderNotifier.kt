package com.duynd.uthsynctask.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duynd.uthsynctask.MainActivity
import com.duynd.uthsynctask.R
import com.duynd.uthsynctask.data.model.EventSource
import com.duynd.uthsynctask.data.model.NotificationSettings
import com.duynd.uthsynctask.data.model.ReminderTier
import com.duynd.uthsynctask.data.model.SyncedEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dựng và hiển thị thông báo nhắc deadline, độ "gắt" tăng dần khi càng gần hạn. */
class ReminderNotifier(private val context: Context) {

    private val timeFormat = SimpleDateFormat("HH:mm dd/MM", Locale("vi")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private val largeIconBitmap by lazy {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        } catch (_: Exception) {
            null
        }
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

        val isPortal = event.source == EventSource.PORTAL
        val title = when {
            isPortal && tier == ReminderTier.URGENT -> "⏰ Sắp vào học: ${event.title}"
            isPortal -> "Lịch học sắp tới: ${event.title}"
            tier == ReminderTier.URGENT -> "⏰ Sắp hết hạn: ${event.title}"
            else -> "Nhắc deadline: ${event.title}"
        }

        val timeLabel = if (isPortal) "Bắt đầu" else "Hạn"
        val referenceTime = if (isPortal) event.startTimeMillis else event.endTimeMillis
        val contentText = "${event.source.displayName} · $timeLabel: ${timeFormat.format(Date(referenceTime))}"

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
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF008588.toInt()) // Tông màu Teal UTH
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

        largeIconBitmap?.let { builder.setLargeIcon(it) }

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
            val soundUri = settings.soundUri?.let { Uri.parse(it) }
                ?: if (tier == ReminderTier.URGENT) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            builder.setSound(soundUri)
        }
        
        if (settings.vibrationEnabled) {
            val pattern = if (tier == ReminderTier.URGENT) {
                longArrayOf(0, 1000, 500, 1000, 500, 1000)
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

    @SuppressLint("MissingPermission")
    fun notifyRoomChange(event: SyncedEvent, hoursBefore: Int, settings: NotificationSettings) {
        if (!hasNotificationPermission()) return

        val channelId = if (hoursBefore <= 1) {
            NotificationChannels.CHANNEL_URGENT
        } else {
            NotificationChannels.CHANNEL_NORMAL
        }
        val notificationId = (event.id + "_room_$hoursBefore").hashCode()

        val title = "⚠️ Đổi phòng học: ${event.title.substringBefore(" (")}"
        val prevRoomText = event.previousRoom ?: "phòng cũ"
        val newRoomText = event.room ?: "phòng mới"
        val contentText = "Thông báo trước ${hoursBefore}h: Môn học đã thay đổi phòng từ $prevRoomText sang $newRoomText. Bắt đầu lúc ${timeFormat.format(
            Date(event.startTimeMillis)
        )}."

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF008588.toInt())
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(
                if (hoursBefore <= 1) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        largeIconBitmap?.let { builder.setLargeIcon(it) }

        if (hoursBefore <= 1 && settings.fullScreenEnabled) {
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
            val soundUri = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
        }

        if (settings.vibrationEnabled) {
            val pattern = if (hoursBefore <= 1) {
                longArrayOf(0, 1000, 500, 1000, 500, 1000)
            } else {
                longArrayOf(0, 250, 100, 250)
            }
            builder.setVibrate(pattern)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun notifyClassPaused(event: SyncedEvent, settings: NotificationSettings) {
        if (!hasNotificationPermission()) return

        val channelId = NotificationChannels.CHANNEL_NORMAL
        val notificationId = (event.id + "_paused").hashCode()

        val cleanTitle = event.title.substringBefore(" (")
        val title = "🚫 Thông báo TẠM NGƯNG HỌC: $cleanTitle"
        val contentText = "Buổi học lúc ${timeFormat.format(Date(event.startTimeMillis))} đã được thông báo TẠM NGƯNG trên Portal UTH."

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD32F2F.toInt())
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        largeIconBitmap?.let { builder.setLargeIcon(it) }

        if (!settings.soundEnabled) {
            builder.setSilent(true)
        } else {
            val soundUri = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
        }

        if (settings.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun notifyPortalTokenExpired(settings: NotificationSettings) {
        if (!hasNotificationPermission()) return

        val channelId = NotificationChannels.CHANNEL_NORMAL
        val notificationId = 999991

        val title = "⚠️ Phiên đăng nhập Portal đã hết hạn"
        val contentText = "Chạm vào đây để đăng nhập lại Portal UTH và tiếp tục đồng bộ thời khóa biểu."

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_PORTAL_LOGIN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF9800.toInt())
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        largeIconBitmap?.let { builder.setLargeIcon(it) }

        if (!settings.soundEnabled) {
            builder.setSilent(true)
        } else {
            val soundUri = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
        }

        if (settings.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 300, 150, 300))
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_OPEN_PORTAL_LOGIN = "EXTRA_OPEN_PORTAL_LOGIN"
    }
}
