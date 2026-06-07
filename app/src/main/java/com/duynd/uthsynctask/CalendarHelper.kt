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

    suspend fun syncToGoogleApi(token: String, assignments: List<Assignment>) {
        val bearerToken = "Bearer $token"

        for (assignment in assignments) {
            try {
                // 1. Kiểm tra trùng lặp qua API (tìm theo tiêu đề)
                val timeMin = "${assignment.date}T00:00:00Z"
                val existing = api.listEvents(bearerToken, timeMin, assignment.title)

                if (existing.isSuccessful) {
                    val items = existing.body()?.items ?: emptyList()
                    val alreadyExists = items.any { it.summary == assignment.title }

                    if (!alreadyExists) {
                        // 2. Nếu chưa có thì gửi JSON Insert
                        val event = GoogleEvent(
                            summary = assignment.title,
                            description = "Đồng bộ từ UTH Tasks\nLink: ${assignment.url}",
                            start = EventDateTime("${assignment.date}T08:00:00Z"),
                            end = EventDateTime("${assignment.date}T09:00:00Z")
                        )
                        val response = api.insertEvent(bearerToken, event)
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
}
