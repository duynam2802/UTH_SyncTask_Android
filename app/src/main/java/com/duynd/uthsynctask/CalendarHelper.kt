package com.duynd.uthsynctask

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CalendarHelper(private val context: android.content.Context) {

    private val api: GoogleCalendarService = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleCalendarService::class.java)

    suspend fun listCalendars(token: String): List<GoogleCalendarItem> {
        val response = api.listCalendars("Bearer $token")
        return if (response.isSuccessful) response.body()?.items ?: emptyList() else emptyList()
    }

    suspend fun syncToGoogleApi(token: String, assignments: List<Assignment>, calendarId: String = "primary") {
        val bearerToken = "Bearer $token"

        for (assignment in assignments) {
            try {
                val timeMin = "${assignment.date}T00:00:00Z"
                val existing = api.listEvents(bearerToken, calendarId, timeMin, assignment.title)

                if (existing.isSuccessful) {
                    val items = existing.body()?.items ?: emptyList()
                    val alreadyExists = items.any { it.summary == assignment.title }

                    if (!alreadyExists) {
                        val event = GoogleEvent(
                            summary = assignment.title,
                            description = "Đồng bộ từ UTH Tasks\nLink: ${assignment.url}",
                            start = EventDateTime("${assignment.date}T08:00:00Z"),
                            end = EventDateTime("${assignment.date}T09:00:00Z")
                        )
                        val response = api.insertEvent(bearerToken, calendarId, event)
                        if (response.isSuccessful) {
                            Log.d("CalendarAPI", "✅ Success: ${assignment.title}")
                        } else {
                            Log.e("CalendarAPI", "❌ Insert failed: ${response.errorBody()?.string()}")
                        }
                    } else {
                        Log.d("CalendarAPI", "ℹ️ Skip: ${assignment.title} (Already exists)")
                    }
                } else {
                    Log.e("CalendarAPI", "❌ List failed: ${existing.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CalendarAPI", "❌ Error: ${e.message}")
            }
        }
    }

    suspend fun deleteMatchingEvents(token: String, calendarId: String, title: String, date: String): Int {
        val bearerToken = "Bearer $token"
        val existing = api.listEvents(bearerToken, calendarId, "${date}T00:00:00Z", title)
        if (!existing.isSuccessful) return 0
        val events = existing.body()?.items ?: emptyList()
        var deleted = 0
        events.filter { it.summary.contains(title, ignoreCase = true) || it.summary.contains("Đã hoàn thành", ignoreCase = true) }.forEach { event ->
            val response = api.deleteEvent(bearerToken, calendarId, event.id)
            if (response.isSuccessful) deleted++
        }
        return deleted
    }

    suspend fun markEventCompleted(token: String, calendarId: String, title: String, date: String): Boolean {
        val bearerToken = "Bearer $token"
        val existing = api.listEvents(bearerToken, calendarId, "${date}T00:00:00Z", title)
        if (!existing.isSuccessful) return false
        val events = existing.body()?.items ?: emptyList()
        val target = events.firstOrNull { it.summary.contains(title, ignoreCase = true) } ?: return false
        val response = api.updateEvent(
            bearerToken,
            calendarId,
            target.id,
            GoogleEvent(
                summary = "$title (Đã hoàn thành)",
                description = "Đánh dấu hoàn thành từ UTH SyncTask",
                start = EventDateTime("${date}T08:00:00Z"),
                end = EventDateTime("${date}T09:00:00Z")
            )
        )
        return response.isSuccessful
    }
}
