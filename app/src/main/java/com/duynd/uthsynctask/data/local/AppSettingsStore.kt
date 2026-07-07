package com.duynd.uthsynctask.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "uth_app_settings")

data class SelectedCalendar(val id: String, val summary: String)

/**
 * Lưu các cài đặt chung của app: lịch Google đang được chọn để lưu deadline,
 * thời điểm đồng bộ gần nhất. Người dùng có thể đổi lịch bất cứ lúc nào ở màn Cài đặt.
 */
class AppSettingsStore(private val context: Context) {

    private object Keys {
        val CALENDAR_ID = stringPreferencesKey("selected_calendar_id")
        val CALENDAR_SUMMARY = stringPreferencesKey("selected_calendar_summary")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at_millis")
    }

    val selectedCalendarFlow: Flow<SelectedCalendar?> = context.settingsDataStore.data.map { prefs ->
        val id = prefs[Keys.CALENDAR_ID]
        val summary = prefs[Keys.CALENDAR_SUMMARY]
        if (id != null && summary != null) SelectedCalendar(id, summary) else null
    }

    val lastSyncAtFlow: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.LAST_SYNC_AT] }

    suspend fun setSelectedCalendar(calendar: SelectedCalendar) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CALENDAR_ID] = calendar.id
            prefs[Keys.CALENDAR_SUMMARY] = calendar.summary
        }
    }

    suspend fun clearSelectedCalendar() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.CALENDAR_ID)
            prefs.remove(Keys.CALENDAR_SUMMARY)
        }
    }

    suspend fun updateLastSyncAt(timeMillis: Long) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.LAST_SYNC_AT] = timeMillis }
    }
}
