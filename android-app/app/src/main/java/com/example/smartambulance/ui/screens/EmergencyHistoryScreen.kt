package com.example.smartambulance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.ui.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHistoryScreen(
    onNavigate: (Any) -> Unit,
    viewModel: UserViewModel = hiltViewModel()
) {
    val activeEmergencies by viewModel.activeEmergencies.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startPatientRealtimeListener()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency History", fontWeight = FontWeight.Bold) },
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
        val hasAny = activeEmergencies.isNotEmpty() || history.isNotEmpty()

        if (!hasAny) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No emergency records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Active Emergencies Section ────────────────────────────
                if (activeEmergencies.isNotEmpty()) {
                    item {
                        Text(
                            text = "🔴 Active Emergencies (${activeEmergencies.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFFC62828)
                        )
                    }

                    items(activeEmergencies) { emergency ->
                        ActiveEmergencyCard(emergency = emergency, onNavigate = onNavigate)
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                // ── Past Emergencies Section ──────────────────────────────
                if (history.isNotEmpty()) {
                    item {
                        Text(
                            text = "Past Emergencies (${history.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    items(history) { emergency ->
                        HistoryEmergencyCard(emergency = emergency)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveEmergencyCard(
    emergency: com.example.smartambulance.data.model.Emergency,
    onNavigate: (Any) -> Unit
) {
    val severityColor = when (emergency.severityLevel.lowercase()) {
        "critical" -> Color(0xFFC62828)
        "high" -> Color(0xFFEF5350)
        "medium" -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }

    Card(
        onClick = {
            emergency.id?.let { id -> onNavigate(TrackAmbulance(id)) }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type: ${emergency.emergencyType.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Box(
                    modifier = Modifier
                        .background(severityColor, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = emergency.severityLevel.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text("Patient: ${emergency.patientName}", fontSize = 14.sp)
            Text("Description: ${emergency.description}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            emergency.hospitalName?.let {
                Text("Hospital: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status: ${emergency.status.replace("_", " ").uppercase()}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = emergency.createdAt?.take(16)?.replace("T", " ") ?: "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            if (!emergency.driverName.isNullOrBlank()) {
                Text(
                    text = "Driver: ${emergency.driverName}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(
                onClick = { emergency.id?.let { id -> onNavigate(TrackAmbulance(id)) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("TRACK EMERGENCY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryEmergencyCard(
    emergency: com.example.smartambulance.data.model.Emergency
) {
    val severityColor = when (emergency.severityLevel.lowercase()) {
        "critical" -> Color(0xFFC62828)
        "high" -> Color(0xFFEF5350)
        "medium" -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type: ${emergency.emergencyType.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Box(
                    modifier = Modifier
                        .background(severityColor, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = emergency.severityLevel.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text("Patient: ${emergency.patientName}", fontSize = 14.sp)
            Text("Description: ${emergency.description}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            emergency.hospitalName?.let {
                Text("Hospital: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    emergency.status.equals("completed", true) -> Color(0xFF2E7D32)
                    emergency.status.equals("cancelled", true) || emergency.status.equals("canceled", true) -> Color(0xFFC62828)
                    else -> Color.Gray
                }

                Text(
                    text = "Status: ${emergency.status.replace("_", " ").uppercase()}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = statusColor
                )
                Text(
                    text = emergency.createdAt?.take(16)?.replace("T", " ") ?: "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
