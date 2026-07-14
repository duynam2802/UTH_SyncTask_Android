package com.duynd.uthsynctask.data.remote.google

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleCalendarApi {

    @GET("calendar/v3/users/me/calendarList")
    suspend fun listCalendars(
        @Header("Authorization") token: String
    ): Response<GoogleCalendarListResponse>

    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun listEvents(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String? = null,
        @Query("timeMax") timeMax: String? = null,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("maxResults") maxResults: Int = 250
    ): Response<GoogleEventsListResponse>

    /** Dùng để tìm sự kiện theo nhãn riêng (uthSyncId) - phục vụ chống trùng lặp. */
    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun listEventsByPrivateProperty(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Query("privateExtendedProperty") privateExtendedProperty: String,
        @Query("singleEvents") singleEvents: Boolean,
        @Query("showDeleted") showDeleted: Boolean
    ): Response<GoogleEventsListResponse>

    @POST("calendar/v3/calendars/{calendarId}/events")
    suspend fun insertEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Body event: GoogleEventBody
    ): Response<GoogleEventItem>

    @PATCH("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun updateEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: GoogleEventBody
    ): Response<GoogleEventItem>

    @DELETE("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): Response<Unit>
}

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

data class GoogleCalendarListResponse(val items: List<GoogleCalendarListItem> = emptyList())

data class GoogleCalendarListItem(
    val id: String,
    val summary: String,
    val primary: Boolean? = null,
    val backgroundColor: String? = null,
    val accessRole: String = "reader"
)

data class GoogleEventsListResponse(val items: List<GoogleEventItem> = emptyList())

data class GoogleEventItem(
    val id: String? = null,
    val summary: String? = null,
    val start: GoogleEventDateTime? = null,
    val htmlLink: String? = null
)

data class GoogleEventBody(
    val summary: String,
    val description: String? = null,
    val start: GoogleEventDateTime,
    val end: GoogleEventDateTime,
    val reminders: GoogleEventReminders = GoogleEventReminders(),
    val extendedProperties: GoogleExtendedProperties? = null,
    val source: GoogleEventSource? = null
)

data class GoogleEventDateTime(
    val dateTime: String? = null,
    val date: String? = null,
    val timeZone: String = "Asia/Ho_Chi_Minh"
)

data class GoogleEventReminders(
    val useDefault: Boolean = false,
    val overrides: List<GoogleReminderOverride> = listOf(GoogleReminderOverride())
)

data class GoogleReminderOverride(
    val method: String = "popup",
    val minutes: Int = 60
)

data class GoogleExtendedProperties(
    val private: Map<String, String> = emptyMap()
)

/** Gắn link gốc (bài trên Moodle) vào sự kiện Google, hiện dưới dạng "xem thêm tại nguồn". */
data class GoogleEventSource(
    val title: String,
    val url: String
)

/** Khoá dùng trong extendedProperties.private để nhận diện sự kiện do app này tạo ra. */
const val UTH_SYNC_ID_KEY = "uthSyncId"
