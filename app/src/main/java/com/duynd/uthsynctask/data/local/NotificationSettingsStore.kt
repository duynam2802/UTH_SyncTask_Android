package com.duynd.uthsynctask.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.duynd.uthsynctask.data.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore by preferencesDataStore(name = "uth_notification_settings")

class NotificationSettingsStore(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("notif_enabled")
        val SOUND = booleanPreferencesKey("notif_sound")
        val VIBRATION = booleanPreferencesKey("notif_vibration")
        val FULL_SCREEN = booleanPreferencesKey("notif_full_screen")
    }

    val settingsFlow: Flow<NotificationSettings> = context.notificationSettingsDataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            soundEnabled = prefs[Keys.SOUND] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            fullScreenEnabled = prefs[Keys.FULL_SCREEN] ?: false
        )
    }

    suspend fun getCurrent(): NotificationSettings = settingsFlow.first()

    suspend fun update(settings: NotificationSettings) {
        context.notificationSettingsDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = settings.enabled
            prefs[Keys.SOUND] = settings.soundEnabled
            prefs[Keys.VIBRATION] = settings.vibrationEnabled
            prefs[Keys.FULL_SCREEN] = settings.fullScreenEnabled
        }
    }
}
