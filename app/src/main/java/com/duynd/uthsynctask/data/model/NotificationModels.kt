package com.duynd.uthsynctask.data.model

/** Mức độ khẩn cấp của một lần nhắc nhở, quyết định độ "gắt" của thông báo. */
enum class ReminderTier {
    /** Chưa tới lúc cần nhắc (còn xa hơn 12h, đã xong, hoặc đã quá hạn). */
    NONE,

    /** Trong khoảng 12h -> 1h trước deadline: nhắc nhẹ nhàng, lặp lại mỗi 1 giờ nếu chưa "Đã biết". */
    NORMAL,

    /** Trong 1h cuối trước deadline mà vẫn chưa hoàn thành: nhắc gấp, âm thanh/rung mạnh hơn,
     *  lặp lại mỗi 15 phút. */
    URGENT
}

/** Cài đặt thông báo do người dùng tuỳ chỉnh ở tab Thông báo. */
data class NotificationSettings(
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = false
)
