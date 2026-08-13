package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Ambulance
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.repository.DriverRepository
import com.example.smartambulance.data.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DriverUiState {
    object Idle : DriverUiState
    object Loading : DriverUiState
    data class Success(val message: String) : DriverUiState
    data class Error(val message: String) : DriverUiState
}

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val emergencyRepository: EmergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DriverUiState>(DriverUiState.Idle)
    val uiState: StateFlow<DriverUiState> = _uiState.asStateFlow()

    private val _ambulance = MutableStateFlow<Ambulance?>(null)
    val ambulance: StateFlow<Ambulance?> = _ambulance.asStateFlow()

    private val _activeEmergency = MutableStateFlow<Emergency?>(null)
    val activeEmergency: StateFlow<Emergency?> = _activeEmergency.asStateFlow()

    fun fetchAmbulanceProfile() {
        val driverId = SessionManager.uid ?: return
        viewModelScope.launch {
            driverRepository.getAmbulanceProfile(driverId)
                .onSuccess { profile ->
                    _ambulance.value = profile
                }
                .onFailure { exception ->
                    // If profile doesn't exist, we can register it
                    registerDefaultAmbulance()
                }
        }
    }

    private fun registerDefaultAmbulance() {
        val driverId = SessionManager.uid ?: return
        val driverName = SessionManager.name ?: "Driver"
        val driverPhone = SessionManager.phone ?: ""
        val defaultAmbulance = Ambulance(
            status = "available",
            driverId = driverId,
            driverName = driverName,
            driverPhone = driverPhone,
            latitude = 12.9716, // Default Bangalore coordinates
            longitude = 77.5946
        )
        viewModelScope.launch {
            driverRepository.updateAmbulance(driverId, defaultAmbulance)
                .onSuccess {
                    _ambulance.value = defaultAmbulance
                }
        }
    }

    fun updateAmbulanceStatus(status: String) {
        val driverId = SessionManager.uid ?: return
        val currentAmbulance = _ambulance.value ?: return
        val updated = currentAmbulance.copy(status = status)
        viewModelScope.launch {
            driverRepository.updateAmbulance(driverId, updated)
                .onSuccess {
                    _ambulance.value = updated
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to update ambulance profile")
                }
        }
    }

    fun assignToEmergency(emergencyId: String) {
        val driverId = SessionManager.uid ?: return
        val driverName = SessionManager.name ?: "Driver"
        val driverPhone = SessionManager.phone ?: ""
        viewModelScope.launch {
            _uiState.value = DriverUiState.Loading
            driverRepository.assignDriver(emergencyId, driverId, driverName, driverPhone)
                .onSuccess {
                    _uiState.value = DriverUiState.Success("Emergency assigned to you successfully")
                    fetchEmergencyDetails(emergencyId)
                    fetchAmbulanceProfile()
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to accept emergency")
                }
        }
    }

    fun updateEmergencyStatus(emergencyId: String, status: String) {
        viewModelScope.launch {
            _uiState.value = DriverUiState.Loading
            driverRepository.updateStatus(emergencyId, status)
                .onSuccess {
                    _uiState.value = DriverUiState.Success("Emergency status updated to $status")
                    if (status == "completed" || status == "cancelled") {
                        _activeEmergency.value = null
                    } else {
                        fetchEmergencyDetails(emergencyId)
                    }
                    fetchAmbulanceProfile()
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to update emergency status")
                }
        }
    }

    fun releaseEmergency(emergencyId: String) {
        val driverId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: SessionManager.uid ?: return
        viewModelScope.launch {
            _uiState.value = DriverUiState.Loading
            driverRepository.releaseEmergency(emergencyId, driverId)
                .onSuccess {
                    _uiState.value = DriverUiState.Success("Emergency released back to queue")
                    _activeEmergency.value = null
                    fetchAmbulanceProfile()
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to release emergency")
                }
        }
    }

    fun rejectEmergency(emergencyId: String) {
        val driverId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: SessionManager.uid ?: return
        viewModelScope.launch {
            driverRepository.rejectEmergency(emergencyId, driverId)
                .onSuccess {
                    _uiState.value = DriverUiState.Success("Emergency call declined")
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to decline emergency")
                }
        }
    }


    fun updateLocation(latitude: Double, longitude: Double, emergencyId: String? = null) {
        val driverId = SessionManager.uid ?: return
        viewModelScope.launch {
            driverRepository.updateLocation(driverId, latitude, longitude, emergencyId)
                .onSuccess {
                    // Update local ambulance coordinates
                    _ambulance.value = _ambulance.value?.copy(latitude = latitude, longitude = longitude)
                    if (emergencyId != null) {
                        _activeEmergency.value = _activeEmergency.value?.copy(
                            driverLatitude = latitude,
                            driverLongitude = longitude
                        )
                    }
                }
        }
    }

    fun fetchEmergencyDetails(id: String) {
        viewModelScope.launch {
            emergencyRepository.getEmergencyById(id)
                .onSuccess { emergency ->
                    _activeEmergency.value = emergency
                }
                .onFailure { exception ->
                    _uiState.value = DriverUiState.Error(exception.message ?: "Failed to fetch emergency details")
                }
        }
    }

    fun setActiveEmergency(emergency: Emergency) {
        _activeEmergency.value = emergency
    }

    fun clearActiveEmergency() {
        _activeEmergency.value = null
    }

    fun resetState() {
        _uiState.value = DriverUiState.Idle
    }
}
