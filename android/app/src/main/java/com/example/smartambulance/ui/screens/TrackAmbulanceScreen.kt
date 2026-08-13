package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlin.math.*

private data class StatusConfig(
    val title: String, val icon: String, val color: Color, val desc: String
)

private val STATUS_CONFIG = mapOf(
    "pending" to StatusConfig("Waiting for Dispatch", "⏳", Color(0xFFD97706), "Emergency coordinates shared. Nearest ambulance will be assigned shortly."),
    "assigned" to StatusConfig("Ambulance En Route", "🚑", Color(0xFF1976D2), "Driver has accepted your emergency and is driving to your location."),
    "on_the_way" to StatusConfig("Ambulance On The Way", "🚑", Color(0xFF1976D2), "Your ambulance is navigating to the incident scene."),
    "reached" to StatusConfig("Reached Scene", "📍", Color(0xFFF57C00), "Paramedics have reached your location."),
    "arrived" to StatusConfig("Ambulance Arrived", "✅", Color(0xFF2E7D32), "The response team has arrived at your location."),
    "patient_picked" to StatusConfig("Patient Picked Up", "👨‍⚕️", Color(0xFF7B1FA2), "Patient is in the ambulance, en route to hospital."),
    "hospital_reached" to StatusConfig("At Hospital", "🏥", Color(0xFF2E7D32), "Ambulance has reached the hospital."),
    "completed" to StatusConfig("Emergency Resolved", "🎉", Color(0xFF16A34A), "The emergency case has been closed successfully."),
    "cancelled" to StatusConfig("Emergency Cancelled", "❌", Color(0xFFDC2626), "This emergency request was cancelled.")
)

private val STATUS_TIMELINE = listOf("pending", "assigned", "on_the_way", "reached", "patient_picked", "hospital_reached", "completed")

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

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
    val activeEmergencyState by viewModel.activeEmergency.collectAsStateWithLifecycle()

    var firestoreEmergency by remember { mutableStateOf<Emergency?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var isTimeoutReached by remember { mutableStateOf(false) }

    // Fetch initial details via REST API & setup Firestore real-time listener
    LaunchedEffect(emergencyId) {
        if (emergencyId.isNotEmpty()) {
            viewModel.fetchActiveEmergency(emergencyId)

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies").document(emergencyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        firestoreEmergency = Emergency(
                            id = snapshot.id,
                            userId = snapshot.getString("userId") ?: "",
                            patientName = snapshot.getString("patientName") ?: "Emergency Patient",
                            emergencyType = snapshot.getString("emergencyType") ?: "General",
                            description = snapshot.getString("description") ?: "",
                            latitude = snapshot.getDouble("latitude") ?: 0.0,
                            longitude = snapshot.getDouble("longitude") ?: 0.0,
                            severityLevel = snapshot.getString("severityLevel") ?: "medium",
                            status = snapshot.getString("status") ?: "pending",
                            driverId = snapshot.getString("driverId"),
                            driverName = snapshot.getString("driverName"),
                            driverPhone = snapshot.getString("driverPhone"),
                            driverLatitude = snapshot.getDouble("driverLatitude"),
                            driverLongitude = snapshot.getDouble("driverLongitude"),
                            hospitalName = snapshot.getString("hospitalName"),
                            hospitalLatitude = snapshot.getDouble("hospitalLatitude"),
                            hospitalLongitude = snapshot.getDouble("hospitalLongitude"),
                            imageUrl = snapshot.getString("imageUrl")
                        )
                    }
                }
        }
        // Timeout guard: if no data after 4 seconds, set timeout flag to display fallback UI
        delay(4000)
        isTimeoutReached = true
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

    // Combine Firestore snapshot data with ViewModel active emergency data
    val emergency = firestoreEmergency ?: activeEmergencyState

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Emergency?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to cancel this emergency request?") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelActiveEmergency(emergencyId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Yes, Cancel") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) { Text("No, Keep") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Ambulance Telemetry", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.fetchActiveEmergency(emergencyId)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (emergency != null) {
            val patientLat = if (emergency.latitude != 0.0) emergency.latitude else 12.9716
            val patientLng = if (emergency.longitude != 0.0) emergency.longitude else 77.5946
            val driverLat = emergency.driverLatitude ?: (patientLat + 0.005)
            val driverLng = emergency.driverLongitude ?: (patientLng + 0.005)

            // Distance & ETA calculation (Haversine formula)
            val R = 6371.0
            val dLat = Math.toRadians(patientLat - driverLat)
            val dLng = Math.toRadians(patientLng - driverLng)
            val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(driverLat)) * cos(Math.toRadians(patientLat)) * sin(dLng / 2).pow(2.0)
            val distKm = R * 2 * atan2(sqrt(a), sqrt(1 - a))
            val etaMins = max(1, Math.round(distKm * 2.5).toInt())

            val statusKey = emergency.status.safeLower()
            val currentStatusConfig = STATUS_CONFIG[statusKey] ?: STATUS_CONFIG["pending"]!!
            val currentStepIdx = STATUS_TIMELINE.indexOf(statusKey).coerceAtLeast(0)

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Map Section (40% height)
                Box(modifier = Modifier.fillMaxWidth().weight(0.40f).background(Color.LightGray)) {
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
                            title = "📍 Patient: ${emergency.patientName}",
                            snippet = "Incident Scene (${emergency.emergencyType})"
                        )

                        if (emergency.driverName != null || emergency.status.safeLower() != "pending") {
                            Marker(
                                state = MarkerState(position = driverLatLng),
                                title = "🚑 ${emergency.driverName ?: "Assigned Ambulance"}",
                                snippet = "Live Driver GPS"
                            )
                        }

                        if (emergency.hospitalLatitude != null && emergency.hospitalLongitude != null) {
                            Marker(
                                state = MarkerState(position = LatLng(emergency.hospitalLatitude, emergency.hospitalLongitude)),
                                title = "🏥 ${emergency.hospitalName ?: "Destination Hospital"}"
                            )
                        }
                    }
                }

                // Controls and Timeline Section (60% height)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = currentStatusConfig.color.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(currentStatusConfig.icon, fontSize = 32.sp)
                            Column {
                                Text(currentStatusConfig.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = currentStatusConfig.color)
                                Text(currentStatusConfig.desc, fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }

                    // ETA Telemetry Card (If Driver is Assigned)
                    if (emergency.driverName != null || emergency.status.safeLower() != "pending") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ESTIMATED ARRIVAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text("$etaMins mins", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                }
                                Column {
                                    Text("DISTANCE TO SCENE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(String.format("%.1f km", distKm), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                }
                            }
                        }
                    }

                    // 7-Step Dispatch Timeline Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dispatch Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            STATUS_TIMELINE.forEachIndexed { idx, stepKey ->
                                val stepConfig = STATUS_CONFIG[stepKey] ?: return@forEachIndexed
                                val isDone = idx < currentStepIdx
                                val isCurrent = idx == currentStepIdx

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(
                                                if (isDone) Color(0xFF16A34A) else if (isCurrent) stepConfig.color else Color(0xFFE2E8F0),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (isDone) "✓" else stepConfig.icon,
                                            fontSize = 11.sp,
                                            color = if (isDone || isCurrent) Color.White else Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        stepConfig.title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isDone) Color(0xFF16A34A) else if (isCurrent) stepConfig.color else Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }

                                if (idx < STATUS_TIMELINE.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .width(2.dp)
                                            .height(8.dp)
                                            .background(if (idx < currentStepIdx) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                    )
                                }
                            }
                        }
                    }

                    // Assigned Driver & Ambulance Team Card
                    if (emergency.driverName != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("👨‍⚕️ Assigned Ambulance Team", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E40AF))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(emergency.driverName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Contact: ${emergency.driverPhone ?: "Available"}", fontSize = 12.sp, color = Color.Gray)
                                    }

                                    emergency.driverPhone?.let { phoneNum ->
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum"))
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Call, "Call Driver", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Call Driver", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Patient Details Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👤 Emergency Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Patient: ${emergency.patientName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Type: ${emergency.emergencyType.safeUpper()}", fontSize = 13.sp)
                            Text("Severity: ${emergency.severityLevel.safeUpper()}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (emergency.description.isNotEmpty()) {
                                Text("Description: ${emergency.description}", fontSize = 13.sp, color = Color.DarkGray)
                            }
                            emergency.hospitalName?.let { hName ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("🏥 Destination Hospital: $hName", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (emergency.status.safeLower() == "pending" || emergency.status.safeLower() == "assigned") {
                            OutlinedButton(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel Request", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = { onBack() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back to Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isTimeoutReached) {
                        CircularProgressIndicator()
                        Text("Connecting to dispatch system...", color = Color.Gray, fontSize = 14.sp)
                    } else {
                        Text("⚠️ Emergency Details Not Found", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                        Text("ID: $emergencyId", fontSize = 12.sp, color = Color.Gray)
                        Button(
                            onClick = {
                                isTimeoutReached = false
                                viewModel.fetchActiveEmergency(emergencyId)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Retry", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Connection")
                        }
                        OutlinedButton(onClick = { onBack() }) {
                            Text("Return to Dashboard")
                        }
                    }
                }
            }
        }
    }
}
