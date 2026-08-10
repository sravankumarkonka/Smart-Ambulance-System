package com.example.smartambulance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.UserViewModel

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHistoryScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: UserViewModel = viewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedEmergency by remember { mutableStateOf<Emergency?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    val isDriver = SessionManager.role == "driver"

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
    }

    // Calculations for Driver Statistics
    val totalResponded = history.size
    val completedCount = history.count { it.status.safeLower() == "completed" }
    val activeCount = history.count { listOf("assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached").contains(it.status.safeLower()) }

    val filteredHistory = history.filter { e ->
        val statusMatch = statusFilter == "all" || e.status.safeLower() == statusFilter.safeLower()
        val searchMatch = searchQuery.isEmpty() ||
            (e.patientName ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.emergencyType ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.hospitalName ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.id ?: "").contains(searchQuery, ignoreCase = true)
        statusMatch && searchMatch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isDriver) "Driver Dispatch Response History" else "Emergency History", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (isDriver) "Log of all emergency responses & hospital transfers" else "Records of your past emergency calls", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Driver Summary Stat Cards
            if (isDriver) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DriverStatCard(modifier = Modifier.weight(1f), title = "TOTAL RESPONDED", value = "$totalResponded", color = Color(0xFF1976D2))
                    DriverStatCard(modifier = Modifier.weight(1f), title = "COMPLETED", value = "$completedCount", color = Color(0xFF2E7D32))
                    DriverStatCard(modifier = Modifier.weight(1f), title = "ACTIVE IN PROGRESS", value = "$activeCount", color = Color(0xFFD97706))
                }
            }

            // Search Bar & Status Filters
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search patient, emergency type, or hospital...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statuses = listOf("all", "completed", "assigned", "arrived", "cancelled")
                items(statuses) { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text(st.uppercase(), fontSize = 10.sp) }
                    )
                }
            }

            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No dispatch history records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredHistory, key = { it.id ?: it.hashCode().toString() }) { emergency ->
                        val severityColor = when (emergency.severityLevel.safeLower()) {
                            "critical" -> Color(0xFFB71C1C)
                            "high" -> Color(0xFFE11D48)
                            "medium" -> Color(0xFFD97706)
                            else -> Color(0xFF16A34A)
                        }

                        Card(
                            onClick = { selectedEmergency = emergency },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Call ID: ${(emergency.id ?: "").take(8)}...",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                        modifier = Modifier
                                            .background(severityColor, shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = emergency.severityLevel.safeUpper(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text("Patient: ${emergency.patientName ?: "Emergency Patient"}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Emergency Type: ${emergency.emergencyType.safeUpper()}", fontSize = 13.sp)

                                emergency.hospitalName?.let { hName ->
                                    Text("🏥 Destination: $hName", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Status: ${emergency.status.safeUpper()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (emergency.status.safeLower() == "completed") Color(0xFF2E7D32) else Color(0xFF1976D2)
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
                }
            }
        }

        // Details Dialog
        selectedEmergency?.let { e ->
            AlertDialog(
                onDismissRequest = { selectedEmergency = null },
                title = { Text("Dispatch Call Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("📌 Call ID: ${e.id ?: "N/A"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("👤 Patient Name: ${e.patientName ?: "Emergency Patient"}", fontSize = 14.sp)
                        Text("🚨 Emergency Type: ${e.emergencyType.safeUpper()}", fontSize = 14.sp)
                        Text("⚡ Severity Level: ${e.severityLevel.safeUpper()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("📝 Description: ${e.description}", fontSize = 13.sp)
                        Text("📍 Coordinates: (${e.latitude}, ${e.longitude})", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        e.hospitalName?.let { Text("🏥 Hospital: $it", fontSize = 13.sp) }
                        Text("🔄 Status: ${e.status.safeUpper()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (e.status.safeLower() == "completed") Color(0xFF2E7D32) else Color(0xFF1976D2))
                        e.createdAt?.let { Text("📅 Timestamp: ${it.replace("T", " ").take(19)}", fontSize = 12.sp, color = Color.Gray) }
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedEmergency = null }) {
                        Text("CLOSE")
                    }
                }
            )
        }
    }
}

@Composable
fun DriverStatCard(modifier: Modifier = Modifier, title: String, value: String, color: Color) {
    Card(
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
