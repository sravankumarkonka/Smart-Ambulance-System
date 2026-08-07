package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                title = { Text("Telemetry & Routing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(DriverDashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Map Section (takes 45%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .background(Color.LightGray)
                ) {
                    val patientLatLng = LatLng(e.latitude, e.longitude)
                    val driverLatLng = LatLng(
                        ambulance?.latitude ?: 12.9716,
                        ambulance?.longitude ?: 77.5946
                    )

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
                            title = "Your Ambulance",
                            snippet = "Telemetry Position"
                        )
                    }
                }

                // Details and controls (takes 55%)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Incident Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Patient Name: ${e.patientName}", fontSize = 14.sp)
                            Text("Emergency type: ${e.emergencyType.uppercase()}", fontSize = 14.sp)
                            Text("Description: ${e.description}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Severity: ${e.severityLevel.uppercase()}", fontSize = 13.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            
                            e.hospitalName?.let {
                                Text("Assigned Target Hospital: $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Stepper status buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (e.status == "assigned") {
                            Button(
                                onClick = { viewModel.updateEmergencyStatus(e.id ?: "", "arrived") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                            ) {
                                Text("ARRIVED AT PATIENT")
                            }
                        } else if (e.status == "arrived") {
                            Button(
                                onClick = { viewModel.updateEmergencyStatus(e.id ?: "", "completed") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("COMPLETE & DEPART")
                            }
                        }

                        // Simulation button
                        Button(
                            onClick = {
                                if (!isSimulating) {
                                    isSimulating = true
                                    scope.launch {
                                        val startLat = ambulance?.latitude ?: 12.9716
                                        val startLng = ambulance?.longitude ?: 77.5946
                                        val targetLat = e.latitude
                                        val targetLng = e.longitude

                                        // Step coordinates
                                        val steps = 8
                                        for (i in 1..steps) {
                                            if (!isSimulating) break
                                            val fraction = i.toDouble() / steps
                                            val curLat = startLat + (targetLat - startLat) * fraction
                                            val curLng = startLng + (targetLng - startLng) * fraction
                                            
                                            viewModel.updateLocation(curLat, curLng, e.id)
                                            delay(1500) // Update every 1.5 seconds
                                        }
                                        isSimulating = false
                                        Toast.makeText(context, "Location simulation finished", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    isSimulating = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSimulating) Color.Red else MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(if (isSimulating) "STOP SIMULATION" else "SIMULATE DRIVE")
                        }
                    }

                    // Release back to queue or cancel options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.releaseEmergency(e.id ?: "") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray)
                        ) {
                            Text("RELEASE TASK")
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.updateEmergencyStatus(e.id ?: "", "cancelled") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) {
                            Text("ABORT CASE")
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
