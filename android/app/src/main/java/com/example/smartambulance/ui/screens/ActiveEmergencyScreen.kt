package com.example.smartambulance.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.DriverUiState
import com.example.smartambulance.ui.viewmodel.DriverViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlin.math.*

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

// Matching web's STATUS_STEPS exactly
private data class StatusStep(
    val key: String, val label: String, val icon: String,
    val action: String?, val actionLabel: String?, val color: Color
)

private val STATUS_STEPS = listOf(
    StatusStep("assigned",        "Assigned",       "✅", "on_the_way",       "🚑 En Route (On The Way)",    Color(0xFF0288D1)),
    StatusStep("on_the_way",      "En Route",        "🚑", "reached",           "📍 Reached Incident Scene",   Color(0xFFF57C00)),
    StatusStep("reached",         "At Scene",        "📍", "patient_picked",    "👨‍⚕️ Patient Picked Up",      Color(0xFF7B1FA2)),
    StatusStep("patient_picked",  "Patient Picked",  "👨‍⚕️", "hospital_reached",  "🏥 Reached Hospital",         Color(0xFF388E3C)),
    StatusStep("hospital_reached","At Hospital",     "🏥", "completed",         "✅ Complete Dispatch",         Color(0xFF2E7D32)),
    StatusStep("completed",       "Completed",       "🎉", null,                null,                          Color(0xFF16A34A))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveEmergencyScreen(
    emergencyId: String,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: DriverViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambulance by viewModel.ambulance.collectAsStateWithLifecycle()

    // Real-time emergency from Firestore
    var emergency by remember { mutableStateOf<Emergency?>(null) }
    var currentStep by remember { mutableIntStateOf(0) }
    var showReleaseDialog by remember { mutableStateOf(false) }

    // GPS location for driver publishing
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var driverLat by remember { mutableDoubleStateOf(0.0) }
    var driverLng by remember { mutableDoubleStateOf(0.0) }

    // Load ambulance profile
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulanceProfile()
    }

    // Firestore real-time listener (matching web's subscribeToEmergency)
    LaunchedEffect(emergencyId) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("emergencies").document(emergencyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val e = Emergency(
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
                    emergency = e
                    // Update current step
                    val stepIdx = STATUS_STEPS.indexOfFirst { it.key == e.status.safeLower() }
                    if (stepIdx >= 0) currentStep = stepIdx
                }
            }
    }

    // Real-time GPS publishing loop (matching web's 5-second interval)
    LaunchedEffect(emergency, driverLat, driverLng) {
        val e = emergency ?: return@LaunchedEffect
        if (e.status.safeLower() == "completed" || e.status.safeLower() == "cancelled") return@LaunchedEffect

        while (true) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val token = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                driverLat = location.latitude
                                driverLng = location.longitude
                                viewModel.updateLocation(location.latitude, location.longitude, emergencyId)
                            }
                        }
                } else {
                    // Simulate movement towards patient (fallback like web does when geolocation fails)
                    val targetLat = if (e.status == "assigned" || e.status == "on_the_way") e.latitude
                        else (e.hospitalLatitude ?: e.latitude)
                    val targetLng = if (e.status == "assigned" || e.status == "on_the_way") e.longitude
                        else (e.hospitalLongitude ?: e.longitude)
                    val curLat = if (driverLat != 0.0) driverLat else (ambulance?.latitude ?: 12.9716)
                    val curLng = if (driverLng != 0.0) driverLng else (ambulance?.longitude ?: 77.5946)

                    val step = 0.0004
                    val diffLat = targetLat - curLat
                    val diffLng = targetLng - curLng
                    val dist = sqrt(diffLat * diffLat + diffLng * diffLng)
                    if (dist > step) {
                        driverLat = curLat + (diffLat / dist) * step
                        driverLng = curLng + (diffLng / dist) * step
                    } else {
                        driverLat = targetLat
                        driverLng = targetLng
                    }
                    viewModel.updateLocation(driverLat, driverLng, emergencyId)
                }
            } catch (_: Exception) {}
            delay(5000)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DriverUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                // If completed, go back to dashboard
                if (emergency?.status.safeLower() == "completed") {
                    onBack()
                }
            }
            is DriverUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Release confirmation dialog
    if (showReleaseDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseDialog = false },
            title = { Text("Release Assignment?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to reject/release this emergency assignment? It will be returned to the queue.") },
            confirmButton = {
                Button(
                    onClick = {
                        showReleaseDialog = false
                        viewModel.releaseEmergency(emergencyId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Release") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReleaseDialog = false }) { Text("Keep") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Dispatch Navigation", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Real-time navigation & milestone tracking", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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
            val patientLat = e.latitude
            val patientLng = e.longitude
            val effectiveDriverLat = if (driverLat != 0.0) driverLat else (ambulance?.latitude ?: 12.9716)
            val effectiveDriverLng = if (driverLng != 0.0) driverLng else (ambulance?.longitude ?: 77.5946)

            // Distance & ETA (Haversine)
            val R = 6371.0
            val dLat = Math.toRadians(patientLat - effectiveDriverLat)
            val dLng = Math.toRadians(patientLng - effectiveDriverLng)
            val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(effectiveDriverLat)) * cos(Math.toRadians(patientLat)) * sin(dLng / 2).pow(2.0)
            val distKm = R * 2 * atan2(sqrt(a), sqrt(1 - a))
            val etaMins = max(1, Math.round(distKm * 2.5).toInt())

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Map Section (40%)
                Box(modifier = Modifier.fillMaxWidth().weight(0.40f).background(Color.LightGray)) {
                    val patientLatLng = LatLng(patientLat, patientLng)
                    val driverLatLng = LatLng(effectiveDriverLat, effectiveDriverLng)
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(driverLatLng, 13f)
                    }

                    GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
                        Marker(state = MarkerState(position = patientLatLng), title = "Patient: ${e.patientName}", snippet = "Emergency Location")
                        Marker(state = MarkerState(position = driverLatLng), title = "🚑 Your Ambulance", snippet = "Live Position")
                        if (e.hospitalLatitude != null && e.hospitalLongitude != null) {
                            Marker(state = MarkerState(position = LatLng(e.hospitalLatitude, e.hospitalLongitude)), title = "🏥 ${e.hospitalName ?: "Hospital"}")
                        }
                    }
                }

                // Controls (60%)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ETA Banner Card
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
                            Text("🚨 ${e.severityLevel.safeUpper()} Priority | Route optimized for emergency", fontSize = 11.sp, color = Color(0xFF991B1B))
                        }
                    }

                    // Dispatch Milestone Stepper (matching web's STATUS_STEPS with action buttons)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dispatch Milestones", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            STATUS_STEPS.forEachIndexed { idx, step ->
                                val isDone = idx < currentStep
                                val isCurrent = idx == currentStep
                                val isPending = idx > currentStep

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    // Step circle
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                if (isDone) Color(0xFF16A34A) else if (isCurrent) step.color else Color(0xFFE2E8F0),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (isDone) "✓" else step.icon,
                                            fontSize = 12.sp,
                                            color = if (isDone || isCurrent) Color.White else Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            step.label,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDone) Color(0xFF16A34A) else if (isCurrent) step.color else Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }

                                    // Action button for current step only
                                    if (isCurrent && step.action != null) {
                                        Button(
                                            onClick = { viewModel.updateEmergencyStatus(emergencyId, step.action) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = step.color),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(step.actionLabel ?: "", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Connector line
                                if (idx < STATUS_STEPS.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 13.dp)
                                            .width(2.dp)
                                            .height(8.dp)
                                            .background(if (idx < currentStep) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                    )
                                }
                            }
                        }
                    }

                    // Quick Navigation Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val uri = Uri.parse("google.navigation:q=$patientLat,$patientLng")
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
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
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🏥 Go to Hospital", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Patient Information Card
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👤 Patient Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Name: ${e.patientName ?: "Emergency Patient"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Type: ${e.emergencyType.safeUpper()}", fontSize = 14.sp)
                            val sevColor = when (e.severityLevel.safeLower()) {
                                "critical" -> Color(0xFFB71C1C); "high" -> Color(0xFFE11D48)
                                "medium" -> Color(0xFFD97706); else -> Color(0xFF16A34A)
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

                    // Release / Reject Button
                    if (e.status.safeLower() != "completed" && e.status.safeLower() != "cancelled") {
                        OutlinedButton(
                            onClick = { showReleaseDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text("Reject / Release Assignment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading emergency details...", color = Color.Gray)
            }
        }
    }
}
