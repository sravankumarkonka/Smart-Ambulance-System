package com.example.smartambulance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.data.model.Hospital
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencyScreen(
    onNavigate: (Any) -> Unit,
    viewModel: UserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendation by viewModel.hospitalRecommendation.collectAsStateWithLifecycle()

    var patientName by remember { mutableStateOf("") }
    var emergencyType by remember { mutableStateOf("accident") }
    var description by remember { mutableStateOf("") }
    var severityLevel by remember { mutableStateOf("medium") }

    // Patient GPS coordinates (default to central Bangalore, editable manually or via GPS)
    var latitudeText by remember { mutableStateOf("12.9716") }
    var longitudeText by remember { mutableStateOf("77.5946") }
    var isDetectingLocation by remember { mutableStateOf(false) }

    // Selected image URI
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                imageBytes = inputStream?.readBytes()
                Toast.makeText(context, "Evidence image attached", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading image file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val latitude = latitudeText.toDoubleOrNull() ?: 12.9716
    val longitude = longitudeText.toDoubleOrNull() ?: 77.5946

    var selectedHospital by remember { mutableStateOf<Hospital?>(null) }

    val types = listOf("accident", "cardiac", "respiratory", "stroke", "pregnancy", "other")
    val severities = listOf("low", "medium", "high", "critical")

    // Debounce hospital recommendation to avoid rapid-fire API calls on every keystroke
    LaunchedEffect(latitude, longitude, severityLevel) {
        // Only fetch if coordinates are valid
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return@LaunchedEffect
        // Debounce: wait 1 second after last change before making the API call
        kotlinx.coroutines.delay(1000L)
        viewModel.recommendHospital(latitude, longitude, severityLevel)
    }

    LaunchedEffect(recommendation) {
        recommendation?.recommended?.let {
            selectedHospital = it
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UserUiState.Success -> {
                val emergencyId = state.message
                Toast.makeText(context, "🚨 Emergency request submitted! Connecting to live tracking...", Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                onNavigate(TrackAmbulance(emergencyId))
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
                title = { Text("Report Emergency", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(UserDashboard) }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Emergency Information", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = patientName,
                onValueChange = { patientName = it },
                label = { Text("Patient Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe the Emergency Situation") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )

            // Select Emergency Type
            Column {
                Text("Emergency Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.take(3).forEach { type ->
                            FilterChip(
                                selected = emergencyType == type,
                                onClick = { emergencyType = type },
                                label = { Text(type.uppercase()) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.drop(3).forEach { type ->
                            FilterChip(
                                selected = emergencyType == type,
                                onClick = { emergencyType = type },
                                label = { Text(type.uppercase()) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Severity Level
            Column {
                Text("Severity Level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    severities.forEach { severity ->
                        val chipColor = when (severity) {
                            "critical" -> Color(0xFFC62828)
                            "high" -> Color(0xFFEF5350)
                            "medium" -> Color(0xFFF57C00)
                            else -> Color(0xFF2E7D32)
                        }
                        FilterChip(
                            selected = severityLevel == severity,
                            onClick = { severityLevel = severity },
                            label = {
                                Text(
                                    text = severity.uppercase(),
                                    color = if (severityLevel == severity) Color.White else chipColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Divider()

            // GPS Location Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GPS Location Coordinates", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
    fun performLocationFetch() {
        isDetectingLocation = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    latitudeText = loc.latitude.toString()
                    longitudeText = loc.longitude.toString()
                    isDetectingLocation = false
                    Toast.makeText(context, "📍 GPS location auto-detected!", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback to active high-accuracy location request
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { currLoc ->
                            if (currLoc != null) {
                                latitudeText = currLoc.latitude.toString()
                                longitudeText = currLoc.longitude.toString()
                                Toast.makeText(context, "📍 Live GPS location detected!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not acquire GPS position. Ensure Location Services are ON.", Toast.LENGTH_LONG).show()
                            }
                            isDetectingLocation = false
                        }
                        .addOnFailureListener { err ->
                            Toast.makeText(context, "Location error: ${err.message}", Toast.LENGTH_SHORT).show()
                            isDetectingLocation = false
                        }
                }
            }.addOnFailureListener {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { currLoc ->
                        if (currLoc != null) {
                            latitudeText = currLoc.latitude.toString()
                            longitudeText = currLoc.longitude.toString()
                            Toast.makeText(context, "📍 Live GPS location detected!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not acquire GPS position. Ensure Location Services are ON.", Toast.LENGTH_LONG).show()
                        }
                        isDetectingLocation = false
                    }
                    .addOnFailureListener { err ->
                        Toast.makeText(context, "Location error: ${err.message}", Toast.LENGTH_SHORT).show()
                        isDetectingLocation = false
                    }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission missing", Toast.LENGTH_SHORT).show()
            isDetectingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            performLocationFetch()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    }

    Button(
        onClick = {
            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (fineGranted || coarseGranted) {
                performLocationFetch()
            } else {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        },
        enabled = !isDetectingLocation,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isDetectingLocation) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(Icons.Default.MyLocation, contentDescription = "Detect", modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (isDetectingLocation) "Detecting..." else "Auto-Detect GPS", fontSize = 12.sp)
    }

                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = { latitudeText = it },
                            label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { longitudeText = it },
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Accident Evidence Photo Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accident Evidence Image (Optional)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Pick Image")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELECT PHOTO")
                        }

                        if (selectedImageUri != null) {
                            Text("Photo Attached ✓", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text("No photo chosen", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Hospital Recommendation Cards
            Text("Recommended Hospital", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            recommendation?.let { rec ->
                rec.comparison.forEach { hospital ->
                    val isSelected = selectedHospital?.id == hospital.id
                    val isCritical = severityLevel == "critical" || severityLevel == "high"
                    val isNoIcu = hospital.availableIcuBeds == 0
                    
                    Card(
                        onClick = { selectedHospital = hospital },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hospital.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Distance: ${hospital.distanceKm} km",
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "ICU Beds: ${hospital.availableIcuBeds}/${hospital.totalIcuBeds} (${hospital.icuStatus})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isNoIcu) Color.Red else if (hospital.availableIcuBeds < 3) Color(0xFFF57C00) else Color(0xFF2E7D32)
                                )
                            }

                            if (isCritical && isNoIcu) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "WARNING: ICU beds full. Not recommended for critical cases.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState is UserUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = {
                        viewModel.reportEmergency(
                            patientName = patientName,
                            emergencyType = emergencyType,
                            description = description,
                            latitude = latitude,
                            longitude = longitude,
                            severityLevel = severityLevel,
                            hospitalName = selectedHospital?.name,
                            hospitalLatitude = selectedHospital?.latitude,
                            hospitalLongitude = selectedHospital?.longitude,
                            imageBytes = imageBytes
                        )
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("REQUEST EMERGENCY DISPATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
