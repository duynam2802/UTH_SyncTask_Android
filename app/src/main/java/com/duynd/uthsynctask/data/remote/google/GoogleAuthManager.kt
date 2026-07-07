package com.duynd.uthsynctask.data.remote.google

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

sealed class AuthorizationOutcome {
    data class Granted(val accessToken: String) : AuthorizationOutcome()
    data class NeedsConsent(val intentSender: IntentSender) : AuthorizationOutcome()
    data class Failed(val message: String) : AuthorizationOutcome()
}

/**
 * Quản lý uỷ quyền truy cập Google Calendar bằng AuthorizationClient (Google Identity Services),
 * thay cho GoogleSignIn cũ (com.google.android.gms.auth.api.signin) đã bị Google deprecate và
 * sẽ bị gỡ khỏi Play Services Auth SDK trong tương lai.
 *
 * Cách hoạt động:
 *  - Gọi [authorize]: nếu người dùng đã từng đồng ý trước đó, trả về accessToken NGAY, không cần
 *    thao tác gì thêm - kể cả khi gọi từ nền (SyncWorker chạy ngầm hàng giờ, không có Activity).
 *  - Nếu cần xin quyền lần đầu (hoặc quyền đã bị thu hồi), kết quả sẽ là [AuthorizationOutcome.NeedsConsent]
 *    kèm theo [IntentSender] - màn hình Cài đặt cần khởi chạy nó qua
 *    `rememberLauncherForActivityResult(StartIntentSenderForResult())` rồi gọi [resultFromIntent]
 *    với Intent trả về để lấy accessToken thật sự.
 */
class GoogleAuthManager(context: Context) {

    private val appContext = context.applicationContext

    // Scope đầy đủ "calendar" (thay vì chỉ "calendar.events") vì app cần liệt kê danh sách lịch
    // (calendarList.list) để cho người dùng chọn lịch lưu deadline, không chỉ đọc/ghi sự kiện.
    private val requestedScopes = listOf(Scope("https://www.googleapis.com/auth/calendar"))

    private fun buildRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()

    suspend fun authorize(): AuthorizationOutcome {
        return try {
            val result = Identity.getAuthorizationClient(appContext).authorize(buildRequest()).await()
            toOutcome(result)
        } catch (e: Exception) {
            AuthorizationOutcome.Failed(e.message ?: "Không thể kết nối tới Google Calendar.")
        }
    }

    /** Gọi trong callback của ActivityResultLauncher sau khi người dùng đồng ý cấp quyền. */
    fun resultFromIntent(data: Intent?): AuthorizationOutcome {
        return try {
            val result = Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
            toOutcome(result)
        } catch (e: Exception) {
            AuthorizationOutcome.Failed(e.message ?: "Không xác nhận được quyền truy cập Google Calendar.")
        }
    }

    /** Ngắt kết nối Google Calendar (đăng xuất). */
    suspend fun revokeAccess(): Result<Unit> = try {
        Identity.getSignInClient(appContext).signOut().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun toOutcome(result: AuthorizationResult): AuthorizationOutcome {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            return if (pendingIntent != null) {
                AuthorizationOutcome.NeedsConsent(pendingIntent.intentSender)
            } else {
                AuthorizationOutcome.Failed("Không thể mở màn hình xin quyền Google Calendar.")
            }
        }
        val token = result.accessToken
        return if (token != null) {
            AuthorizationOutcome.Granted(token)
        } else {
            AuthorizationOutcome.Failed("Google không trả về access token hợp lệ.")
        }
    }
}
