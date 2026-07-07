package com.duynd.uthsynctask.data.model

/** Nguồn dữ liệu deadline/lịch học. PORTAL đã có model sẵn nhưng CHƯA được đưa vào luồng
 *  đồng bộ chính (SyncRepository chỉ lặp qua COURSES, THNN) vì còn thiếu cách đăng nhập. */
enum class EventSource(val displayName: String, val baseUrl: String) {
    COURSES("Courses UTH", "https://courses.ut.edu.vn"),
    THNN("thnn UTH", "https://thnn.ut.edu.vn"),
    PORTAL("Portal UTH", "https://portal.ut.edu.vn")
}

/**
 * Một deadline/sự kiện đã lấy được từ Moodle, đại diện cho 1 dòng dữ liệu xuyên suốt
 * vòng đời: lấy về -> đồng bộ lên Google Calendar -> theo dõi hoàn thành -> xoá.
 *
 * [id] là khoá ổn định (source + id gốc bên Moodle) dùng để:
 *  - Tránh đồng bộ trùng lặp giữa các lần chạy.
 *  - Gắn vào `extendedProperties.private` của sự kiện Google Calendar để có thể
 *    tìm lại / cập nhật / xoá đúng sự kiện sau này, kể cả khi người dùng đổi lịch lưu.
 */
data class SyncedEvent(
    val id: String,
    val source: EventSource,
    val title: String,
    val courseName: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val sourceUrl: String,
    val isPreciseTime: Boolean = false,
    val googleCalendarId: String? = null,
    val googleEventId: String? = null,
    val isCompleted: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val lastNotifiedAtMillis: Long = 0L
) {
    val isSyncedToGoogle: Boolean get() = googleEventId != null
}

/** Một lịch Google Calendar của người dùng, hiển thị trong màn hình chọn lịch lưu. */
data class GoogleCalendarOption(
    val id: String,
    val summary: String,
    val isPrimary: Boolean,
    val backgroundColorHex: String?,
    val accessRole: String
) {
    /** Chỉ những lịch có quyền ghi mới cho phép chọn làm nơi lưu deadline. */
    val isWritable: Boolean get() = accessRole == "owner" || accessRole == "writer"
}

/** Kết quả của một lần đồng bộ, dùng để hiển thị popup/snackbar cho người dùng. */
sealed class SyncOutcome {
    data class Success(
        val newEventsCount: Int,
        val updatedEventsCount: Int,
        val totalEventsCount: Int,
        val warnings: List<String> = emptyList()
    ) : SyncOutcome()

    data object NeedsGoogleAuthorization : SyncOutcome()
    data object NeedsCalendarSelection : SyncOutcome()
    data class UthLoginFailed(val message: String) : SyncOutcome()
    data class Error(val message: String) : SyncOutcome()
}
