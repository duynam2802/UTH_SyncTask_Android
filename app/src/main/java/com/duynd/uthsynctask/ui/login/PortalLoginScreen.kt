package com.duynd.uthsynctask.ui.login

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalLoginScreen(
    credentialStore: SecureCredentialStore,
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loginStatusText by remember { mutableStateOf("Đang mở trang đăng nhập Portal...") }
    var isSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng nhập Portal UTH") },
                navigationIcon = {
                    IconButton(onClick = onLoginSuccess) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!isSuccess) {
                                    loginStatusText = "Vui lòng nhập tài khoản và mật khẩu trên trang Portal..."
                                }
                                val js = """
                                    (function() {
                                        try {
                                            let keys = [];
                                            for (let i = 0; i < localStorage.length; i++) {
                                                keys.push(localStorage.key(i));
                                            }
                                        } catch(e) {}

                                        // Intercept localStorage.setItem
                                        const originalSetItem = localStorage.setItem;
                                        localStorage.setItem = function(key, value) {
                                            originalSetItem.apply(this, arguments);
                                            if (value && typeof value === 'string' && value.length > 20) {
                                                if (value.startsWith('eyJ') || value.includes('.')) {
                                                    PortalBridge.onTokenFound(value);
                                                }
                                            }
                                        };

                                        // Intercept fetch
                                        const originalFetch = window.fetch;
                                        window.fetch = async function(...args) {
                                            const url = args[0];
                                            const response = await originalFetch.apply(this, args);
                                            try {
                                                const clone = response.clone();
                                                const text = await clone.text();
                                                
                                                // Try parse JSON
                                                try {
                                                    let data = JSON.parse(text);
                                                    if (data) {
                                                        let token = data.token || data.accessToken || data.access_token || data.body;
                                                        if (token && typeof token === 'string' && token.length > 20) {
                                                            PortalBridge.onTokenFound(token);
                                                        }
                                                    }
                                                } catch(err) {}
                                            } catch (e) {}
                                            return response;
                                        };

                                        function checkStorage() {
                                            for (let i = 0; i < localStorage.length; i++) {
                                                let k = localStorage.key(i);
                                                let val = localStorage.getItem(k);
                                                if (val && typeof val === 'string' && val.length > 20) {
                                                    if (val.startsWith('eyJ')) {
                                                        PortalBridge.onTokenFound(val);
                                                        return true;
                                                    }
                                                    try {
                                                        let obj = JSON.parse(val);
                                                        let t = obj.token || obj.accessToken || obj.access_token;
                                                        if (t && typeof t === 'string' && t.length > 20) {
                                                            PortalBridge.onTokenFound(t);
                                                            return true;
                                                        }
                                                    } catch (e) {}
                                                }
                                            }
                                            return false;
                                        }

                                        setInterval(checkStorage, 1000);
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onTokenFound(token: String) {
                                scope.launch {
                                    if (!isSuccess && token.isNotBlank() && token.startsWith("eyJ")) {
                                        isSuccess = true
                                        loginStatusText = "Đăng nhập Portal thành công! Đã lấy token."
                                        Log.d("PortalLogin", "Token captured: $token")
                                        credentialStore.savePortalToken(token)
                                        delay(1500L)
                                        onLoginSuccess()
                                    }
                                }
                            }
                        }, "PortalBridge")

                        loadUrl("https://portal.ut.edu.vn/")
                    }
                }
            )

            // Small line displaying progress at the bottom of the screen
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isSuccess) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = loginStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
