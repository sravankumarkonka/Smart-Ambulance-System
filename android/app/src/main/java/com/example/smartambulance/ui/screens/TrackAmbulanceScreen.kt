package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
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
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackAmbulanceScreen(
    emergencyId: String,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    // Poll active emergency every 4 seconds to simulate real-time socket tracking
    LaunchedEffect(emergencyId) {
        while (true) {
            viewModel.fetchActiveEmergency(emergencyId)
            delay(4000)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UserUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is UserUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track Ambulance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Map section (takes top 45% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    val patientLatLng = LatLng(e.latitude, e.longitude)
                    val driverLatLng = if (e.driverLatitude != null && e.driverLongitude != null) {
                        LatLng(e.driverLatitude, e.driverLongitude)
                    } else null

                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(patientLatLng, 14f)
                    }

                    // Adjust camera when driver coordinates change to frame both
                    LaunchedEffect(driverLatLng) {
                        if (driverLatLng != null) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(driverLatLng, 13f)
                        }
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        // Patient Marker
                        Marker(
                            state = MarkerState(position = patientLatLng),
                            title = "Patient: ${e.patientName}",
                            snippet = "Your Location"
                        )

                        // Driver Marker
                        driverLatLng?.let {
                            Marker(
                                state = MarkerState(position = it),
                                title = "Ambulance: ${e.driverName ?: "En route"}",
                                snippet = "Driver contact: ${e.driverPhone}"
                            )
                        }

                        // Hospital Marker
                        if (e.hospitalLatitude != null && e.hospitalLongitude != null) {
                            Marker(
                                state = MarkerState(position = LatLng(e.hospitalLatitude, e.hospitalLongitude)),
                                title = "Destination: ${e.hospitalName ?: "Hospital"}",
                                snippet = "Assigned hospital"
                            )
                        }
                    }
                }

                // Details section (takes bottom 55% of height)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status Stepper Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Dispatch Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val statusSteps = listOf("pending", "assigned", "arrived", "completed")
                            val currentStatusIndex = statusSteps.indexOf(e.status.lowercase())
                            
                            statusSteps.forEachIndexed { index, step ->
                                val stepLabel = when (step) {
                                    "pending" -> "Request Received"
                                    "assigned" -> "Ambulance Dispatched"
                                    "arrived" -> "Arrived at Incident"
                                    else -> "Completed & Handed Over"
                                }
                                val isActive = index <= currentStatusIndex
                                val color = if (isActive) Color(0xFF2E7D32) else Color.Gray
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(color, shape = RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stepLabel,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Ambulance details
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Responder Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (e.driverName != null) {
                                Text("Driver Name: ${e.driverName}", fontSize = 14.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Phone: ${e.driverPhone}", fontSize = 14.sp)
                                    Button(
                                        onClick = { /* Launch phone dialer */ },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Call", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Text("Assigning nearest ambulance unit...", fontSize = 14.sp, color = Color.Gray)
                            }
                            
                            e.hospitalName?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Assigned Hospital:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Mock Accident Photo Upload
                    Button(
                        onClick = {
                            // Upload a mock image representation (100 byte array)
                            val dummyImage = ByteArray(100) { 0 }
                            viewModel.uploadAccidentImage(e.id ?: "", dummyImage)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Camera")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("UPLOAD ACCIDENT SCENE PHOTO")
                    }

                    if (e.imageUrl != null) {
                        Text(
                            text = "Accident Scene Photo Registered",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Cancel emergency button (only if not completed/cancelled)
                    if (e.status != "completed" && e.status != "cancelled") {
                        OutlinedButton(
                            onClick = { viewModel.cancelActiveEmergency(e.id ?: "") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) {
                            Text("CANCEL EMERGENCY REQUEST", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
