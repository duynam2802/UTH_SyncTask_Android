package com.duynd.uthsynctask.ui.login

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalLoginScreen(
    credentialStore: SecureCredentialStore,
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Mỗi khi load xong trang, ta tiêm code JS để theo dõi localStorage
                        // Khi nào tìm thấy token (chuỗi bắt đầu bằng ey...) thì báo về cho Android
                        val js = """
                            (function() {
                                function findToken() {
                                    for (let i = 0; i < localStorage.length; i++) {
                                        let key = localStorage.key(i);
                                        let val = localStorage.getItem(key);
                                        if (val && val.startsWith('eyJ')) {
                                            PortalBridge.onTokenFound(val);
                                            return true;
                                        }
                                    }
                                    return false;
                                }
                                // Chạy ngay và thử lại sau mỗi 2 giây
                                if (!findToken()) {
                                    setInterval(findToken, 2000);
                                }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onTokenFound(token: String) {
                        scope.launch {
                            credentialStore.savePortalToken(token)
                            onLoginSuccess()
                        }
                    }
                }, "PortalBridge")

                loadUrl("https://portal.ut.edu.vn/login")
            }
        }
    )
}
