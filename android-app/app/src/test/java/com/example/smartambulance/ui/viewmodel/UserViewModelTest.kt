package com.example.smartambulance.ui.viewmodel

import com.example.smartambulance.data.model.CreateEmergencyResponse
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.HospitalRecommendation
import com.example.smartambulance.data.repository.EmergencyRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: EmergencyRepository = mockk()
    private lateinit var viewModel: UserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = UserViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reportEmergency_success_updatesUiStateToSuccess() = runTest {
        val response = CreateEmergencyResponse(id = "emergency123")
        coEvery {
            repository.createEmergency(
                patientName = "John Doe",
                emergencyType = "Cardiac Arrest",
                description = "Severe chest pain",
                latitude = 12.9716,
                longitude = 77.5946,
                severityLevel = "high",
                hospitalName = null,
                hospitalLatitude = null,
                hospitalLongitude = null
            )
        } returns Result.success(response)

        val dummyEmergency = Emergency(
            id = "emergency123",
            userId = "user123",
            patientName = "John Doe",
            status = "pending",
            emergencyType = "Cardiac Arrest",
            description = "Severe chest pain",
            latitude = 12.9716,
            longitude = 77.5946,
            severityLevel = "high"
        )
        coEvery { repository.getEmergencyById("emergency123") } returns Result.success(dummyEmergency)

        viewModel.reportEmergency(
            patientName = "John Doe",
            emergencyType = "Cardiac Arrest",
            description = "Severe chest pain",
            latitude = 12.9716,
            longitude = 77.5946,
            severityLevel = "high"
        )
        testScheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is UserUiState.Success)
        assertEquals("Emergency reported successfully with ID: emergency123", (currentState as UserUiState.Success).message)
        assertEquals(dummyEmergency, viewModel.activeEmergency.value)
    }

    @Test
    fun cancelActiveEmergency_success_updatesUiStateToSuccessAndClearsActive() = runTest {
        coEvery { repository.cancelEmergency("emergency123") } returns Result.success(true)

        viewModel.cancelActiveEmergency("emergency123")
        testScheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is UserUiState.Success)
        assertEquals("Emergency cancelled successfully", (currentState as UserUiState.Success).message)
        assertEquals(null, viewModel.activeEmergency.value)
    }

    @Test
    fun recommendHospital_success_updatesHospitalRecommendation() = runTest {
        val dummyHospital = com.example.smartambulance.data.model.Hospital(
            id = "hos-1",
            name = "City Hospital",
            latitude = 12.9800,
            longitude = 77.6000,
            totalIcuBeds = 10,
            availableIcuBeds = 5,
            rating = 4.5,
            phone = "1234567890",
            distanceKm = 4.2,
            icuStatus = "available"
        )
        val recommendation = HospitalRecommendation(
            recommended = dummyHospital,
            comparison = listOf(dummyHospital)
        )
        coEvery { repository.recommendHospital(12.9716, 77.5946, "high") } returns Result.success(recommendation)

        viewModel.recommendHospital(12.9716, 77.5946, "high")

        testScheduler.advanceUntilIdle()

        assertEquals(recommendation, viewModel.hospitalRecommendation.value)
    }
}
