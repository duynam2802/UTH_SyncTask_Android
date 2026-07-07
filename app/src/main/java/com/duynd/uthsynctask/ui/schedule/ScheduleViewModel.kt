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
        eventStore.eventsFlow.map { list -> list.sortedByDescending { it.endTimeMillis } },
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
}
