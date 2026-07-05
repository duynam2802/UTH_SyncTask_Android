package com.duynd.uthsynctask

import retrofit2.Response
import retrofit2.http.*

interface GoogleCalendarService {
    @GET("calendar/v3/users/me/calendarList")
    suspend fun listCalendars(@Header("Authorization") token: String): Response<GoogleCalendarListResponse>

    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun listEvents(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String? = null,
        @Query("q") query: String? = null
    ): Response<GoogleEventList>

    @POST("calendar/v3/calendars/{calendarId}/events")
    suspend fun insertEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Body event: GoogleEvent
    ): Response<Any>

    @PATCH("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun updateEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: GoogleEvent
    ): Response<Any>

    @DELETE("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") token: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): Response<Any>
}

data class GoogleCalendarListResponse(val items: List<GoogleCalendarItem>)
data class GoogleCalendarItem(val id: String, val summary: String, val primary: Boolean? = false)
data class GoogleEventList(val items: List<GoogleEventItem>)
data class GoogleEventItem(val id: String, val summary: String)
data class GoogleEvent(
    val summary: String,
    val description: String,
    val start: EventDateTime,
    val end: EventDateTime,
    val id: String? = null
)
data class EventDateTime(val dateTime: String, val timeZone: String = "UTC")
