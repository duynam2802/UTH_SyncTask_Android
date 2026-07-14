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
    val groups: List<EventGroup> = emptyList(),
    val lastSyncAtMillis: Long? = null
)

data class EventGroup(
    val title: String,
    val events: List<SyncedEvent>
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val eventStore = EventStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val syncRepository = SyncRepository(application)

    val uiState: StateFlow<ScheduleUiState> = combine(
        eventStore.eventsFlow,
        settingsStore.lastSyncAtFlow
    ) { events, lastSync ->
        val groups = groupEvents(events)
        ScheduleUiState(groups = groups, lastSyncAtMillis = lastSync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    private fun groupEvents(events: List<SyncedEvent>): List<EventGroup> {
        val now = System.currentTimeMillis()
        val startOfWeek = getStartOfWeekMillis()
        val endOfWeek = startOfWeek + 7L * 24 * 60 * 60 * 1000

        val (completed, active) = events.partition { it.isCompleted }

        val upcoming = active.filter { it.startTimeMillis > now && it.startTimeMillis <= now + 24 * 60 * 60 * 1000 }
            .sortedBy { it.startTimeMillis }
        val thisWeek = active.filter { it.startTimeMillis > now + 24 * 60 * 60 * 1000 && it.startTimeMillis <= endOfWeek }
            .sortedBy { it.startTimeMillis }
        val others = active.filter { it.startTimeMillis > endOfWeek || it.startTimeMillis <= now }
            .sortedBy { it.startTimeMillis }

        val completedThisWeek = completed.filter { it.startTimeMillis >= startOfWeek && it.startTimeMillis <= endOfWeek }
            .sortedByDescending { it.startTimeMillis }
        val completedEarlier = completed.filter { it.startTimeMillis < startOfWeek }
            .sortedByDescending { it.startTimeMillis }

        return buildList {
            if (upcoming.isNotEmpty()) add(EventGroup("🔥 Sắp diễn ra (24h tới)", upcoming))
            if (thisWeek.isNotEmpty()) add(EventGroup("📅 Trong tuần này", thisWeek))
            if (others.isNotEmpty()) add(EventGroup("⏳ Sắp tới / Khác", others))
            if (completedThisWeek.isNotEmpty()) add(EventGroup("✅ Đã xong tuần này", completedThisWeek))
            if (completedEarlier.isNotEmpty()) add(EventGroup("📁 Đã hoàn thành cũ hơn", completedEarlier))
        }
    }

    private fun getStartOfWeekMillis(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

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
}
