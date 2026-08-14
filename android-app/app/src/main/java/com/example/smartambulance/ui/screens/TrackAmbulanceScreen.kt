package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.isValidCoordinate
import com.example.smartambulance.data.repository.GoogleMapsRepository
import com.example.smartambulance.data.repository.RouteStep
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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

    val gmapsRepo = remember { GoogleMapsRepository() }

    // Live driver telemetry state from Firestore
    var liveDriverLat by remember { mutableStateOf<Double?>(null) }
    var liveDriverLng by remember { mutableStateOf<Double?>(null) }
    var liveDriverSpeed by remember { mutableStateOf(0.0) }
    var liveDriverHeading by remember { mutableStateOf(0.0) }
    var liveStatus by remember { mutableStateOf("") }

    // Calculated route, ETA, distance, and steps
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var etaText by remember { mutableStateOf<String?>("Calculating...") }
    var distanceText by remember { mutableStateOf<String?>("") }
    var routeSteps by remember { mutableStateOf<List<RouteStep>>(emptyList()) }
    var routeSummary by remember { mutableStateOf<String?>(null) }

    // Pulse animation for live indicator
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    // Subscribe to Firestore emergency doc & driver telemetry
    DisposableEffect(emergencyId) {
        viewModel.fetchActiveEmergency(emergencyId)
        val db = FirebaseFirestore.getInstance()

        var ambListener: com.google.firebase.firestore.ListenerRegistration? = null

        val emergencyListener = db.collection("emergencies").document(emergencyId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("TrackAmbulanceScreen", "Firestore emergency listener error: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    liveStatus = snap.getString("status") ?: ""
                    val dLat = snap.getDouble("driverLatitude")
                    val dLng = snap.getDouble("driverLongitude")
                    if (isValidCoordinate(dLat, dLng)) {
                        liveDriverLat = dLat
                        liveDriverLng = dLng
                    }

                    val driverId = snap.getString("driverId") ?: snap.getString("assignedDriver")
                    if (driverId != null && driverId != "null" && ambListener == null) {
                        ambListener = db.collection("ambulances").document(driverId)
                            .addSnapshotListener { ambSnap, _ ->
                                if (ambSnap != null && ambSnap.exists()) {
                                    val aLat = ambSnap.getDouble("latitude")
                                    val aLng = ambSnap.getDouble("longitude")
                                    if (isValidCoordinate(aLat, aLng)) {
                                        liveDriverLat = aLat
                                        liveDriverLng = aLng
                                    }
                                    liveDriverSpeed = ambSnap.getDouble("speed") ?: 0.0
                                    liveDriverHeading = ambSnap.getDouble("heading") ?: 0.0
                                }
                            }
                    }
                }
            }

        onDispose {
            emergencyListener.remove()
            ambListener?.remove()
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

    val currentStatus = (liveStatus.ifEmpty { emergency?.status ?: "" }).trim().lowercase()
    val isStage2 = currentStatus in listOf("patient_picked", "hospital_reached")
    val hasDriver = isValidCoordinate(liveDriverLat, liveDriverLng)

    // Recalculate route and ETA whenever positions or stage changes
    LaunchedEffect(emergency, liveDriverLat, liveDriverLng, currentStatus) {
        val e = emergency ?: return@LaunchedEffect
        val patientValid = isValidCoordinate(e.latitude, e.longitude)
        if (!patientValid) return@LaunchedEffect

        val originLat = if (hasDriver) liveDriverLat!! else e.latitude
        val originLng = if (hasDriver) liveDriverLng!! else e.longitude

        val hasHospital = isValidCoordinate(e.hospitalLatitude, e.hospitalLongitude)
        val destLat = if (isStage2 && hasHospital) e.hospitalLatitude!! else e.latitude
        val destLng = if (isStage2 && hasHospital) e.hospitalLongitude!! else e.longitude

        if (isValidCoordinate(originLat, originLng) && isValidCoordinate(destLat, destLng)) {
            val res = gmapsRepo.getRoute(Pair(originLat, originLng), Pair(destLat, destLng))
            if (res != null && res.polylinePoints.isNotEmpty()) {
                routePoints = res.polylinePoints.map { LatLng(it.first, it.second) }
                etaText = res.trafficDurationText ?: res.durationText
                distanceText = res.distanceText
                routeSteps = res.steps
                routeSummary = res.summary
            } else {
                val distKm = gmapsRepo.haversineDistance(originLat, originLng, destLat, destLng)
                distanceText = String.format("%.1f km", distKm)
                if (distKm < 0.05) {
                    etaText = "Arrived"
                } else {
                    val minutes = Math.max(1, Math.round(distKm / 30.0 * 60).toInt())
                    etaText = "$minutes mins"
                }
                routePoints = listOf(LatLng(originLat, originLng), LatLng(destLat, destLng))
                routeSteps = emptyList()
                routeSummary = null
            }
        } else {
            etaText = "ETA unavailable"
            distanceText = "-- km"
            routePoints = emptyList()
            routeSteps = emptyList()
            routeSummary = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Ambulance Tracker", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (hasDriver) {
                            Text(
                                text = "● LIVE TELEMETRY  ${liveDriverSpeed.toInt()} km/h",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text("Connecting to GPS...", fontSize = 11.sp, color = Color.Gray)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top 50%: Interactive Google Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.50f)
                ) {
                    LiveTrackingMapView(
                        emergency = e,
                        liveDriverLat = liveDriverLat,
                        liveDriverLng = liveDriverLng,
                        liveDriverSpeed = liveDriverSpeed,
                        liveDriverHeading = liveDriverHeading,
                        currentStatus = currentStatus,
                        routePoints = routePoints,
                        pulseAlpha = pulseAlpha
                    )
                }

                // Bottom 50%: Full Information & Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.50f)
                ) {
                    LiveTrackingInfoPanel(
                        emergency = e,
                        emergencyId = emergencyId,
                        currentStatus = currentStatus,
                        isStage2 = isStage2,
                        etaText = etaText,
                        distanceText = distanceText,
                        liveDriverLat = liveDriverLat,
                        liveDriverLng = liveDriverLng,
                        liveDriverSpeed = liveDriverSpeed,
                        routeSteps = routeSteps,
                        routeSummary = routeSummary,
                        onCancelEmergency = { viewModel.cancelActiveEmergency(emergencyId) }
                    )
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Connecting to live tracking server...", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LiveTrackingMapView(
    emergency: Emergency,
    liveDriverLat: Double?,
    liveDriverLng: Double?,
    liveDriverSpeed: Double,
    liveDriverHeading: Double,
    currentStatus: String,
    routePoints: List<LatLng>,
    pulseAlpha: Float
) {
    val patientValid = isValidCoordinate(emergency.latitude, emergency.longitude)
    if (!patientValid) return

    val hospitalValid = isValidCoordinate(emergency.hospitalLatitude, emergency.hospitalLongitude)
    val hasDriver = isValidCoordinate(liveDriverLat, liveDriverLng)
    val driverLatLng = if (hasDriver) LatLng(liveDriverLat!!, liveDriverLng!!) else null
    val patientLatLng = LatLng(emergency.latitude, emergency.longitude)
    val focusLatLng = driverLatLng ?: patientLatLng

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(focusLatLng, 15f)
    }

    var mapLoaded by remember { mutableStateOf(false) }
    var mapLoadTimedOut by remember { mutableStateOf(false) }

    // Detect map load timeout — if tiles don't load in 15s, the API key is likely invalid
    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) {
            kotlinx.coroutines.delay(15_000)
            if (!mapLoaded) mapLoadTimedOut = true
        }
    }

    LaunchedEffect(driverLatLng, patientLatLng, emergency.hospitalLatitude, emergency.hospitalLongitude) {
        try {
            val boundsBuilder = LatLngBounds.builder()
            boundsBuilder.include(patientLatLng)
            if (driverLatLng != null) boundsBuilder.include(driverLatLng)
            if (hospitalValid) boundsBuilder.include(LatLng(emergency.hospitalLatitude!!, emergency.hospitalLongitude!!))

            val bounds = boundsBuilder.build()
            if (bounds.northeast != bounds.southwest) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            } else {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(focusLatLng, 15f))
            }
        } catch (_: Exception) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(focusLatLng, 15f))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, compassEnabled = true, mapToolbarEnabled = true),
            onMapLoaded = {
                mapLoaded = true
                mapLoadTimedOut = false
            }
        ) {
            // Patient marker
            Marker(
                state = MarkerState(position = patientLatLng),
                title = "👤 Patient: ${emergency.patientName}",
                snippet = "Emergency Location",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )

            // Driver marker
            driverLatLng?.let { dll ->
                Marker(
                    state = MarkerState(position = dll),
                    title = "🚑 ${emergency.driverName ?: "Ambulance"}",
                    snippet = "${liveDriverSpeed.toInt()} km/h • Bearing ${liveDriverHeading.toInt()}°",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Hospital marker
            if (hospitalValid) {
                Marker(
                    state = MarkerState(position = LatLng(emergency.hospitalLatitude!!, emergency.hospitalLongitude!!)),
                    title = "🏥 ${emergency.hospitalName ?: "Hospital"}",
                    snippet = "Destination Hospital",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Route Polyline
            if (routePoints.isNotEmpty()) {
                val isStage2 = currentStatus in listOf("patient_picked", "hospital_reached")
                Polyline(
                    points = routePoints,
                    color = if (isStage2) Color(0xFF2E7D32) else Color(0xFF1565C0),
                    width = 12f
                )
            }
        }

        if (mapLoadTimedOut) {
            com.example.smartambulance.ui.components.OpenStreetMapWebView(
                patientLat = emergency.latitude,
                patientLng = emergency.longitude,
                patientName = emergency.patientName,
                driverLat = liveDriverLat,
                driverLng = liveDriverLng,
                driverName = emergency.driverName ?: "Ambulance",
                driverSpeed = liveDriverSpeed,
                hospitalLat = emergency.hospitalLatitude,
                hospitalLng = emergency.hospitalLongitude,
                hospitalName = emergency.hospitalName ?: "Hospital"
            )
        } else if (!mapLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Initializing Maps...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Status overlay at top of map
        val (statusText, statusBgColor) = when (currentStatus) {
            "waiting", "pending" -> "⏳ Waiting for ambulance assignment..." to Color(0xFFF57C00)
            "assigned", "accepted" -> "🚑 Ambulance Dispatched! En route to patient" to Color(0xFF1565C0)
            "on_the_way", "en_route", "enroute" -> "🚀 Ambulance En Route to Patient" to Color(0xFF0288D1)
            "reached", "arrived" -> "📍 Ambulance Arrived at Scene" to Color(0xFF7B1FA2)
            "patient_picked" -> "🧑‍⚕️ Patient Picked Up — Heading to Hospital" to Color(0xFF388E3C)
            "hospital_reached" -> "🏥 Arrived at Destination Hospital" to Color(0xFF2E7D32)
            "completed" -> "✅ Emergency Resolved" to Color(0xFF1B5E20)
            "cancelled", "canceled" -> "❌ Emergency Cancelled" to Color(0xFFC62828)
            else -> "📡 Connecting to emergency network..." to Color.Gray
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp).align(Alignment.TopCenter),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = statusBgColor.copy(alpha = 0.95f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                if (hasDriver) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTrackingInfoPanel(
    emergency: Emergency,
    emergencyId: String,
    currentStatus: String,
    isStage2: Boolean,
    etaText: String?,
    distanceText: String?,
    liveDriverLat: Double?,
    liveDriverLng: Double?,
    liveDriverSpeed: Double,
    routeSteps: List<RouteStep>,
    routeSummary: String?,
    onCancelEmergency: () -> Unit
) {
    val context = LocalContext.current
    val hospitalValid = isValidCoordinate(emergency.hospitalLatitude, emergency.hospitalLongitude)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── ETA & DISTANCE METRICS ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ESTIMATED ARRIVAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    Text(etaText ?: "Calculating...", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text(distanceText ?: "-- km", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                }
            }

            // Speed metric
            if (isValidCoordinate(liveDriverLat, liveDriverLng)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SPEED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text("${liveDriverSpeed.toInt()} km/h", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBF360C))
                    }
                }
            }
        }

        // ── STAGE BADGES ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TRACKING STAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isStage1Active = !isStage2 && currentStatus != "completed"
                    StageBadge(
                        title = "STAGE 1",
                        subtitle = "Ambulance → Patient",
                        isActive = isStage1Active,
                        isDone = isStage2 || currentStatus == "completed",
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp).size(16.dp)
                    )

                    StageBadge(
                        title = "STAGE 2",
                        subtitle = "Patient → Hospital",
                        isActive = isStage2 && currentStatus != "completed",
                        isDone = currentStatus == "completed",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── DISPATCH TIMELINE ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DISPATCH TIMELINE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))

                val timelineSteps = listOf(
                    Triple("📞", "Emergency Reported", listOf("pending", "waiting", "assigned", "accepted", "on_the_way", "en_route", "enroute", "reached", "arrived", "patient_picked", "hospital_reached", "completed")),
                    Triple("🚑", "Ambulance Assigned", listOf("assigned", "accepted", "on_the_way", "en_route", "enroute", "reached", "arrived", "patient_picked", "hospital_reached", "completed")),
                    Triple("🚀", "En Route to Patient", listOf("on_the_way", "en_route", "enroute", "reached", "arrived", "patient_picked", "hospital_reached", "completed")),
                    Triple("📍", "Arrived at Scene", listOf("reached", "arrived", "patient_picked", "hospital_reached", "completed")),
                    Triple("🧑‍⚕️", "Patient Picked Up", listOf("patient_picked", "hospital_reached", "completed")),
                    Triple("🏥", "Hospital Reached", listOf("hospital_reached", "completed")),
                    Triple("✅", "Completed", listOf("completed"))
                )

                timelineSteps.forEach { (icon, label, doneStatuses) ->
                    val isDone = currentStatus in doneStatuses
                    val isCurrent = doneStatuses.contains(currentStatus) &&
                            timelineSteps.indexOf(Triple(icon, label, doneStatuses)).let { idx ->
                                idx == timelineSteps.size - 1 || currentStatus !in timelineSteps[idx + 1].third
                            }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(icon, fontSize = 14.sp)
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isDone -> Color(0xFF2E7D32)
                                else -> Color.Gray
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isDone) {
                            Text("✓", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── EMERGENCY DETAILS ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("EMERGENCY DETAILS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Patient", fontSize = 12.sp, color = Color.Gray)
                    Text(emergency.patientName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Type", fontSize = 12.sp, color = Color.Gray)
                    Text(emergency.emergencyType.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC62828))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Severity", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        emergency.severityLevel.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = when (emergency.severityLevel.lowercase()) {
                            "critical" -> Color(0xFFC62828)
                            "high" -> Color(0xFFEF5350)
                            "medium" -> Color(0xFFF57C00)
                            else -> Color(0xFF2E7D32)
                        }
                    )
                }
                if (!emergency.description.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Details", fontSize = 12.sp, color = Color.Gray)
                        Text(emergency.description, fontSize = 12.sp, modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End)
                    }
                }
            }
        }

        // ── DRIVER CARD ──
        if (!emergency.driverName.isNullOrBlank() || !emergency.driverPhone.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Assigned Ambulance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(emergency.driverName ?: "Driver", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (!emergency.driverPhone.isNullOrBlank()) {
                            Text(emergency.driverPhone!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!emergency.driverPhone.isNullOrBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${emergency.driverPhone}"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call Driver", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // ── HOSPITAL CARD ──
        if (!emergency.hospitalName.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocalHospital, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                    Column {
                        Text("Destination Hospital", fontSize = 11.sp, color = Color(0xFF388E3C))
                        Text(emergency.hospitalName!!, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B5E20))
                    }
                }
            }
        }

        // ── TURN-BY-TURN ROUTE STEPS ──
        if (routeSteps.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ROUTE DIRECTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF283593))
                        if (routeSummary != null) {
                            Text("via $routeSummary", fontSize = 10.sp, color = Color(0xFF5C6BC0))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    routeSteps.take(5).forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${index + 1}.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3949AB),
                                modifier = Modifier.width(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(step.instruction, fontSize = 11.sp, lineHeight = 15.sp)
                                Text("${step.distanceText} • ${step.durationText}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                    if (routeSteps.size > 5) {
                        Text("+ ${routeSteps.size - 5} more steps", fontSize = 10.sp,
                            color = Color(0xFF5C6BC0), modifier = Modifier.padding(start = 26.dp, top = 4.dp))
                    }
                }
            }
        }

        // ── CANCEL BUTTON ──
        if (currentStatus in listOf("waiting", "pending", "assigned", "accepted", "on_the_way")) {
            OutlinedButton(
                onClick = onCancelEmergency,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
            ) {
                Text("CANCEL EMERGENCY REQUEST", fontWeight = FontWeight.Bold)
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StageBadge(
    title: String,
    subtitle: String,
    isActive: Boolean,
    isDone: Boolean,
    modifier: Modifier
) {
    val bgColor = when {
        isDone -> Color(0xFF2E7D32)
        isActive -> Color(0xFF1565C0)
        else -> Color.LightGray.copy(alpha = 0.5f)
    }

    val contentColor = if (isActive || isDone) Color.White else Color.DarkGray

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Text(subtitle, fontSize = 10.sp, color = contentColor.copy(alpha = 0.9f))
        }
    }
}
