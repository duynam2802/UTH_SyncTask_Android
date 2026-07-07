package com.duynd.uthsynctask.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duynd.uthsynctask.data.local.AppSettingsStore
import com.duynd.uthsynctask.data.local.EventStore
import com.duynd.uthsynctask.data.model.SyncOutcome
import com.duynd.uthsynctask.data.model.SyncedEvent
import com.duynd.uthsynctask.domain.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScheduleSyncState {
    data object Idle : ScheduleSyncState()
    data object Syncing : ScheduleSyncState()
    data class Finished(val outcome: SyncOutcome) : ScheduleSyncState()
}

data class ScheduleUiState(
    val events: List<SyncedEvent> = emptyList(),
    val lastSyncAtMillis: Long? = null
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val eventStore = EventStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val syncRepository = SyncRepository(application)

    val uiState: StateFlow<ScheduleUiState> = combine(
        eventStore.eventsFlow.map { list -> list.sortedBy { it.startTimeMillis } },
        settingsStore.lastSyncAtFlow
    ) { events, lastSync ->
        ScheduleUiState(events = events, lastSyncAtMillis = lastSync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    private val _syncState = MutableStateFlow<ScheduleSyncState>(ScheduleSyncState.Idle)
    val syncState: StateFlow<ScheduleSyncState> = _syncState.asStateFlow()

    fun syncNow() {
        if (_syncState.value is ScheduleSyncState.Syncing) return
        _syncState.value = ScheduleSyncState.Syncing
        viewModelScope.launch {
            val outcome = syncRepository.sync()
            _syncState.value = ScheduleSyncState.Finished(outcome)
        }
    }

    fun dismissSyncResult() {
        _syncState.value = ScheduleSyncState.Idle
    }

    fun toggleCompleted(event: SyncedEvent) {
        viewModelScope.launch {
            syncRepository.setCompleted(event.id, !event.isCompleted)
        }
    }

    fun deleteEvent(event: SyncedEvent) {
        viewModelScope.launch {
            syncRepository.deleteEvent(event.id)
        }
    }

    /** Tạo một deadline giả sắp hết hạn để test thông báo và tính năng nhắc lại sau 1 phút. */
    fun createTestUrgentEvent() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val testEvent = SyncedEvent(
                id = "test_urgent_${now}",
                source = com.duynd.uthsynctask.data.model.EventSource.COURSES,
                title = "Deadline Test (Nhắc lại sau 1p)",
                courseName = "Môn Học Thử Nghiệm",
                startTimeMillis = now - 30 * 60 * 1000,
                endTimeMillis = now + 30 * 60 * 1000,
                sourceUrl = "https://courses.ut.edu.vn",
                isPreciseTime = true
            )
            eventStore.upsert(testEvent)
            
            val notifier = com.duynd.uthsynctask.notification.ReminderNotifier(getApplication())
            val settingsStore = com.duynd.uthsynctask.data.local.NotificationSettingsStore(getApplication())
            
            // 1. Thông báo lần đầu ngay lập tức
            val settings = settingsStore.getCurrent()
            if (settings.enabled) {
                notifier.notify(testEvent, com.duynd.uthsynctask.data.model.ReminderTier.URGENT, settings)
                eventStore.updateLastNotifiedAt(testEvent.id, System.currentTimeMillis())
            }

            // 2. Tự động nhắc lại sau 1 phút để test
            kotlinx.coroutines.delay(60000)
            
            // Kiểm tra xem người dùng có xoá event hoặc đánh dấu hoàn thành trong lúc chờ không
            val freshEvent = eventStore.getAll().firstOrNull { it.id == testEvent.id }
            if (freshEvent != null && !freshEvent.isCompleted) {
                val freshSettings = settingsStore.getCurrent()
                if (freshSettings.enabled) {
                    notifier.notify(freshEvent, com.duynd.uthsynctask.data.model.ReminderTier.URGENT, freshSettings)
                    eventStore.updateLastNotifiedAt(freshEvent.id, System.currentTimeMillis())
                }
            }
        }
    }
}
