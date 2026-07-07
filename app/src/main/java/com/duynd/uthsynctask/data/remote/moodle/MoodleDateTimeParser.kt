package com.duynd.uthsynctask.data.remote.moodle

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parse chuỗi ngày giờ Moodle hiển thị ở khối "activity-dates" (VD:
 * "Wednesday, 1 July 2026, 4:40 PM"). Thử nhiều định dạng vì Moodle có thể hiển thị
 * khác nhau tuỳ ngôn ngữ hồ sơ người dùng (mẫu thực tế thu được là tiếng Anh, nhưng
 * code vẫn thử thêm vài biến thể để tăng khả năng tương thích).
 */
object MoodleDateTimeParser {

    private val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private val candidatePatterns = listOf(
        "EEEE, d MMMM yyyy, h:mm a" to Locale.ENGLISH,   // "Wednesday, 1 July 2026, 4:40 PM"
        "d MMMM yyyy, h:mm a" to Locale.ENGLISH,          // phòng khi không có tên thứ
        "EEEE, d MMMM yyyy, HH:mm" to Locale.ENGLISH,     // biến thể giờ 24h
        "d MMMM yyyy, HH:mm" to Locale.ENGLISH
    )

    /** Trả về mốc thời gian (epoch millis) theo giờ Việt Nam, hoặc null nếu không parse được. */
    fun parse(rawText: String): Long? {
        val text = rawText.trim()
        for ((pattern, locale) in candidatePatterns) {
            try {
                val format = SimpleDateFormat(pattern, locale)
                format.timeZone = vietnamTimeZone
                format.isLenient = false
                val date = format.parse(text)
                if (date != null) return date.time
            } catch (e: Exception) {
                // thử định dạng tiếp theo
            }
        }
        return null
    }
}
