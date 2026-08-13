package com.example.smartambulance

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.smartambulance.ui.screens.*

@Composable
fun MainNavigation() {
    // Start the backstack at the Login screen
    val backStack = rememberNavBackStack(Login)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(onNavigate = { navKey ->
                    // For dashboard targets, replace login to clear backstack
                    if (navKey == UserDashboard || navKey == DriverDashboard || navKey == AdminDashboard || navKey == SuperAdminDashboard) {
                        backStack.removeLastOrNull()
                    }
                    backStack.add(navKey)
                })
            }
            entry<Register> {
                RegisterScreen(onNavigate = { navKey ->
                    if (navKey == UserDashboard || navKey == DriverDashboard || navKey == AdminDashboard || navKey == SuperAdminDashboard) {
                        backStack.removeLastOrNull() // Pop register screen
                        backStack.removeLastOrNull() // Pop login screen
                    }
                    backStack.add(navKey)
                })
            }
            entry<UserDashboard> {
                UserDashboardScreen(onNavigate = { navKey ->
                    if (navKey == Login) {
                        backStack.removeLastOrNull()
                    }
                    backStack.add(navKey)
                })
            }
            entry<ReportEmergency> {
                ReportEmergencyScreen(
                    onNavigate = { navKey ->
                        if (navKey is TrackAmbulance) {
                            // Replace ReportEmergency with TrackAmbulance so back goes to dashboard
                            backStack.removeLastOrNull()
                        }
                        backStack.add(navKey)
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<TrackAmbulance> { key ->
                TrackAmbulanceScreen(
                    emergencyId = key.emergencyId,
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<EmergencyHistory> {
                EmergencyHistoryScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DriverDashboard> {
                DriverDashboardScreen(onNavigate = { navKey ->
                    if (navKey == Login) {
                        backStack.removeLastOrNull()
                    }
                    backStack.add(navKey)
                })
            }
            entry<DriverHistory> {
                DriverHistoryScreen(
                    onNavigate = { _ -> backStack.removeLastOrNull() }
                )
            }
            entry<ActiveEmergency> { key ->
                ActiveEmergencyScreen(
                    emergencyId = key.emergencyId,
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AdminDashboard> {
                AdminDashboardScreen(onNavigate = { navKey ->
                    if (navKey == Login) {
                        backStack.removeLastOrNull()
                    }
                    backStack.add(navKey)
                })
            }
            entry<SuperAdminDashboard> {
                SuperAdminDashboardScreen(onNavigate = { navKey ->
                    if (navKey == Login) {
                        backStack.removeLastOrNull()
                    }
                    backStack.add(navKey)
                })
            }
            entry<LiveMap> {
                LiveMapScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<ProfileScreen> {
                ProfileScreen(
                    onNavigate = { navKey ->
                        if (navKey == Login) {
                            backStack.removeLastOrNull()
                        }
                        backStack.add(navKey)
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
