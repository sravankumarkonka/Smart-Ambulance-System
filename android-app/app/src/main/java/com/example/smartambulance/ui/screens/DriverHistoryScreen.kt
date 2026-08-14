package com.example.smartambulance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.toEmergency
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHistoryScreen(
    onNavigate: (Any) -> Unit
) {
    val driverId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        ?: SessionManager.uid ?: ""
    val db = remember { FirebaseFirestore.getInstance() }
    var activeList by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var historyList by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(driverId) {
        if (driverId.isBlank()) {
            loading = false
            return@DisposableEffect onDispose { }
        }

        val listener = db.collection("emergencies")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val active = mutableListOf<Emergency>()
                    val history = mutableListOf<Emergency>()

                    snapshot.documents.forEach { doc ->
                        val item = doc.toEmergency() ?: return@forEach
                        val currentDriverId = doc.getString("driverId") ?: doc.getString("assignedDriver")
                        if (currentDriverId != driverId && item.driverId != driverId) {
                            return@forEach
                        }
                        if (com.example.smartambulance.data.model.isStatusHistory(item.status)) {
                            history.add(item)
                        } else if (com.example.smartambulance.data.model.isStatusActive(item.status)) {
                            active.add(item)
                        }
                    }

                    activeList = active.sortedByDescending { it.createdAt ?: "" }
                    historyList = history.sortedByDescending { it.createdAt ?: "" }
                }
                loading = false
            }

        onDispose { listener.remove() }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Response History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("back") }) {
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active: ${activeList.size}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = "Completed: ${historyList.count { (it.status ?: "").lowercase() == "completed" }}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "Total: ${activeList.size + historyList.size}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (activeList.isEmpty() && historyList.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No response history recorded yet.", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Active emergencies section
                    if (activeList.isNotEmpty()) {
                        item {
                            Text(
                                text = "🔴 Active Cases (${activeList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFC62828)
                            )
                        }

                        items(activeList) { item ->
                            val statusColor = when (item.status?.lowercase()) {
                                "assigned", "accepted" -> Color(0xFF1565C0)
                                "on_the_way", "en_route", "enroute" -> Color(0xFF0288D1)
                                "reached", "arrived" -> Color(0xFF7B1FA2)
                                "patient_picked" -> Color(0xFF388E3C)
                                "hospital_reached" -> Color(0xFF2E7D32)
                                else -> Color(0xFFF57C00)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.emergencyType.uppercase()} CALL",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(statusColor, shape = RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = (item.status ?: "ACTIVE").replace("_", " ").uppercase(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text("Patient: ${item.patientName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (!item.hospitalName.isNullOrBlank()) {
                                        Text("Destination: 🏥 ${item.hospitalName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("Severity: ${item.severityLevel.uppercase()}", fontSize = 12.sp, color = Color.Gray)
                                    Text("Time: ${item.createdAt?.take(16)?.replace("T", " ") ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    // History section
                    if (historyList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Past Responses (${historyList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        items(historyList) { item ->
                            val statusColor = when (item.status?.lowercase()) {
                                "completed" -> Color(0xFF2E7D32)
                                "cancelled" -> Color(0xFFC62828)
                                else -> Color(0xFFF57C00)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.emergencyType.uppercase()} CALL",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(statusColor, shape = RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = item.status?.uppercase() ?: "PENDING",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text("Patient: ${item.patientName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (!item.hospitalName.isNullOrBlank()) {
                                        Text("Destination: 🏥 ${item.hospitalName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("Severity: ${item.severityLevel.uppercase()}", fontSize = 12.sp, color = Color.Gray)
                                    Text("Time: ${item.createdAt?.take(16)?.replace("T", " ") ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
