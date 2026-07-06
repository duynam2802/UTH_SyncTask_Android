package com.duynd.uthsynctask.data.model

/**
 * Thông tin đăng nhập UTH (dùng chung cho Portal / Courses / thnn).
 */
data class UthCredentials(
    val mssv: String,
    val password: String
)

/**
 * Kết quả trả về từ [com.duynd.uthsynctask.data.remote.UthAuthRepository].
 * Tách riêng "sai tài khoản/mật khẩu" và "lỗi mạng/hệ thống" để UI hiển thị
 * thông báo phù hợp cho từng trường hợp, đúng yêu cầu hiển thị lỗi rõ ràng.
 */
sealed class LoginResult {
    data object Success : LoginResult()
    data class InvalidCredentials(val message: String) : LoginResult()
    data class NetworkError(val message: String) : LoginResult()
    data class UnknownError(val message: String) : LoginResult()
}

/**
 * Trạng thái hiển thị của màn hình đăng nhập.
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object CheckingSavedAccount : LoginUiState
    data object Loading : LoginUiState
    data class Success(val mssv: String) : LoginUiState
    data class Error(val kind: LoginErrorKind, val message: String) : LoginUiState
}

enum class LoginErrorKind {
    WRONG_CREDENTIALS,
    NETWORK,
    UNKNOWN
}
