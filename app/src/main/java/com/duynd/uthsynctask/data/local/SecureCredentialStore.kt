package com.duynd.uthsynctask.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duynd.uthsynctask.data.model.UthCredentials
import kotlinx.coroutines.flow.first

private val Context.credentialDataStore by preferencesDataStore(name = "uth_secure_credentials")

/**
 * Lưu tài khoản/mật khẩu UTH đã MÃ HOÁ (AES-256-GCM qua Android Keystore, xem [CryptoManager])
 * trong Jetpack DataStore - thay cho việc lưu chữ thường/hardcode trong source như code cũ.
 *
 * Đây là nơi DUY NHẤT trong app được phép đọc/ghi mật khẩu người dùng.
 * Tất cả hàm đều là suspend vì DataStore làm I/O bất đồng bộ (không chặn UI thread).
 */
class SecureCredentialStore(private val context: Context) {

    private object Keys {
        val MSSV = stringPreferencesKey("enc_mssv")
        val PASSWORD = stringPreferencesKey("enc_password")
        val REMEMBER = booleanPreferencesKey("remember")
        val PORTAL_TOKEN = stringPreferencesKey("portal_token")
    }

    suspend fun saveCredentials(credentials: UthCredentials) {
        context.credentialDataStore.edit { prefs ->
            prefs[Keys.MSSV] = CryptoManager.encrypt(credentials.mssv)
            prefs[Keys.PASSWORD] = CryptoManager.encrypt(credentials.password)
            prefs[Keys.REMEMBER] = true
        }
    }

    suspend fun savePortalToken(token: String) {
        context.credentialDataStore.edit { prefs ->
            prefs[Keys.PORTAL_TOKEN] = CryptoManager.encrypt(token)
        }
    }

    suspend fun getPortalToken(): String? {
        val prefs = context.credentialDataStore.data.first()
        val encToken = prefs[Keys.PORTAL_TOKEN] ?: return null
        return CryptoManager.decrypt(encToken)
    }

    suspend fun getSavedCredentials(): UthCredentials? {
        val prefs = context.credentialDataStore.data.first()
        if (prefs[Keys.REMEMBER] != true) return null
        val encMssv = prefs[Keys.MSSV] ?: return null
        val encPassword = prefs[Keys.PASSWORD] ?: return null
        val mssv = CryptoManager.decrypt(encMssv) ?: return null
        val password = CryptoManager.decrypt(encPassword) ?: return null
        return UthCredentials(mssv, password)
    }

    suspend fun hasSavedCredentials(): Boolean = getSavedCredentials() != null

    /** Xoá tài khoản UTH đã lưu (dùng khi người dùng bấm "Đăng xuất"). */
    suspend fun clearCredentials() {
        context.credentialDataStore.edit { prefs ->
            prefs.remove(Keys.MSSV)
            prefs.remove(Keys.PASSWORD)
            prefs[Keys.REMEMBER] = false
        }
    }
}
