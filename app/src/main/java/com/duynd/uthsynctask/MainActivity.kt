package com.duynd.uthsynctask

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var syncStatus by mutableStateOf("Đang chờ...")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CALENDAR] == true &&
            permissions[Manifest.permission.WRITE_CALENDAR] == true
        ) {
            checkGoogleLoginAndSync()
        } else {
            Log.e("UTH_SYNC", "Calendar permissions denied")
            syncStatus = "❌ Cần quyền truy cập lịch"
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                startSyncProcess(account)
            }
        } catch (e: Exception) {
            Log.e("UTH_SYNC", "Google Sign-In failed: ${e.message}")
            syncStatus = "❌ Đăng nhập Google thất bại"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UTHSyncTaskTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = syncStatus,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (hasCalendarPermissions()) {
            checkGoogleLoginAndSync()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    private fun hasCalendarPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkGoogleLoginAndSync() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val calendarScope = "https://www.googleapis.com/auth/calendar.events"
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(calendarScope))
            .build()
        val client = GoogleSignIn.getClient(this, gso)

        if (account != null && GoogleSignIn.hasPermissions(account, Scope(calendarScope))) {
            startSyncProcess(account)
        } else {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    private fun startSyncProcess(account: GoogleSignInAccount) {
        syncStatus = "🔄 Đang lấy dữ liệu từ UTH..."
        
        // Lên lịch chạy tự động mỗi 1 giờ
        account.email?.let { schedulePeriodicSync(it) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Lấy Access Token cho Google Calendar API
                val scope = "oauth2:https://www.googleapis.com/auth/calendar.events"
                val token = GoogleAuthUtil.getToken(this@MainActivity, account.account!!, scope)

                val scraper = UthScraper()
                val calendarHelper = CalendarHelper(this@MainActivity)

                // Đăng nhập và lấy HTML từ Courses UTH
                val resultHtml = scraper.loginAndGetSchedule("083205012971", "0964911614@UTH")

                if (resultHtml != null) {
                    val tasks = parseEventsFromHtml(resultHtml)
                    
                    withContext(Dispatchers.Main) {
                        syncStatus = "🚀 Đang đồng bộ ${tasks.size} nhiệm vụ..."
                    }

                    // Đồng bộ trực tiếp qua REST API
                    calendarHelper.syncToGoogleApi(token, tasks)
                    
                    withContext(Dispatchers.Main) {
                        syncStatus = "✅ Đồng bộ hoàn tất!"
                        Toast.makeText(this@MainActivity, "Đã đồng bộ xong!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        syncStatus = "❌ Lỗi đăng nhập Courses UTH"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    syncStatus = "❌ Lỗi: ${e.message}"
                }
                Log.e("UTH_SYNC", "Sync error", e)
            }
        }
    }

    private fun schedulePeriodicSync(googleEmail: String) {
        // Lưu thông tin cần thiết cho Worker
        val sharedPrefs = getSharedPreferences("UTH_PREFS", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("google_email", googleEmail)
            putString("mssv", "083205012971")
            putString("pass", "0964911614@UTH")
            apply()
        }

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UTH_SYNC_WORK",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.d("UTH_SYNC", "Scheduled periodic sync every 1 hour")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UTHSyncTaskTheme {
        Greeting("Android")
    }
}
