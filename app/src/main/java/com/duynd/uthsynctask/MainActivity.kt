package com.duynd.uthsynctask

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.duynd.uthsynctask.notification.ReminderNotifier
import com.duynd.uthsynctask.ui.navigation.AppNavHost
import com.duynd.uthsynctask.ui.theme.UTHSyncTaskTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val _openPortalLogin = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPortalLoginExtra(intent)

        setContent {
            val openPortalLogin by _openPortalLogin.collectAsState()

            UTHSyncTaskTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        openPortalLoginOnStart = openPortalLogin,
                        onPortalLoginConsumed = { _openPortalLogin.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkPortalLoginExtra(intent)
    }

    private fun checkPortalLoginExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderNotifier.EXTRA_OPEN_PORTAL_LOGIN, false) == true) {
            _openPortalLogin.value = true
        }
    }
}
