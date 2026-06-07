package com.duynd.uthsynctask

import retrofit2.Response
import retrofit2.http.*

interface GoogleCalendarService {
    @GET("calendar/v3/calendars/primary/events")
    suspend fun listEvents(
        @Header("Authorization") token: String,
        @Query("timeMin") timeMin: String,
        @Query("q") query: String
    ): Response<GoogleEventList>

    @POST("calendar/v3/calendars/primary/events")
    suspend fun insertEvent(
        @Header("Authorization") token: String,
        @Body event: GoogleEvent
    ): Response<Any>
}

data class GoogleEventList(val items: List<GoogleEventItem>)

data class GoogleEventItem(val id: String, val summary: String)

data class GoogleEvent(
    val summary: String,
    val description: String,
    val start: EventDateTime,
    val end: EventDateTime
)

data class EventDateTime(val dateTime: String, val timeZone: String = "UTC")
