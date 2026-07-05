package com.duynd.uthsynctask

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val preferences = AppPreferences(applicationContext)
            val credentials = preferences.getUthCredentials()
            val accountEmail = preferences.getGoogleAccountEmail()
            val selectedCalendarId = preferences.getSelectedCalendarId()
            if (credentials.mssv.isBlank() || credentials.password.isBlank() || accountEmail.isNullOrBlank()) {
                Log.e("SyncWorker", "Missing credentials or Google account")
                return@withContext Result.failure()
            }

            val token = GoogleAuthUtil.getToken(applicationContext, accountEmail, "oauth2:https://www.googleapis.com/auth/calendar")
            val scraper = UthScraper()
            val calendarHelper = CalendarHelper(applicationContext)
            val resultHtml = scraper.loginAndGetSchedule(credentials.mssv, credentials.password)

            if (resultHtml != null) {
                val tasks = parseEventsFromHtml(resultHtml)
                calendarHelper.syncToGoogleApi(token, tasks, selectedCalendarId)
                if (preferences.isReminderEnabled() && preferences.getNotificationMode() != NotificationMode.OFF) {
                    NotificationHelper(applicationContext).notifyUpcomingEvent("Nhắc lịch học", "Có ${tasks.size} lịch học cần kiểm tra.", "sync-worker")
                }
                Log.d("SyncWorker", "Sync completed successfully")
                Result.success()
            } else {
                Log.e("SyncWorker", "Failed to login or get schedule")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during sync", e)
            Result.retry()
        }
    }
}
