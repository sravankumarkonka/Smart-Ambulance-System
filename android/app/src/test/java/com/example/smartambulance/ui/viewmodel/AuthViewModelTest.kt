package com.example.smartambulance.ui.viewmodel

import com.example.smartambulance.data.model.AuthResponse
import com.example.smartambulance.data.model.User
import com.example.smartambulance.data.repository.AuthRepository
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
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_success_updatesUiStateToSuccess() = runTest {
        val email = "test@example.com"
        val password = "password"
        val dummyUser = User(uid = "123", name = "Test User", email = email, phone = "1234567890", role = "user")
        val authResponse = AuthResponse(idToken = "token", uid = "123", profile = dummyUser)
        coEvery { authRepository.login(email, password) } returns Result.success(authResponse)

        viewModel.login(email, password)
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)

        testScheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is AuthUiState.Success)
        assertEquals(dummyUser, (currentState as AuthUiState.Success).user)
        assertEquals("user", currentState.role)
    }

    @Test
    fun login_failure_updatesUiStateToError() = runTest {
        val email = "test@example.com"
        val password = "password"
        val errorMsg = "Invalid credentials"
        coEvery { authRepository.login(email, password) } returns Result.failure(Exception(errorMsg))

        viewModel.login(email, password)
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)

        testScheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is AuthUiState.Error)
        assertEquals(errorMsg, (currentState as AuthUiState.Error).message)
    }

    @Test
    fun sendPasswordReset_success_updatesUiStateToPasswordResetSent() = runTest {
        val email = "test@example.com"
        coEvery { authRepository.sendPasswordResetEmail(email) } returns Result.success(Unit)

        viewModel.sendPasswordReset(email)
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)

        testScheduler.advanceUntilIdle()

        assertEquals(AuthUiState.PasswordResetSent, viewModel.uiState.value)
    }
}
