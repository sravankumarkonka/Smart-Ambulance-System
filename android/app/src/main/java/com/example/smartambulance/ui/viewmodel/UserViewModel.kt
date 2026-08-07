package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.HospitalRecommendation
import com.example.smartambulance.data.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UserUiState {
    object Idle : UserUiState
    object Loading : UserUiState
    data class Success(val message: String) : UserUiState
    data class Error(val message: String) : UserUiState
}

@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: EmergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Idle)
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<Emergency>>(emptyList())
    val history: StateFlow<List<Emergency>> = _history.asStateFlow()

    private val _activeEmergency = MutableStateFlow<Emergency?>(null)
    val activeEmergency: StateFlow<Emergency?> = _activeEmergency.asStateFlow()

    private val _hospitalRecommendation = MutableStateFlow<HospitalRecommendation?>(null)
    val hospitalRecommendation: StateFlow<HospitalRecommendation?> = _hospitalRecommendation.asStateFlow()

    fun reportEmergency(
        patientName: String,
        emergencyType: String,
        description: String,
        latitude: Double,
        longitude: Double,
        severityLevel: String,
        hospitalName: String? = null,
        hospitalLatitude: Double? = null,
        hospitalLongitude: Double? = null
    ) {
        if (patientName.isBlank() || description.isBlank()) {
            _uiState.value = UserUiState.Error("Patient Name and Description cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            repository.createEmergency(
                patientName,
                emergencyType,
                description,
                latitude,
                longitude,
                severityLevel,
                hospitalName,
                hospitalLatitude,
                hospitalLongitude
            ).onSuccess { response ->
                _uiState.value = UserUiState.Success("Emergency reported successfully with ID: ${response.id}")
                // Retrieve the newly created emergency to track it
                fetchActiveEmergency(response.id)
            }.onFailure { exception ->
                _uiState.value = UserUiState.Error(exception.message ?: "Failed to report emergency")
            }
        }
    }

    fun fetchHistory() {
        val userId = SessionManager.uid ?: return
        viewModelScope.launch {
            repository.getEmergencyHistory(userId)
                .onSuccess { list ->
                    _history.value = list
                }
                .onFailure { exception ->
                    _uiState.value = UserUiState.Error(exception.message ?: "Failed to load history")
                }
        }
    }

    fun fetchActiveEmergency(id: String) {
        viewModelScope.launch {
            repository.getEmergencyById(id)
                .onSuccess { emergency ->
                    _activeEmergency.value = emergency
                }
                .onFailure { exception ->
                    _uiState.value = UserUiState.Error(exception.message ?: "Failed to fetch emergency details")
                }
        }
    }

    fun cancelActiveEmergency(id: String) {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            repository.cancelEmergency(id)
                .onSuccess {
                    _uiState.value = UserUiState.Success("Emergency cancelled successfully")
                    _activeEmergency.value = null
                    fetchHistory()
                }
                .onFailure { exception ->
                    _uiState.value = UserUiState.Error(exception.message ?: "Failed to cancel emergency")
                }
        }
    }

    fun recommendHospital(latitude: Double, longitude: Double, severityLevel: String) {
        viewModelScope.launch {
            repository.recommendHospital(latitude, longitude, severityLevel)
                .onSuccess { recommendation ->
                    _hospitalRecommendation.value = recommendation
                }
                .onFailure { exception ->
                    _uiState.value = UserUiState.Error(exception.message ?: "Failed to get hospital recommendations")
                }
        }
    }

    fun uploadAccidentImage(id: String, imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            repository.uploadEmergencyImage(id, imageBytes)
                .onSuccess { url ->
                    _uiState.value = UserUiState.Success("Image uploaded successfully!")
                    // Reload active emergency to get new image URL
                    fetchActiveEmergency(id)
                }
                .onFailure { exception ->
                    _uiState.value = UserUiState.Error(exception.message ?: "Failed to upload image")
                }
        }
    }

    fun resetState() {
        _uiState.value = UserUiState.Idle
    }
}
