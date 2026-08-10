package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.ui.viewmodel.DriverUiState
import com.example.smartambulance.ui.viewmodel.DriverViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveEmergencyScreen(
    emergencyId: String,
    onNavigate: (NavKey) -> Unit,
    viewModel: DriverViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emergency by viewModel.activeEmergency.collectAsStateWithLifecycle()
    val ambulance by viewModel.ambulance.collectAsStateWithLifecycle()

    var isSimulating by remember { mutableStateOf(false) }

    LaunchedEffect(emergencyId) {
        viewModel.fetchEmergencyDetails(emergencyId)
        viewModel.fetchAmbulanceProfile()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DriverUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                if (emergency == null) {
                    onNavigate(DriverDashboard)
                }
            }
            is DriverUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Dispatch Navigation", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Real-time navigation & milestone tracking", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(DriverDashboard) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🟢 Active Duty", fontSize = 11.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        emergency?.let { e ->
            val driverLat = ambulance?.latitude ?: 12.9716
            val driverLng = ambulance?.longitude ?: 77.5946
            val patientLat = e.latitude
            val patientLng = e.longitude

            // Calculate Distance (Haversine formula in KM)
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(patientLat - driverLat)
            val dLng = Math.toRadians(patientLng - driverLng)
            val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(driverLat)) * cos(Math.toRadians(patientLat)) * sin(dLng / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val distKm = earthRadiusKm * c
            val etaMins = max(1, Math.round(distKm * 2.5).toInt())

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Map Section (40% height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                        .background(Color.LightGray)
                ) {
                    val patientLatLng = LatLng(patientLat, patientLng)
                    val driverLatLng = LatLng(driverLat, driverLng)

                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(driverLatLng, 13f)
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(
                            state = MarkerState(position = patientLatLng),
                            title = "Patient: ${e.patientName}",
                            snippet = "Emergency Location"
                        )
                        Marker(
                            state = MarkerState(position = driverLatLng),
                            title = "Ambulance Unit",
                            snippet = "Telemetry Position"
                        )
                    }
                }

                // Controls & Details Section (60% height)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live Dispatch ETA Banner Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⏱️ Live Dispatch ETA & Telemetry", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF991B1B))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(String.format("%.1f km", distKm), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                }
                                Column {
                                    Text("ESTIMATED ETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text("$etaMins mins", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                }
                            }
                            Text("🚨 ${e.severityLevel.safeUpper()} Priority | Route optimized for emergency response", fontSize = 11.sp, color = Color(0xFF991B1B))
                        }
                    }

                    // Dispatch Control Panel Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Dispatch Control Panel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Current Status: ${e.status.safeUpper()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                            // Milestone Primary Action Button
                            val currentStatus = e.status.safeLower()
                            val nextButtonText = when (currentStatus) {
                                "assigned" -> "🚑 En Route (On The Way)"
                                "accepted", "on_the_way" -> "🎯 Arrived at Patient Location"
                                "arrived", "reached" -> "🩺 Patient Picked Up"
                                "patient_picked" -> "🏥 Arrived at Hospital"
                                "hospital_reached" -> "✅ Complete Dispatch Call"
                                else -> "✅ Complete Dispatch Call"
                            }

                            val nextStatusTarget = when (currentStatus) {
                                "assigned" -> "on_the_way"
                                "accepted", "on_the_way" -> "arrived"
                                "arrived", "reached" -> "patient_picked"
                                "patient_picked" -> "hospital_reached"
                                "hospital_reached" -> "completed"
                                else -> "completed"
                            }

                            Button(
                                onClick = { viewModel.updateEmergencyStatus(e.id ?: "", nextStatusTarget) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6BFF))
                            ) {
                                Text(nextButtonText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // Quick Navigation Intents to Google Maps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = Uri.parse("google.navigation:q=$patientLat,$patientLng")
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🎯 Go to Patient", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val hLat = e.hospitalLatitude ?: patientLat
                                        val hLng = e.hospitalLongitude ?: patientLng
                                        val uri = Uri.parse("google.navigation:q=$hLat,$hLng")
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🏥 Go to Hospital", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Reject / Release Task Button
                            OutlinedButton(
                                onClick = { viewModel.releaseEmergency(e.id ?: "") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Text("Reject / Release Assignment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Live GPS Simulation Button
                            Button(
                                onClick = {
                                    if (!isSimulating) {
                                        isSimulating = true
                                        scope.launch {
                                            val startLat = ambulance?.latitude ?: 12.9716
                                            val startLng = ambulance?.longitude ?: 77.5946
                                            val targetLat = e.latitude
                                            val targetLng = e.longitude

                                            val steps = 8
                                            for (i in 1..steps) {
                                                if (!isSimulating) break
                                                val fraction = i.toDouble() / steps
                                                val curLat = startLat + (targetLat - startLat) * fraction
                                                val curLng = startLng + (targetLng - startLng) * fraction
                                                
                                                viewModel.updateLocation(curLat, curLng, e.id)
                                                delay(1500)
                                            }
                                            isSimulating = false
                                            Toast.makeText(context, "Telemetry simulation complete", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        isSimulating = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSimulating) Color.Red else Color.Gray)
                            ) {
                                Text(if (isSimulating) "STOP TELEMETRY DRIVE" else "SIMULATE LIVE DRIVE GPS", fontSize = 12.sp)
                            }
                        }
                    }

                    // Patient Information Details Panel Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👤 Patient Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Name: ${e.patientName ?: "Emergency Patient"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Emergency Type: ${e.emergencyType.safeUpper()}", fontSize = 14.sp)
                            
                            val sevColor = when (e.severityLevel.safeLower()) {
                                "critical" -> Color(0xFFB71C1C)
                                "high" -> Color(0xFFE11D48)
                                "medium" -> Color(0xFFD97706)
                                else -> Color(0xFF16A34A)
                            }
                            Text("Severity: ${e.severityLevel.safeUpper()}", fontSize = 13.sp, color = sevColor, fontWeight = FontWeight.Bold)
                            Text("Description: ${e.description}", fontSize = 13.sp, color = Color.DarkGray)

                            e.hospitalName?.let { hName ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("🏥 Destination Hospital:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(hName, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Turn-by-Turn Directions Milestones Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🗺️ Turn-by-Turn Route Milestones", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            StepRow(number = "1", title = "Depart origin location", sub = "0 m - 0 min")
                            StepRow(number = "2", title = "Proceed to patient emergency location", sub = String.format("%.1f km - %d mins", distKm, etaMins))
                            StepRow(number = "3", title = "Transfer patient to ${e.hospitalName ?: "Destination Hospital"}", sub = "Final destination")
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun StepRow(number: String, title: String, sub: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).background(Color(0xFF1E6BFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(sub, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
