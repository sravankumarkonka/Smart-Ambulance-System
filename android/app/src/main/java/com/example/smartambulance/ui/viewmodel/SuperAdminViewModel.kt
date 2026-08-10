package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.model.AuditLog
import com.example.smartambulance.data.model.User
import com.example.smartambulance.data.repository.SuperAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SuperAdminUiState {
    object Idle : SuperAdminUiState
    object Loading : SuperAdminUiState
    data class Success(val message: String) : SuperAdminUiState
    data class Error(val message: String) : SuperAdminUiState
}

@HiltViewModel
class SuperAdminViewModel @Inject constructor(
    private val repository: SuperAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SuperAdminUiState>(SuperAdminUiState.Idle)
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    private val _pendingAdmins = MutableStateFlow<List<User>>(emptyList())
    val pendingAdmins: StateFlow<List<User>> = _pendingAdmins.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    fun fetchAllData() {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading

            val pendingRes = repository.getPendingAdmins()
            pendingRes.onSuccess { _pendingAdmins.value = it }

            val usersRes = repository.getAllUsers()
            usersRes.onSuccess { _allUsers.value = it }

            val logsRes = repository.getAuditLogs()
            logsRes.onSuccess { _auditLogs.value = it }

            _uiState.value = SuperAdminUiState.Idle
        }
    }

    fun approveAdmin(uid: String) {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading
            repository.approveAdmin(uid)
                .onSuccess { msg ->
                    _uiState.value = SuperAdminUiState.Success(msg)
                    fetchAllData()
                }
                .onFailure { err ->
                    _uiState.value = SuperAdminUiState.Error(err.message ?: "Failed to approve admin")
                }
        }
    }

    fun rejectAdmin(uid: String) {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading
            repository.rejectAdmin(uid)
                .onSuccess { msg ->
                    _uiState.value = SuperAdminUiState.Success(msg)
                    fetchAllData()
                }
                .onFailure { err ->
                    _uiState.value = SuperAdminUiState.Error(err.message ?: "Failed to reject admin")
                }
        }
    }

    fun suspendUser(uid: String, role: String) {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading
            repository.suspendUser(uid, role)
                .onSuccess { msg ->
                    _uiState.value = SuperAdminUiState.Success(msg)
                    fetchAllData()
                }
                .onFailure { err ->
                    _uiState.value = SuperAdminUiState.Error(err.message ?: "Failed to suspend user")
                }
        }
    }

    fun activateUser(uid: String, role: String) {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading
            repository.activateUser(uid, role)
                .onSuccess { msg ->
                    _uiState.value = SuperAdminUiState.Success(msg)
                    fetchAllData()
                }
                .onFailure { err ->
                    _uiState.value = SuperAdminUiState.Error(err.message ?: "Failed to activate user")
                }
        }
    }

    fun deleteUser(uid: String, role: String) {
        viewModelScope.launch {
            _uiState.value = SuperAdminUiState.Loading
            repository.deleteUser(uid, role)
                .onSuccess { msg ->
                    _uiState.value = SuperAdminUiState.Success(msg)
                    fetchAllData()
                }
                .onFailure { err ->
                    _uiState.value = SuperAdminUiState.Error(err.message ?: "Failed to delete user")
                }
        }
    }

    fun resetState() {
        _uiState.value = SuperAdminUiState.Idle
    }
}
