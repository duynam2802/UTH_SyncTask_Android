package com.duynd.uthsynctask.data.remote.google

import com.duynd.uthsynctask.data.model.GoogleCalendarOption
import com.duynd.uthsynctask.data.model.SyncedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Bọc [GoogleCalendarApi] thành các thao tác nghiệp vụ: liệt kê lịch, thêm/sửa/xoá sự kiện,
 * tìm sự kiện đã đồng bộ trước đó theo nhãn riêng [UTH_SYNC_ID_KEY] (dùng để chống trùng lặp
 * và để xoá đúng sự kiện khi người dùng đổi lịch lưu hoặc xoá deadline).
 */
class GoogleCalendarRepository {

    private val api: GoogleCalendarApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleCalendarApi::class.java)

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    suspend fun listWritableCalendars(accessToken: String): Result<List<GoogleCalendarOption>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.listCalendars(bearer(accessToken))
                if (!response.isSuccessful) {
                    error("Không tải được danh sách lịch Google (mã ${response.code()}).")
                }
                response.body()?.items.orEmpty().map { item ->
                    GoogleCalendarOption(
                        id = item.id,
                        summary = item.summary,
                        isPrimary = item.primary == true,
                        backgroundColorHex = item.backgroundColor,
                        accessRole = item.accessRole
                    )
                }.filter { it.isWritable }
            }
        }

    /** Tìm eventId đã tồn tại trong 1 lịch cụ thể ứng với 1 [syncId], nếu có. */
    suspend fun findEventIdBySyncId(
        accessToken: String,
        calendarId: String,
        syncId: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.listEventsByPrivateProperty(
                token = bearer(accessToken),
                calendarId = calendarId,
                privateExtendedProperty = "$UTH_SYNC_ID_KEY=$syncId",
                singleEvents = true,
                showDeleted = false
            )
            if (!response.isSuccessful) return@runCatching null
            response.body()?.items.orEmpty().firstOrNull()?.id
        }
    }

    /**
     * Tìm TẤT CẢ lịch (trong [calendars]) đang chứa 1 sự kiện ứng với [syncId].
     * Dùng để phát hiện & dọn sự kiện bị lưu nhầm ở lịch khác (khi người dùng đổi lịch lưu).
     */
    suspend fun findAllOccurrences(
        accessToken: String,
        syncId: String,
        calendars: List<GoogleCalendarOption>
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        calendars.mapNotNull { calendar ->
            val eventId = findEventIdBySyncId(accessToken, calendar.id, syncId).getOrNull()
            eventId?.let { calendar.id to it }
        }
    }

    suspend fun listAllEvents(
        accessToken: String,
        calendarId: String,
        timeMin: Long? = null
    ): Result<List<GoogleEventItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val timeMinStr = timeMin?.let { isoFormat.format(java.util.Date(it)) + "Z" }
            val response = api.listEvents(bearer(accessToken), calendarId, timeMin = timeMinStr)
            if (!response.isSuccessful) {
                error("Không thể lấy danh sách sự kiện (mã ${response.code()}).")
            }
            response.body()?.items.orEmpty()
        }
    }

    suspend fun insertEvent(
        accessToken: String,
        calendarId: String,
        event: SyncedEvent
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = toEventBody(event)
            val response = api.insertEvent(bearer(accessToken), calendarId, body)
            if (!response.isSuccessful || response.body()?.id == null) {
                error("Không thể thêm sự kiện '${event.title}' vào Google Calendar (mã ${response.code()}).")
            }
            response.body()!!.id!!
        }
    }

    suspend fun updateEvent(
        accessToken: String,
        calendarId: String,
        googleEventId: String,
        event: SyncedEvent
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = toEventBody(event)
            val response = api.updateEvent(bearer(accessToken), calendarId, googleEventId, body)
            if (!response.isSuccessful) {
                error("Không thể cập nhật sự kiện '${event.title}' (mã ${response.code()}).")
            }
        }
    }

    suspend fun deleteEvent(
        accessToken: String,
        calendarId: String,
        googleEventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.deleteEvent(bearer(accessToken), calendarId, googleEventId)
            // Google trả 410 (Gone) nếu sự kiện đã bị xoá từ trước - coi như thành công.
            if (!response.isSuccessful && response.code() != 410 && response.code() != 404) {
                error("Không thể xoá sự kiện (mã ${response.code()}).")
            }
        }
    }

    private val descriptionTimeFormat = SimpleDateFormat("HH:mm 'ngày' dd/MM/yyyy", Locale("vi")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private fun toEventBody(event: SyncedEvent): GoogleEventBody {
        val statusPrefix = if (event.isCompleted) "✅ " else ""

        val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        
        // Phân biệt logic hiển thị:
        // 1. PORTAL (Lịch học): Hiển thị đúng block thời gian từ lúc bắt đầu đến kết thúc.
        // 2. Moodle (Deadline): Hiển thị từ 0h sáng ngày hôm đó tới đúng giờ deadline để dễ theo dõi.
        val startMillis = if (event.source == com.duynd.uthsynctask.data.model.EventSource.PORTAL) {
            event.startTimeMillis
        } else {
            Calendar.getInstance(vietnamTimeZone).apply {
                timeInMillis = event.endTimeMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val description = buildString {
            append("Thời gian bắt đầu: ${descriptionTimeFormat.format(java.util.Date(event.startTimeMillis))}\n")
            append("Thời gian kết thúc: ${descriptionTimeFormat.format(java.util.Date(event.endTimeMillis))}\n")
            if (!event.isPreciseTime) {
                append("⚠️ Giờ ước tính (không lấy được giờ chính xác từ nguồn).\n")
            }
            append("Nguồn: ${event.source.displayName}\n")
            append("Xem chi tiết trên Courses: ${event.sourceUrl}")
        }

        return GoogleEventBody(
            summary = "$statusPrefix${event.title}",
            description = description,
            start = GoogleEventDateTime(dateTime = isoFormat.format(java.util.Date(startMillis))),
            end = GoogleEventDateTime(dateTime = isoFormat.format(java.util.Date(event.endTimeMillis))),
            reminders = GoogleEventReminders(
                useDefault = false,
                overrides = listOf(GoogleReminderOverride(method = "popup", minutes = 60))
            ),
            extendedProperties = GoogleExtendedProperties(
                private = mapOf(UTH_SYNC_ID_KEY to event.id)
            )
        )
    }

    private fun bearer(token: String) = "Bearer $token"
}
