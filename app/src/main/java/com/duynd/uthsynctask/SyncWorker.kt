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
            val sharedPrefs = applicationContext.getSharedPreferences("UTH_PREFS", Context.MODE_PRIVATE)
            val mssv = sharedPrefs.getString("mssv", "083205012971") ?: ""
            val pass = sharedPrefs.getString("pass", "0964911614@UTH") ?: ""
            val accountEmail = sharedPrefs.getString("google_email", null)

            if (accountEmail == null) {
                Log.e("SyncWorker", "No Google account email found")
                return@withContext Result.failure()
            }

            // Lấy Access Token cho Google Calendar API
            val scope = "oauth2:https://www.googleapis.com/auth/calendar.events"
            val token = GoogleAuthUtil.getToken(applicationContext, accountEmail, scope)

            val scraper = UthScraper()
            val calendarHelper = CalendarHelper(applicationContext)

            val resultHtml = scraper.loginAndGetSchedule(mssv, pass)

            if (resultHtml != null) {
                val tasks = parseEventsFromHtml(resultHtml)
                calendarHelper.syncToGoogleApi(token, tasks)
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
