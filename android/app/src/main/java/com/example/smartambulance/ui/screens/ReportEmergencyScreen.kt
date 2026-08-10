package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
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
import com.example.smartambulance.data.model.Hospital
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencyScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendation by viewModel.hospitalRecommendation.collectAsStateWithLifecycle()

    var patientName by remember { mutableStateOf("") }
    var emergencyType by remember { mutableStateOf("accident") }
    var description by remember { mutableStateOf("") }
    var severityLevel by remember { mutableStateOf("medium") }

    // Simulated patient coordinates (default to central Bangalore)
    var latitude by remember { mutableStateOf(12.9716) }
    var longitude by remember { mutableStateOf(77.5946) }

    var selectedHospital by remember { mutableStateOf<Hospital?>(null) }

    val types = listOf("accident", "cardiac", "respiratory", "stroke", "pregnancy", "other")
    val severities = listOf("low", "medium", "high", "critical")

    LaunchedEffect(latitude, longitude, severityLevel) {
        viewModel.recommendHospital(latitude, longitude, severityLevel)
    }

    LaunchedEffect(recommendation) {
        // Default to the recommended hospital if none is selected yet
        recommendation?.recommended?.let {
            selectedHospital = it
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UserUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
                onBack()
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
                    // Split in columns for better UI layout
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    color = if (severityLevel == severity) Color.White else chipColor
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
                            hospitalLongitude = selectedHospital?.longitude
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
