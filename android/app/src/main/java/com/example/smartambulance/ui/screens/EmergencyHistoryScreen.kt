package com.example.smartambulance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHistoryScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: UserViewModel = viewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedEmergency by remember { mutableStateOf<Emergency?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
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
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No past emergency records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { emergency ->
                    val severityColor = when (emergency.severityLevel.lowercase()) {
                        "critical" -> Color(0xFFC62828)
                        "high" -> Color(0xFFEF5350)
                        "medium" -> Color(0xFFF57C00)
                        else -> Color(0xFF2E7D32)
                    }

                    Card(
                        onClick = { selectedEmergency = emergency },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                    text = "Status: ${emergency.status.uppercase()}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (emergency.status == "completed") Color(0xFF2E7D32) else Color.Red
                                )
                                Text(
                                    text = emergency.createdAt?.take(16)?.replace("T", " ") ?: "",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Text(
                                text = "👆 Tap to view all query details",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Full Details Modal Dialog
        selectedEmergency?.let { e ->
            AlertDialog(
                onDismissRequest = { selectedEmergency = null },
                title = {
                    Text("Emergency Query Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📌 ID: ${e.id ?: "N/A"}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("👤 Patient Name: ${e.patientName}", fontSize = 14.sp)
                        Text("🚨 Type: ${e.emergencyType.uppercase()}", fontSize = 14.sp)
                        Text("⚡ Severity Level: ${e.severityLevel.uppercase()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("📝 Description: ${e.description}", fontSize = 13.sp)
                        Text("📍 GPS Location: (${e.latitude}, ${e.longitude})", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text("🏥 Hospital: ${e.hospitalName ?: "Pending Assignment"}", fontSize = 13.sp)
                        Text("🚑 Driver ID: ${e.driverId ?: "Unassigned"}", fontSize = 13.sp)
                        Text("🔄 Status: ${e.status.uppercase()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (e.status == "completed") Color(0xFF2E7D32) else Color.Red)
                        e.createdAt?.let {
                            Text("📅 Date & Time: ${it.replace("T", " ").take(19)}", fontSize = 12.sp, color = Color.Gray)
                        }
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
