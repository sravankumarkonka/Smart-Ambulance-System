package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.isValidCoordinate
import com.example.smartambulance.data.repository.GoogleMapsRepository
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

    // Calculated route, ETA, and distance state
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var etaText by remember { mutableStateOf<String?>("Calculating...") }
    var distanceText by remember { mutableStateOf<String?>("") }

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
            }
        } else {
            etaText = "ETA unavailable"
            distanceText = "-- km"
            routePoints = emptyList()
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
                // Top 55%: Interactive Google Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
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

                // Bottom 45%: Information & Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                ) {
                    LiveTrackingInfoPanel(
                        emergency = e,
                        currentStatus = currentStatus,
                        isStage2 = isStage2,
                        etaText = etaText,
                        distanceText = distanceText,
                        liveDriverLat = liveDriverLat,
                        liveDriverLng = liveDriverLng,
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
            onMapLoaded = { mapLoaded = true }
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

        if (!mapLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Initializing Google Maps...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

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
                Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    currentStatus: String,
    isStage2: Boolean,
    etaText: String?,
    distanceText: String?,
    liveDriverLat: Double?,
    liveDriverLng: Double?,
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
        // ETA & DISTANCE METRICS
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
        }

        // STAGE BADGES
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

        // DRIVER CARD
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

        // HOSPITAL CARD
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

        // EXTERNAL GOOGLE MAPS BUTTON
        Button(
            onClick = {
                val destLat = if (isStage2 && hospitalValid) emergency.hospitalLatitude!! else (liveDriverLat ?: emergency.latitude)
                val destLng = if (isStage2 && hospitalValid) emergency.hospitalLongitude!! else (liveDriverLng ?: emergency.longitude)
                try {
                    val gmmIntentUri = Uri.parse("google.navigation:q=$destLat,$destLng&mode=d")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    context.startActivity(mapIntent)
                } catch (_: Exception) {
                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng&travelmode=driving")
                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("OPEN IN GOOGLE MAPS APP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // CANCEL BUTTON
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
