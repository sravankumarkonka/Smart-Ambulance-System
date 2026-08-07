package com.example.smartambulance

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.smartambulance.ui.screens.*

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Login
    ) {
        composable<Login> {
            LoginScreen(onNavigate = { navKey ->
                if (navKey == UserDashboard || navKey == DriverDashboard || navKey == AdminDashboard || navKey == SuperAdminDashboard) {
                    navController.navigate(navKey) {
                        popUpTo(Login) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<Register> {
            RegisterScreen(onNavigate = { navKey ->
                if (navKey == UserDashboard || navKey == DriverDashboard) {
                    navController.navigate(navKey) {
                        popUpTo(Login) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<UserDashboard> {
            UserDashboardScreen(onNavigate = { navKey ->
                if (navKey == Login) {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<ReportEmergency> {
            ReportEmergencyScreen(onNavigate = { navKey ->
                navController.navigate(navKey)
            })
        }
        composable<TrackAmbulance> { backStackEntry ->
            val trackRoute = backStackEntry.toRoute<TrackAmbulance>()
            TrackAmbulanceScreen(
                emergencyId = trackRoute.emergencyId,
                onNavigate = { navKey -> navController.navigate(navKey) }
            )
        }
        composable<EmergencyHistory> {
            EmergencyHistoryScreen(onNavigate = { navKey ->
                navController.navigate(navKey)
            })
        }
        composable<DriverDashboard> {
            DriverDashboardScreen(onNavigate = { navKey ->
                if (navKey == Login) {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<ActiveEmergency> { backStackEntry ->
            val route = backStackEntry.toRoute<ActiveEmergency>()
            ActiveEmergencyScreen(
                emergencyId = route.emergencyId,
                onNavigate = { navKey -> navController.navigate(navKey) }
            )
        }
        composable<AdminDashboard> {
            AdminDashboardScreen(onNavigate = { navKey ->
                if (navKey == Login) {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<SuperAdminDashboard> {
            SuperAdminDashboardScreen(onNavigate = { navKey ->
                if (navKey == Login) {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(navKey)
                }
            })
        }
        composable<LiveMap> {
            LiveMapScreen(onNavigate = { navKey ->
                navController.navigate(navKey)
            })
        }
        composable<DriverHistory> {
            DriverHistoryScreen(onNavigate = { destination ->
                if (destination == "back") {
                    navController.popBackStack()
                } else {
                    navController.navigate(destination)
                }
            })
        }
        composable<AdminHistory> {
            AdminHistoryScreen(onNavigate = { destination ->
                if (destination == "back") {
                    navController.popBackStack()
                } else {
                    navController.navigate(destination)
                }
            })
        }
        composable<Profile> {
            ProfileScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }
        composable<NotificationCenter> {
            NotificationCenterScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }
    }
}
