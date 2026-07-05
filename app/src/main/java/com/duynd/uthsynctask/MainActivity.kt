package com.duynd.uthsynctask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duynd.uthsynctask.ui.theme.UTHSyncTaskTheme
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AppUiState(
    val selectedTab: Int = 0,
    val username: String = "",
    val password: String = "",
    val rememberCredentials: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val notificationMode: NotificationMode = NotificationMode.BASIC,
    val googleAccountName: String = "Chưa đăng nhập",
    val selectedCalendarName: String = "Lịch chính",
    val selectedCalendarId: String = "primary",
    val availableCalendars: List<GoogleCalendarItem> = emptyList(),
    val events: List<Assignment> = emptyList(),
    val isLoading: Boolean = false,
    val status: String = "Sẵn sàng",
    val dialogMessage: String? = null
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val GOOGLE_CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        private const val GOOGLE_CALENDAR_TOKEN_SCOPE = "oauth2:$GOOGLE_CALENDAR_SCOPE"
    }

    private lateinit var preferences: AppPreferences
    private lateinit var notificationHelper: NotificationHelper
    private var uiState by mutableStateOf(AppUiState())
    private var signedInAccount: GoogleSignInAccount? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CALENDAR] == true && permissions[Manifest.permission.WRITE_CALENDAR] == true) {
            checkGoogleLoginAndSync()
        } else {
            showDialog("Cần quyền truy cập Google Calendar để đồng bộ lịch học.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d("UTH_SYNC", "Notification permission handled")
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                signedInAccount = account
                preferences.saveGoogleAccount(account.email, account.displayName)
                uiState = uiState.copy(
                    googleAccountName = account.displayName ?: account.email ?: "Đã đăng nhập",
                    status = "Đã đăng nhập Google"
                )
                if (preferences.isAutoSyncEnabled()) {
                    schedulePeriodicSync()
                }
                lifecycleScope.launch {
                    fetchCalendars(account)
                }
            }
        } catch (e: Exception) {
            Log.e("UTH_SYNC", "Google Sign-In failed: ${e.message}")
            showDialog("Đăng nhập Google thất bại. Vui lòng thử lại.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = AppPreferences(this)
        notificationHelper = NotificationHelper(this)

        val loadedCredentials = preferences.getUthCredentials()
        uiState = uiState.copy(
            username = loadedCredentials.mssv,
            password = loadedCredentials.password,
            rememberCredentials = loadedCredentials.remember,
            autoSyncEnabled = preferences.isAutoSyncEnabled(),
            reminderEnabled = preferences.isReminderEnabled(),
            notificationMode = preferences.getNotificationMode(),
            selectedCalendarName = preferences.getSelectedCalendarName(),
            selectedCalendarId = preferences.getSelectedCalendarId(),
            googleAccountName = preferences.getGoogleAccountName() ?: "Chưa đăng nhập",
            status = preferences.getLastSyncSummary()
        )

        setContent {
            UTHSyncTaskTheme {
                UTHMainScreen(
                    uiState = uiState,
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { uiState = uiState.copy(selectedTab = it) },
                    onUsernameChanged = { uiState = uiState.copy(username = it) },
                    onPasswordChanged = { uiState = uiState.copy(password = it) },
                    onRememberChanged = { remember ->
                        uiState = uiState.copy(rememberCredentials = remember)
                        if (!remember) preferences.clearUthCredentials() else preferences.saveUthCredentials(UthCredentials(uiState.username, uiState.password, true))
                    },
                    onLoginClicked = { handleLoginTest() },
                    onGoogleLoginClicked = { ensureGoogleLogin() },
                    onManualSyncClicked = { performSync(manual = true) },
                    onLogoutGoogleClicked = { logoutGoogle() },
                    onAutoSyncChanged = { enabled ->
                        uiState = uiState.copy(autoSyncEnabled = enabled)
                        preferences.setAutoSyncEnabled(enabled)
                        if (enabled) schedulePeriodicSync() else cancelPeriodicSync()
                    },
                    onReminderEnabledChanged = { enabled ->
                        uiState = uiState.copy(reminderEnabled = enabled)
                        preferences.setReminderEnabled(enabled)
                    },
                    onNotificationModeChanged = { mode ->
                        uiState = uiState.copy(notificationMode = mode)
                        preferences.setNotificationMode(mode)
                    },
                    onCalendarSelected = { calendarId, calendarName ->
                        preferences.saveSelectedCalendar(calendarId, calendarName)
                        uiState = uiState.copy(selectedCalendarId = calendarId, selectedCalendarName = calendarName)
                    },
                    onEventAction = { assignment, action ->
                        when (action) {
                            "complete" -> markAssignmentCompleted(assignment)
                            "delete" -> deleteAssignment(assignment)
                        }
                    },
                    onDismissDialog = { uiState = uiState.copy(dialogMessage = null) }
                )
            }
        }

        if (hasCalendarPermissions()) {
            ensureGoogleLogin()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val eventKey = intent.getStringExtra("event_key")
        if (intent.action == "ACK_EVENT" && !eventKey.isNullOrBlank()) {
            preferences.acknowledgeEvent(eventKey)
            showDialog("Đã ghi nhận 'Đã biết' cho nhắc nhở này.")
        }
    }

    private fun hasCalendarPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureGoogleLogin() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            signedInAccount = account
            uiState = uiState.copy(googleAccountName = account.displayName ?: account.email ?: "Đã đăng nhập")
            lifecycleScope.launch {
                fetchCalendars(account)
            }
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GOOGLE_CALENDAR_SCOPE))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun checkGoogleLoginAndSync() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GOOGLE_CALENDAR_SCOPE))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(GOOGLE_CALENDAR_SCOPE))) {
            signedInAccount = account
            lifecycleScope.launch {
                fetchCalendars(account)
            }
        } else {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    private fun handleLoginTest() {
        val credentials = UthCredentials(uiState.username, uiState.password, uiState.rememberCredentials)
        if (credentials.mssv.isBlank() || credentials.password.isBlank()) {
            showDialog("Vui lòng nhập MSSV và mật khẩu UTH trước khi kiểm tra.")
            return
        }
        preferences.saveUthCredentials(credentials)
        uiState = uiState.copy(status = "Đang kiểm tra đăng nhập UTH...")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val scraper = UthScraper()
                val html = scraper.loginAndGetSchedule(credentials.mssv, credentials.password)
                withContext(Dispatchers.Main) {
                    if (html != null) {
                        val events = parseEventsFromHtml(html)
                        uiState = uiState.copy(status = "Đăng nhập UTH thành công", events = events)
                        showDialog("Đăng nhập UTH thành công. Bạn có thể đồng bộ ngay.")
                    } else {
                        uiState = uiState.copy(status = "Đăng nhập UTH thất bại")
                        showDialog("Đăng nhập UTH thất bại. Vui lòng kiểm tra tài khoản hoặc mật khẩu.")
                    }
                }
            }
        }
    }

    private fun performSync(manual: Boolean = false) {
        val credentials = preferences.getUthCredentials()
        if (credentials.mssv.isBlank() || credentials.password.isBlank()) {
            showDialog("Vui lòng nhập thông tin đăng nhập UTH trước khi đồng bộ.")
            return
        }
        val account = signedInAccount ?: GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            showDialog("Bạn cần đăng nhập Google trước khi đồng bộ vào Google Calendar.")
            return
        }

        uiState = uiState.copy(isLoading = true, status = "Đang đồng bộ dữ liệu...")
        lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(this@MainActivity, account.account!!, GOOGLE_CALENDAR_TOKEN_SCOPE)
                }

                val scraper = UthScraper()
                val calendarHelper = CalendarHelper(this@MainActivity)
                val html = withContext(Dispatchers.IO) { scraper.loginAndGetSchedule(credentials.mssv, credentials.password) }
                val portalHtml = withContext(Dispatchers.IO) { scraper.fetchPortalCalendarHtml() }

                if (html == null) {
                    uiState = uiState.copy(isLoading = false, status = "Không thể đăng nhập UTH")
                    showDialog("Không thể đăng nhập UTH. Hãy kiểm tra tài khoản, mật khẩu và mạng.")
                    return@launch
                }

                val assignments = parseEventsFromHtml(html)
                uiState = uiState.copy(events = assignments, isLoading = true, status = "Đang gửi vào ${uiState.selectedCalendarName}...")
                calendarHelper.syncToGoogleApi(token, assignments, uiState.selectedCalendarId)
                val calendars = calendarHelper.listCalendars(token)
                val selectedCalendar = calendars.find { it.id == uiState.selectedCalendarId } ?: calendars.firstOrNull()
                if (selectedCalendar != null) {
                    preferences.saveSelectedCalendar(selectedCalendar.id, selectedCalendar.summary)
                    uiState = uiState.copy(
                        availableCalendars = calendars,
                        selectedCalendarId = selectedCalendar.id,
                        selectedCalendarName = selectedCalendar.summary,
                        isLoading = false,
                        status = "Đồng bộ hoàn tất (${assignments.size} sự kiện)"
                    )
                } else {
                    uiState = uiState.copy(availableCalendars = calendars, isLoading = false, status = "Đồng bộ hoàn tất (${assignments.size} sự kiện)")
                }
                preferences.saveLastSyncSummary("Đồng bộ hoàn tất (${assignments.size} sự kiện)")
                notificationHelper.notifySyncCompleted("UTH SyncTask", "Đã đồng bộ ${assignments.size} sự kiện")
                if (preferences.isReminderEnabled() && preferences.getNotificationMode() != NotificationMode.OFF) {
                    notificationHelper.notifyUpcomingEvent("Nhắc lịch học", "Có ${assignments.size} lịch học mới cần kiểm tra.", "sync-summary")
                }
                if (manual) {
                    showDialog("Đồng bộ thủ công hoàn tất.")
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, status = "Đồng bộ thất bại")
                Log.e("UTH_SYNC", "Sync failed", e)
                showDialog("Đồng bộ thất bại: ${e.message}")
            }
        }
    }

    private suspend fun fetchCalendars(account: GoogleSignInAccount) {
        try {
            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(this@MainActivity, account.account!!, GOOGLE_CALENDAR_TOKEN_SCOPE)
            }
            val helper = CalendarHelper(this)
            val calendars = helper.listCalendars(token).ifEmpty { listOf(GoogleCalendarItem("primary", "Lịch chính")) }
            val selected = calendars.find { it.id == uiState.selectedCalendarId } ?: calendars.firstOrNull()
            if (selected != null) {
                preferences.saveSelectedCalendar(selected.id, selected.summary)
            }
            uiState = uiState.copy(availableCalendars = calendars, selectedCalendarId = selected?.id ?: uiState.selectedCalendarId, selectedCalendarName = selected?.summary ?: uiState.selectedCalendarName)
        } catch (e: Exception) {
            Log.e("UTH_SYNC", "Failed to fetch calendars", e)
        }
    }

    private fun logoutGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            signedInAccount = null
            preferences.clearGoogleAccount()
            uiState = uiState.copy(googleAccountName = "Chưa đăng nhập", selectedCalendarName = "Lịch chính", availableCalendars = emptyList())
            showDialog("Đã đăng xuất Google.")
        }
    }

    private fun markAssignmentCompleted(assignment: Assignment) {
        lifecycleScope.launch {
            try {
                val account = signedInAccount ?: GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                if (account == null) {
                    showDialog("Bạn cần đăng nhập Google trước khi thay đổi sự kiện.")
                    return@launch
                }
                val token = withContext(Dispatchers.IO) { GoogleAuthUtil.getToken(this@MainActivity, account.account!!, GOOGLE_CALENDAR_TOKEN_SCOPE) }
                val helper = CalendarHelper(this@MainActivity)
                val changed = helper.markEventCompleted(token, uiState.selectedCalendarId, assignment.title, assignment.date)
                if (changed) {
                    showDialog("Đã đánh dấu sự kiện hoàn thành.")
                } else {
                    showDialog("Không tìm thấy sự kiện để cập nhật.")
                }
            } catch (e: Exception) {
                Log.e("UTH_SYNC", "Failed to mark event", e)
                showDialog("Không thể cập nhật sự kiện: ${e.message}")
            }
        }
    }

    private fun deleteAssignment(assignment: Assignment) {
        lifecycleScope.launch {
            try {
                val account = signedInAccount ?: GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                if (account == null) {
                    showDialog("Bạn cần đăng nhập Google trước khi xóa sự kiện.")
                    return@launch
                }
                val token = withContext(Dispatchers.IO) { GoogleAuthUtil.getToken(this@MainActivity, account.account!!, GOOGLE_CALENDAR_TOKEN_SCOPE) }
                val helper = CalendarHelper(this@MainActivity)
                val deleted = helper.deleteMatchingEvents(token, uiState.selectedCalendarId, assignment.title, assignment.date)
                if (deleted > 0) {
                    showDialog("Đã xóa $deleted sự kiện liên quan.")
                } else {
                    showDialog("Không tìm thấy sự kiện để xóa.")
                }
            } catch (e: Exception) {
                Log.e("UTH_SYNC", "Failed to delete event", e)
                showDialog("Không thể xóa sự kiện: ${e.message}")
            }
        }
    }

    private fun schedulePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UTH_SYNC_WORK",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    private fun cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork("UTH_SYNC_WORK")
    }

    private fun showDialog(message: String) {
        uiState = uiState.copy(dialogMessage = message)
    }
}
