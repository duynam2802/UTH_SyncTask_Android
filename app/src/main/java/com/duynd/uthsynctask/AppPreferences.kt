package com.duynd.uthsynctask

import android.content.Context

class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("UTH_PREFS", Context.MODE_PRIVATE)

    fun saveUthCredentials(credentials: UthCredentials) {
        prefs.edit()
            .putString(KEY_MSSV, credentials.mssv)
            .putString(KEY_PASSWORD, credentials.password)
            .putBoolean(KEY_REMEMBER_CREDENTIALS, credentials.remember)
            .apply()
    }

    fun getUthCredentials(): UthCredentials {
        val mssv = prefs.getString(KEY_MSSV, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        val remember = prefs.getBoolean(KEY_REMEMBER_CREDENTIALS, true)
        return UthCredentials(mssv = mssv, password = password, remember = remember)
    }

    fun clearUthCredentials() {
        prefs.edit()
            .remove(KEY_MSSV)
            .remove(KEY_PASSWORD)
            .remove(KEY_REMEMBER_CREDENTIALS)
            .apply()
    }

    fun saveGoogleAccount(email: String?, name: String? = null) {
        prefs.edit()
            .putString(KEY_GOOGLE_EMAIL, email)
            .putString(KEY_GOOGLE_NAME, name)
            .apply()
    }

    fun getGoogleAccountEmail(): String? = prefs.getString(KEY_GOOGLE_EMAIL, null)
    fun getGoogleAccountName(): String? = prefs.getString(KEY_GOOGLE_NAME, null)

    fun clearGoogleAccount() {
        prefs.edit().remove(KEY_GOOGLE_EMAIL).remove(KEY_GOOGLE_NAME).apply()
    }

    fun saveSelectedCalendar(calendarId: String, calendarName: String) {
        prefs.edit()
            .putString(KEY_SELECTED_CALENDAR_ID, calendarId)
            .putString(KEY_SELECTED_CALENDAR_NAME, calendarName)
            .apply()
    }

    fun getSelectedCalendarId(): String = prefs.getString(KEY_SELECTED_CALENDAR_ID, "primary") ?: "primary"
    fun getSelectedCalendarName(): String = prefs.getString(KEY_SELECTED_CALENDAR_NAME, "Lịch chính") ?: "Lịch chính"

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SYNC, true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun isReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER_ENABLED, true)

    fun setNotificationMode(mode: NotificationMode) {
        prefs.edit().putString(KEY_NOTIFICATION_MODE, mode.value).apply()
    }

    fun getNotificationMode(): NotificationMode = NotificationMode.fromPreferenceValue(prefs.getString(KEY_NOTIFICATION_MODE, NotificationMode.BASIC.value))

    fun saveLastSyncSummary(summary: String) {
        prefs.edit().putString(KEY_LAST_SYNC_SUMMARY, summary).apply()
    }

    fun getLastSyncSummary(): String = prefs.getString(KEY_LAST_SYNC_SUMMARY, "Chưa đồng bộ") ?: "Chưa đồng bộ"

    fun acknowledgeEvent(eventKey: String) {
        val existing = prefs.getStringSet(KEY_ACKNOWLEDGED_EVENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(eventKey)
        prefs.edit().putStringSet(KEY_ACKNOWLEDGED_EVENTS, existing).apply()
    }

    fun isEventAcknowledged(eventKey: String): Boolean = prefs.getStringSet(KEY_ACKNOWLEDGED_EVENTS, emptySet())?.contains(eventKey) == true

    companion object {
        private const val KEY_MSSV = "mssv"
        private const val KEY_PASSWORD = "pass"
        private const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_GOOGLE_NAME = "google_name"
        private const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"
        private const val KEY_SELECTED_CALENDAR_NAME = "selected_calendar_name"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_NOTIFICATION_MODE = "notification_mode"
        private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
        private const val KEY_ACKNOWLEDGED_EVENTS = "acknowledged_events"
    }
}
