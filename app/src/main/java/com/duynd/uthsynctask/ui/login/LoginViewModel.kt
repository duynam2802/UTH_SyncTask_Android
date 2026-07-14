package com.duynd.uthsynctask.ui.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duynd.uthsynctask.data.local.SecureCredentialStore
import com.duynd.uthsynctask.data.model.LoginErrorKind
import com.duynd.uthsynctask.data.model.LoginResult
import com.duynd.uthsynctask.data.model.LoginUiState
import com.duynd.uthsynctask.data.model.UthCredentials
import com.duynd.uthsynctask.data.remote.UthAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = SecureCredentialStore(application)
    private val authRepository = UthAuthRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    var mssvInput by mutableStateOf("")
        private set
    var passwordInput by mutableStateOf("")
        private set
    var rememberMe by mutableStateOf(true)
        private set

    init {
        // Tự động điền + vào thẳng app nếu đã có tài khoản được ghi nhớ.
        // KHÔNG gọi đăng nhập UTH ở đây để tiết kiệm tài nguyên, khi nào sync mới thực sự đăng nhập.
        viewModelScope.launch {
            val saved = credentialStore.getSavedCredentials()
            if (saved != null) {
                mssvInput = saved.mssv
                passwordInput = saved.password
                _uiState.value = LoginUiState.Success(saved.mssv)
            }
        }
    }

    fun onMssvChange(value: String) {
        mssvInput = value
    }

    fun onPasswordChange(value: String) {
        passwordInput = value
    }

    fun onRememberMeChange(value: Boolean) {
        rememberMe = value
    }

    fun onDialogDismissed() {
        _uiState.value = LoginUiState.Idle
    }

    fun login() {
        val mssv = mssvInput.trim()
        val password = passwordInput

        if (mssv.isEmpty() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error(
                LoginErrorKind.UNKNOWN,
                "Vui lòng nhập đầy đủ mã số sinh viên và mật khẩu."
            )
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.login(mssv, password)) {
                is LoginResult.Success, is LoginResult.SuccessWithToken -> {
                    if (rememberMe) {
                        credentialStore.saveCredentials(UthCredentials(mssv, password))
                    } else {
                        credentialStore.clearCredentials()
                    }
                    _uiState.value = LoginUiState.Success(mssv)
                }
                is LoginResult.InvalidCredentials -> {
                    _uiState.value = LoginUiState.Error(LoginErrorKind.WRONG_CREDENTIALS, result.message)
                }
                is LoginResult.NetworkError -> {
                    _uiState.value = LoginUiState.Error(LoginErrorKind.NETWORK, result.message)
                }
                is LoginResult.UnknownError -> {
                    _uiState.value = LoginUiState.Error(LoginErrorKind.UNKNOWN, result.message)
                }
            }
        }
    }
}
