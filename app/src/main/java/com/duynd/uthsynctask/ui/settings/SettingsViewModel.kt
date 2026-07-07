package com.duynd.uthsynctask.ui.settings

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duynd.uthsynctask.data.local.AppSettingsStore
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.data.local.SelectedCalendar
import com.duynd.uthsynctask.data.model.GoogleCalendarOption
import com.duynd.uthsynctask.data.remote.google.AuthorizationOutcome
import com.duynd.uthsynctask.data.remote.google.GoogleAuthManager
import com.duynd.uthsynctask.data.remote.google.GoogleCalendarRepository
import com.duynd.uthsynctask.domain.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class GoogleConnectionState {
    data object NotConnected : GoogleConnectionState()
    data object Connecting : GoogleConnectionState()
    data class Connected(val calendars: List<GoogleCalendarOption>) : GoogleConnectionState()
    data class Error(val message: String) : GoogleConnectionState()
}

sealed class CleanupState {
    data object Idle : CleanupState()
    data object Running : CleanupState()
    data class Done(val removedCount: Int) : CleanupState()
    data class Failed(val message: String) : CleanupState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = SecureCredentialStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val googleAuthManager = GoogleAuthManager(application)
    private val googleCalendarRepository = GoogleCalendarRepository()
    private val syncRepository = SyncRepository(application)

    val selectedCalendar: StateFlow<SelectedCalendar?> = settingsStore.selectedCalendarFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _connectionState = MutableStateFlow<GoogleConnectionState>(GoogleConnectionState.NotConnected)
    val connectionState: StateFlow<GoogleConnectionState> = _connectionState.asStateFlow()

    private val _pendingConsent = MutableStateFlow<IntentSender?>(null)
    val pendingConsent: StateFlow<IntentSender?> = _pendingConsent.asStateFlow()

    private val _cleanupState = MutableStateFlow<CleanupState>(CleanupState.Idle)
    val cleanupState: StateFlow<CleanupState> = _cleanupState.asStateFlow()

    init {
        // Thử kết nối ngầm (không hỏi lại) nếu trước đó người dùng đã từng đồng ý.
        viewModelScope.launch {
            when (val outcome = googleAuthManager.authorize()) {
                is AuthorizationOutcome.Granted -> loadCalendars(outcome.accessToken)
                else -> Unit // Chưa kết nối hoặc cần đồng ý - chờ người dùng bấm nút Kết nối.
            }
        }
    }

    fun connectGoogle() {
        _connectionState.value = GoogleConnectionState.Connecting
        viewModelScope.launch {
            when (val outcome = googleAuthManager.authorize()) {
                is AuthorizationOutcome.Granted -> loadCalendars(outcome.accessToken)
                is AuthorizationOutcome.NeedsConsent -> _pendingConsent.value = outcome.intentSender
                is AuthorizationOutcome.Failed -> _connectionState.value = GoogleConnectionState.Error(outcome.message)
            }
        }
    }

    /** Gọi từ Composable ngay sau khi launch IntentSenderRequest, để không launch lặp lại. */
    fun clearPendingConsent() {
        _pendingConsent.value = null
    }

    /** Gọi trong callback của rememberLauncherForActivityResult sau khi người dùng phản hồi popup Google. */
    fun onConsentResult(data: Intent?) {
        when (val outcome = googleAuthManager.resultFromIntent(data)) {
            is AuthorizationOutcome.Granted -> viewModelScope.launch { loadCalendars(outcome.accessToken) }
            is AuthorizationOutcome.Failed -> _connectionState.value = GoogleConnectionState.Error(outcome.message)
            is AuthorizationOutcome.NeedsConsent -> _connectionState.value =
                GoogleConnectionState.Error("Bạn chưa đồng ý cấp quyền truy cập Google Calendar.")
        }
    }

    private suspend fun loadCalendars(token: String) {
        googleCalendarRepository.listWritableCalendars(token)
            .onSuccess { _connectionState.value = GoogleConnectionState.Connected(it) }
            .onFailure {
                _connectionState.value = GoogleConnectionState.Error(it.message ?: "Lỗi không xác định.")
            }
    }

    fun selectCalendar(option: GoogleCalendarOption) {
        viewModelScope.launch {
            settingsStore.setSelectedCalendar(SelectedCalendar(option.id, option.summary))
        }
    }

    fun cleanupDuplicates() {
        _cleanupState.value = CleanupState.Running
        viewModelScope.launch {
            syncRepository.cleanupDuplicatesAcrossCalendars()
                .onSuccess { _cleanupState.value = CleanupState.Done(it) }
                .onFailure { _cleanupState.value = CleanupState.Failed(it.message ?: "Lỗi không xác định.") }
        }
    }

    fun dismissCleanupState() {
        _cleanupState.value = CleanupState.Idle
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            googleAuthManager.revokeAccess()
            settingsStore.clearSelectedCalendar()
            _connectionState.value = GoogleConnectionState.NotConnected
        }
    }

    fun logoutUth(onDone: () -> Unit) {
        viewModelScope.launch {
            credentialStore.clearCredentials()
            onDone()
        }
    }
}
