package com.duynd.uthsynctask.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Mã hoá/giải mã dữ liệu nhạy cảm (mật khẩu UTH) bằng khoá AES-256-GCM lưu trong
 * Android Keystore - khoá KHÔNG BAO GIỜ rời khỏi phần cứng bảo mật của máy,
 * kể cả app cũng không đọc trực tiếp được, chỉ dùng để encrypt/decrypt.
 *
 * Dùng trực tiếp Android Keystore thay vì androidx.security-crypto/EncryptedSharedPreferences
 * vì thư viện đó đã bị Google deprecate (khuyến nghị chính thức: dùng thẳng Keystore + DataStore).
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "uth_synctask_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12 // Chuẩn GCM dùng IV 12 byte

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Không yêu cầu xác thực mỗi lần dùng vì SyncWorker cần chạy nền mỗi 1h
            // kể cả khi máy đang khoá màn hình.
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Trả về chuỗi Base64 gồm IV nối với dữ liệu đã mã hoá, tiện lưu chung 1 giá trị trong DataStore. */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Trả về null nếu dữ liệu hỏng hoặc khoá không còn hợp lệ (VD: khôi phục backup sang máy khác). */
    fun decrypt(base64Combined: String): String? {
        return try {
            val combined = Base64.decode(base64Combined, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherBytes = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
