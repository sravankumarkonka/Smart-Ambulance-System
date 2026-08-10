package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.model.AdminStats
import com.example.smartambulance.data.model.Ambulance
import com.example.smartambulance.data.model.AuditLog
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.User
import com.example.smartambulance.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdminUiState {
    object Idle : AdminUiState
    object Loading : AdminUiState
    data class Success(val message: String) : AdminUiState
    data class Error(val message: String) : AdminUiState
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Idle)
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _stats = MutableStateFlow<AdminStats?>(null)
    val stats: StateFlow<AdminStats?> = _stats.asStateFlow()

    private val _ambulances = MutableStateFlow<List<Ambulance>>(emptyList())
    val ambulances: StateFlow<List<Ambulance>> = _ambulances.asStateFlow()

    private val _emergencies = MutableStateFlow<List<Emergency>>(emptyList())
    val emergencies: StateFlow<List<Emergency>> = _emergencies.asStateFlow()

    private val _pendingDrivers = MutableStateFlow<List<User>>(emptyList())
    val pendingDrivers: StateFlow<List<User>> = _pendingDrivers.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    fun fetchAllAdminData() {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading

            repository.getStats().onSuccess { _stats.value = it }
            repository.getAllAmbulances().onSuccess { _ambulances.value = it }
            repository.getAllEmergencies().onSuccess { _emergencies.value = it }
            repository.getPendingDrivers().onSuccess { _pendingDrivers.value = it }
            repository.getAuditLogs().onSuccess { _auditLogs.value = it }

            _uiState.value = AdminUiState.Idle
        }
    }

    fun fetchStats() {
        fetchAllAdminData()
    }

    fun fetchAmbulances() {
        viewModelScope.launch {
            repository.getAllAmbulances()
                .onSuccess { list ->
                    _ambulances.value = list
                }
                .onFailure { exception ->
                    _uiState.value = AdminUiState.Error(exception.message ?: "Failed to fetch ambulance list")
                }
        }
    }

    fun approveDriver(uid: String) {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            repository.approveDriver(uid)
                .onSuccess { msg ->
                    _uiState.value = AdminUiState.Success(msg)
                    fetchAllAdminData()
                }
                .onFailure { err ->
                    _uiState.value = AdminUiState.Error(err.message ?: "Failed to approve driver")
                }
        }
    }

    fun rejectDriver(uid: String) {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            repository.rejectDriver(uid)
                .onSuccess { msg ->
                    _uiState.value = AdminUiState.Success(msg)
                    fetchAllAdminData()
                }
                .onFailure { err ->
                    _uiState.value = AdminUiState.Error(err.message ?: "Failed to reject driver")
                }
        }
    }

    fun resetState() {
        _uiState.value = AdminUiState.Idle
    }
}
