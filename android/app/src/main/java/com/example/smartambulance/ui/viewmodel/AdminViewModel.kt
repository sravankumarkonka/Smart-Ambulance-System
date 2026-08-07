package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.model.AdminStats
import com.example.smartambulance.data.model.Ambulance
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

    fun fetchStats() {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            repository.getStats()
                .onSuccess { data ->
                    _stats.value = data
                    _uiState.value = AdminUiState.Success("Stats loaded successfully")
                }
                .onFailure { exception ->
                    _uiState.value = AdminUiState.Error(exception.message ?: "Failed to fetch stats")
                }
        }
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

    fun resetState() {
        _uiState.value = AdminUiState.Idle
    }
}
