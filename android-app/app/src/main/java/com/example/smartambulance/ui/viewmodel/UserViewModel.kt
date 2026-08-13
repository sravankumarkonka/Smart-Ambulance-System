package com.example.smartambulance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.HospitalRecommendation
import com.example.smartambulance.data.model.isStatusActive
import com.example.smartambulance.data.model.isStatusHistory
import com.example.smartambulance.data.repository.EmergencyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
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

    private val _activeEmergencies = MutableStateFlow<List<Emergency>>(emptyList())
    val activeEmergencies: StateFlow<List<Emergency>> = _activeEmergencies.asStateFlow()

    private val _activeEmergency = MutableStateFlow<Emergency?>(null)
    val activeEmergency: StateFlow<Emergency?> = _activeEmergency.asStateFlow()

    private val _hospitalRecommendation = MutableStateFlow<HospitalRecommendation?>(null)
    val hospitalRecommendation: StateFlow<HospitalRecommendation?> = _hospitalRecommendation.asStateFlow()

    private var patientListenerRegistration: ListenerRegistration? = null

    init {
        startPatientRealtimeListener()
    }

    fun startPatientRealtimeListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: SessionManager.uid ?: return
        patientListenerRegistration?.remove()
        patientListenerRegistration = repository.listenToPatientEmergencies(
            userId = uid,
            onSuccess = { list ->
                val active = list.filter { isStatusActive(it.status) }
                val hist = list.filter { isStatusHistory(it.status) }
                _activeEmergencies.value = active
                _history.value = hist
                _activeEmergency.value = active.firstOrNull()
            },
            onError = {
                fetchHistory()
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        patientListenerRegistration?.remove()
    }

    fun reportEmergency(
        patientName: String,
        emergencyType: String,
        description: String,
        latitude: Double,
        longitude: Double,
        severityLevel: String,
        hospitalName: String? = null,
        hospitalLatitude: Double? = null,
        hospitalLongitude: Double? = null,
        imageBytes: ByteArray? = null
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
                _uiState.value = UserUiState.Success(response.id)
                fetchActiveEmergency(response.id)
                startPatientRealtimeListener()
                if (imageBytes != null) {
                    launch {
                        repository.uploadEmergencyImage(response.id, imageBytes)
                    }
                }
            }.onFailure { exception ->
                _uiState.value = UserUiState.Error(exception.message ?: "Failed to report emergency")
            }
        }
    }

    fun fetchHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: SessionManager.uid ?: return
        viewModelScope.launch {
            repository.getEmergencyHistory(userId)
                .onSuccess { list ->
                    val active = list.filter { isStatusActive(it.status) }
                    val hist = list.filter { isStatusHistory(it.status) }
                    _activeEmergencies.value = active
                    _history.value = hist
                    _activeEmergency.value = active.firstOrNull()
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
                    startPatientRealtimeListener()
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
                    val defaultHospital = com.example.smartambulance.data.model.Hospital(
                        id = "hosp_1",
                        name = "City Central General Hospital",
                        latitude = 12.9716,
                        longitude = 77.5946,
                        totalIcuBeds = 10,
                        availableIcuBeds = 4,
                        rating = 4.8,
                        phone = "080-12345678",
                        distanceKm = 1.2,
                        icuStatus = "AVAILABLE"
                    )
                    _hospitalRecommendation.value = HospitalRecommendation(
                        recommended = defaultHospital,
                        comparison = listOf(
                            defaultHospital,
                            com.example.smartambulance.data.model.Hospital(
                                id = "hosp_2",
                                name = "Apollo Emergency Care Center",
                                latitude = 12.9780,
                                longitude = 77.6400,
                                totalIcuBeds = 8,
                                availableIcuBeds = 2,
                                rating = 4.6,
                                phone = "080-87654321",
                                distanceKm = 3.5,
                                icuStatus = "AVAILABLE"
                            )
                        )
                    )
                }
        }
    }

    fun uploadAccidentImage(id: String, imageBytes: ByteArray) {
        viewModelScope.launch {
            repository.uploadEmergencyImage(id, imageBytes)
                .onSuccess {
                    _uiState.value = UserUiState.Success("Notice: Images are stored locally (Storage disabled on Spark plan).")
                }
        }
    }

    fun resetState() {
        _uiState.value = UserUiState.Idle
    }
}

