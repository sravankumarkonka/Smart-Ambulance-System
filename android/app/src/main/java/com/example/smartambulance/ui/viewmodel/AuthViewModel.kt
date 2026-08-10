package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.User
import com.example.smartambulance.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: User, val role: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
    object PasswordResetSent : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.login(email, password)
                .onSuccess { response ->
                    _uiState.value = AuthUiState.Success(response.profile, response.profile.role)
                }
                .onFailure { exception ->
                    _uiState.value = AuthUiState.Error(formatErrorMessage(exception, "Login failed"))
                }
        }
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String
    ) {
        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.register(name, email, phone, password, role)
                .onSuccess { response ->
                    _uiState.value = AuthUiState.Success(response.profile, response.profile.role)
                }
                .onFailure { exception ->
                    _uiState.value = AuthUiState.Error(formatErrorMessage(exception, "Registration failed"))
                }
        }
    }

    private fun formatErrorMessage(exception: Throwable, defaultMessage: String): String {
        val msg = exception.message ?: ""
        return when {
            exception is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) ->
                "Server is waking up (Render cold-start). Please wait a few seconds and try again."
            exception is java.net.ConnectException || msg.contains("Failed to connect", ignoreCase = true) ->
                "Unable to connect to server. Please check your internet connection."
            msg.isNotBlank() -> msg
            else -> defaultMessage
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Email cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    _uiState.value = AuthUiState.PasswordResetSent
                }
                .onFailure { exception ->
                    _uiState.value = AuthUiState.Error(exception.message ?: "Failed to send password reset email")
                }
        }
    }

    fun logout() {
        SessionManager.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
