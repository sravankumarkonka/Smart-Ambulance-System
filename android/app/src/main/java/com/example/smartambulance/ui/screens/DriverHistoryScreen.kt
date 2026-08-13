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
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHistoryScreen(
    onNavigate: (Any) -> Unit
) {
    val driverId = SessionManager.uid ?: ""
    val db = remember { FirebaseFirestore.getInstance() }
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
                    val list = snapshot.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val currentDriverId = d["driverId"] as? String
                        val assignedDriverId = d["assignedDriver"] as? String
                        if (currentDriverId != driverId && assignedDriverId != driverId) {
                            return@mapNotNull null
                        }

                        val createdAtRaw = d["createdAt"] ?: d["timestamp"]
                        val createdAtStr = when (createdAtRaw) {
                            is com.google.firebase.Timestamp -> createdAtRaw.toDate().toString()
                            is String -> createdAtRaw
                            else -> null
                        }

                        Emergency(
                            id = doc.id,
                            userId = d["userId"] as? String ?: "",
                            patientName = d["patientName"] as? String ?: "Patient",
                            emergencyType = d["emergencyType"] as? String ?: "general",
                            description = d["description"] as? String ?: "",
                            latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                            severityLevel = d["severityLevel"] as? String ?: d["severity"] as? String ?: "medium",
                            status = d["status"] as? String ?: "pending",
                            hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                            createdAt = createdAtStr
                        )
                    }
                    historyList = list.sortedByDescending { it.createdAt ?: "" }
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
                    text = "Total Completed: ${historyList.count { (it.status ?: "").lowercase() == "completed" }}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "Total Calls: ${historyList.size}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (historyList.isEmpty()) {
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
                                Text("Time: ${item.createdAt ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
