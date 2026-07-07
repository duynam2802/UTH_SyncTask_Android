package com.duynd.uthsynctask.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duynd.uthsynctask.data.local.NotificationSettingsStore
import com.duynd.uthsynctask.data.model.NotificationSettings
import com.duynd.uthsynctask.notification.ReminderNotifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = NotificationSettingsStore(application)
    private val notifier = ReminderNotifier(application)

    val settings: StateFlow<NotificationSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }
    fun setSoundEnabled(enabled: Boolean) = updateSettings { it.copy(soundEnabled = enabled) }
    fun setVibrationEnabled(enabled: Boolean) = updateSettings { it.copy(vibrationEnabled = enabled) }
    fun setFullScreenEnabled(enabled: Boolean) = updateSettings { it.copy(fullScreenEnabled = enabled) }

    private fun updateSettings(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            settingsStore.update(transform(settings.value))
        }
    }

    /** Gửi 1 thông báo mẫu để người dùng xem trước - dùng dữ liệu giả, không đụng tới deadline thật. */
    fun sendTestNotification() {
        viewModelScope.launch {
            val fakeEvent = com.duynd.uthsynctask.data.model.SyncedEvent(
                id = "__test_notification__",
                source = com.duynd.uthsynctask.data.model.EventSource.COURSES,
                title = "Đây là thông báo thử",
                courseName = null,
                startTimeMillis = System.currentTimeMillis(),
                endTimeMillis = System.currentTimeMillis() + 30 * 60 * 1000,
                sourceUrl = "",
                isPreciseTime = true
            )
            notifier.notify(fakeEvent, com.duynd.uthsynctask.data.model.ReminderTier.NORMAL, settings.value)
        }
    }
}
