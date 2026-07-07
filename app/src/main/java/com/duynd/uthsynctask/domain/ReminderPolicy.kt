package com.duynd.uthsynctask.domain

import com.duynd.uthsynctask.data.model.ReminderTier
import com.duynd.uthsynctask.data.model.SyncedEvent

/**
 * Quy tắc nhắc nhở deadline (theo đúng yêu cầu ban đầu):
 *  - Bắt đầu nhắc từ 12h trước deadline, lặp lại mỗi 1 giờ nếu chưa đánh dấu "Đã biết"/"Hoàn thành".
 *  - Trong 1h cuối mà vẫn chưa xong -> chuyển sang mức khẩn cấp, nhắc dày hơn (mỗi 15 phút),
 *    thông báo nổi bật + âm thanh/rung mạnh hơn.
 *  - Sự kiện đã hoàn thành hoặc đã quá hạn từ lâu thì không nhắc nữa.
 */
object ReminderPolicy {
    private const val ADVANCE_WINDOW_MILLIS = 12 * 60 * 60 * 1000L   // 12 giờ trước deadline
    private const val URGENT_WINDOW_MILLIS = 60 * 60 * 1000L         // 1 giờ cuối trước deadline
    private const val OVERDUE_GRACE_MILLIS = 2 * 60 * 60 * 1000L     // vẫn nhắc thêm tối đa 2h sau khi trễ hạn

    private const val NORMAL_REPEAT_INTERVAL_MILLIS = 60 * 60 * 1000L   // lặp mỗi 1 giờ
    private const val URGENT_REPEAT_INTERVAL_MILLIS = 15 * 60 * 1000L   // lặp mỗi 15 phút

    fun evaluateTier(event: SyncedEvent, nowMillis: Long): ReminderTier {
        if (event.isCompleted) return ReminderTier.NONE

        val timeUntilDeadline = event.endTimeMillis - nowMillis

        return when {
            timeUntilDeadline < -OVERDUE_GRACE_MILLIS -> ReminderTier.NONE // trễ hạn đã lâu, thôi không làm phiền nữa
            timeUntilDeadline > ADVANCE_WINDOW_MILLIS -> ReminderTier.NONE // còn quá xa, chưa cần nhắc
            timeUntilDeadline <= URGENT_WINDOW_MILLIS -> ReminderTier.URGENT
            else -> ReminderTier.NORMAL
        }
    }

    fun shouldNotifyNow(event: SyncedEvent, tier: ReminderTier, nowMillis: Long): Boolean {
        if (tier == ReminderTier.NONE) return false

        val interval = if (tier == ReminderTier.URGENT) URGENT_REPEAT_INTERVAL_MILLIS else NORMAL_REPEAT_INTERVAL_MILLIS
        return nowMillis - event.lastNotifiedAtMillis >= interval
    }
}
