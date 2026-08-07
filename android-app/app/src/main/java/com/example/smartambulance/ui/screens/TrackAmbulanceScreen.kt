package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackAmbulanceScreen(
    emergencyId: String,
    onNavigate: (Any) -> Unit,
    viewModel: UserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    // Live ambulance position from Firestore real-time listener
    var liveDriverLat by remember { mutableStateOf<Double?>(null) }
    var liveDriverLng by remember { mutableStateOf<Double?>(null) }
    var liveDriverSpeed by remember { mutableStateOf(0.0) }
    var liveDriverHeading by remember { mutableStateOf(0.0) }
    var liveStatus by remember { mutableStateOf("") }

    // Pulse animation
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    // Subscribe to Firestore emergency doc (real-time) + ambulance doc (real-time)
    LaunchedEffect(emergencyId) {
        viewModel.fetchActiveEmergency(emergencyId)
        val db = FirebaseFirestore.getInstance()

        // Listen to emergency doc for status changes
        db.collection("emergencies").document(emergencyId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    liveStatus = snap.getString("status") ?: ""
                    val dLat = snap.getDouble("driverLatitude")
                    val dLng = snap.getDouble("driverLongitude")
                    if (dLat != null && dLng != null) {
                        liveDriverLat = dLat
                        liveDriverLng = dLng
                    }
                    val driverId = snap.getString("driverId")
                    if (driverId != null) {
                        // Also subscribe to ambulances/{driverId} for live telemetry
                        db.collection("ambulances").document(driverId)
                            .addSnapshotListener { ambSnap, _ ->
                                if (ambSnap != null && ambSnap.exists()) {
                                    ambSnap.getDouble("latitude")?.let { lat ->
                                        ambSnap.getDouble("longitude")?.let { lng ->
                                            liveDriverLat = lat
                                            liveDriverLng = lng
                                        }
                                    }
                                    liveDriverSpeed = ambSnap.getDouble("speed") ?: 0.0
                                    liveDriverHeading = ambSnap.getDouble("heading") ?: 0.0
                                }
                            }
                    }
                }
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

    val hasDriver = liveDriverLat != null && liveDriverLng != null
    val driverLatLng = if (hasDriver) LatLng(liveDriverLat!!, liveDriverLng!!) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Ambulance Tracker", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (hasDriver) {
                            Text(
                                text = "● LIVE  ${liveDriverSpeed.toInt()} km/h",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(UserDashboard) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {

                // ── MAP (60%) ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .background(Color(0xFF0D1B2A))
                ) {
                    val patientLatLng = LatLng(e.latitude, e.longitude)
                    val focusLatLng = driverLatLng ?: patientLatLng

                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(focusLatLng, 15f)
                    }

                    // Track if map has loaded successfully
                    var mapLoaded by remember { mutableStateOf(false) }

                    LaunchedEffect(driverLatLng, patientLatLng) {
                        try {
                            if (driverLatLng != null) {
                                val boundsBuilder = LatLngBounds.builder()
                                boundsBuilder.include(patientLatLng)
                                boundsBuilder.include(driverLatLng)
                                e.hospitalLatitude?.let { hl ->
                                    e.hospitalLongitude?.let { hlng ->
                                        boundsBuilder.include(LatLng(hl, hlng))
                                    }
                                }
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                                )
                            } else {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(patientLatLng, 15f)
                                )
                            }
                        } catch (_: Exception) {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(focusLatLng, 15f))
                        }
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapType = MapType.NORMAL,
                            isMyLocationEnabled = false
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            compassEnabled = true,
                            mapToolbarEnabled = true
                        ),
                        onMapLoaded = { mapLoaded = true }
                    ) {
                        // Patient marker (📍)
                        Marker(
                            state = MarkerState(position = patientLatLng),
                            title = "📍 ${e.patientName}",
                            snippet = "Emergency Location (${String.format("%.4f", e.latitude)}, ${String.format("%.4f", e.longitude)})"
                        )

                        // Live ambulance marker (🚑)
                        driverLatLng?.let { dll ->
                            Marker(
                                state = MarkerState(position = dll),
                                title = "🚑 ${e.driverName ?: "Ambulance"}",
                                snippet = "${liveDriverSpeed.toInt()} km/h • Bearing ${liveDriverHeading.toInt()}°"
                            )
                            // Route line
                            Polyline(
                                points = buildList {
                                    add(dll)
                                    add(patientLatLng)
                                    e.hospitalLatitude?.let { hl ->
                                        e.hospitalLongitude?.let { hlng -> add(LatLng(hl, hlng)) }
                                    }
                                },
                                color = Color(0xFF1565C0),
                                width = 12f
                            )
                        }

                        // Hospital marker (🏥)
                        e.hospitalLatitude?.let { hl ->
                            e.hospitalLongitude?.let { hlng ->
                                Marker(
                                    state = MarkerState(position = LatLng(hl, hlng)),
                                    title = "🏥 ${e.hospitalName ?: "Hospital"}",
                                    snippet = "Destination"
                                )
                            }
                        }
                    }

                    // Show loading overlay until map tiles load
                    if (!mapLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D1B2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Loading map...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                    }

                    // Status & Navigation Header Overlay
                    val (statusText, statusColor) = when (liveStatus.ifEmpty { e.status }) {
                        "Waiting", "pending" -> "⏳ Waiting for driver..." to Color(0xFFF57C00)
                        "assigned" -> "🚑 Ambulance is on the way!" to Color(0xFF1565C0)
                        "on_the_way" -> "🚀 Driver en route to you" to Color(0xFF0288D1)
                        "reached", "arrived" -> "📍 Driver arrived at scene" to Color(0xFF7B1FA2)
                        "patient_picked" -> "🧑‍⚕️ You're in the ambulance" to Color(0xFF388E3C)
                        "hospital_reached" -> "🏥 Arrived at hospital" to Color(0xFF2E7D32)
                        "completed" -> "✅ Emergency resolved" to Color(0xFF1B5E20)
                        "cancelled" -> "❌ Emergency cancelled" to Color(0xFFC62828)
                        else -> "📡 Connecting..." to Color.Gray
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopCenter),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.95f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (hasDriver) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = pulseAlpha))
                                    )
                                }
                            }
                        }

                        // Google Maps Live Traffic Navigation Button
                        Button(
                            onClick = {
                                val targetLat = driverLatLng?.latitude ?: e.latitude
                                val targetLng = driverLatLng?.longitude ?: e.longitude
                                try {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=$targetLat,$targetLng&mode=d")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$targetLat,$targetLng&travelmode=driving")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OPEN IN GOOGLE MAPS (LIVE TRAFFIC)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── INFO PANEL (40%) ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Telemetry cards (visible when driver is assigned)
                    if (hasDriver) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryCard("SPEED", "${liveDriverSpeed.toInt()} km/h", Color(0xFF1565C0), Modifier.weight(1f))
                            TelemetryCard("BEARING", "${liveDriverHeading.toInt()}°", Color(0xFF7B1FA2), Modifier.weight(1f))
                            TelemetryCard("STATUS", liveStatus.replace("_", " ").uppercase().take(8), Color(0xFF388E3C), Modifier.weight(1f))
                        }
                    }

                    // Dispatch Status Stepper Card
                    val curStat = liveStatus.ifEmpty { e.status }
                    val steps = listOf(
                        "Dispatch" to (curStat in listOf("pending", "Waiting", "assigned", "on_the_way", "arrived", "patient_picked", "hospital_reached", "completed")),
                        "En Route" to (curStat in listOf("on_the_way", "arrived", "patient_picked", "hospital_reached", "completed")),
                        "Arrived" to (curStat in listOf("arrived", "patient_picked", "hospital_reached", "completed")),
                        "Hospital" to (curStat in listOf("hospital_reached", "completed"))
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            steps.forEachIndexed { idx, (label, active) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        } else {
                                            Text("${idx + 1}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(label, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.primary else Color.Gray)
                                }
                            }
                        }
                    }

                    // Driver card
                    if (e.driverName != null || e.driverPhone != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Driver Assigned", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    Text(e.driverName ?: "Driver", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    e.driverPhone?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                                }
                                IconButton(onClick = {
                                    e.driverPhone?.let { phone ->
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        context.startActivity(intent)
                                    }
                                }) {
                                    Icon(Icons.Default.Call, contentDescription = "Call Driver", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }

                    // Hospital card
                    e.hospitalName?.let { hosp ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.LocalHospital, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                                Column {
                                    Text("Destination Hospital", fontSize = 11.sp, color = Color(0xFF388E3C))
                                    Text(hosp, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B5E20))
                                }
                            }
                        }
                    }

                    // Cancel button
                    if (liveStatus == "Waiting" || liveStatus == "pending" || liveStatus == "assigned" || e.status == "assigned" || e.status == "Waiting") {
                        OutlinedButton(
                            onClick = { viewModel.cancelActiveEmergency(emergencyId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) { Text("CANCEL EMERGENCY REQUEST") }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Connecting to emergency system...", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TelemetryCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
