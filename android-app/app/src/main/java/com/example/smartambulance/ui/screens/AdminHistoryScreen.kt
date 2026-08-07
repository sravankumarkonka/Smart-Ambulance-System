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
import com.example.smartambulance.data.model.Emergency
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHistoryScreen(
    onNavigate: (Any) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var emergencyHistory by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val emListener = db.collection("emergencies")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
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
                            driverName = d["driverName"] as? String,
                            hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                            createdAt = d["createdAt"] as? String ?: d["timestamp"] as? String
                        )
                    }
                    emergencyHistory = list.sortedByDescending { it.createdAt ?: "" }
                }
                loading = false
            }

        val auditListener = db.collection("audit_logs")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        mapOf(
                            "id" to doc.id,
                            "action" to (d["action"] as? String ?: "LOG"),
                            "performedBy" to (d["performedBy"] as? String ?: "System"),
                            "createdAt" to (d["createdAt"] as? String ?: "")
                        )
                    }
                    auditLogs = list.sortedByDescending { it["createdAt"] as String }
                }
            }

        onDispose {
            emListener.remove()
            auditListener.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin System History", fontWeight = FontWeight.Bold) },
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Emergencies (${emergencyHistory.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audit Logs (${auditLogs.size})") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else when (selectedTab) {
                0 -> {
                    if (emergencyHistory.isEmpty()) {
                        Text("No emergency records found.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(emergencyHistory) { item ->
                                val statusColor = when (item.status?.lowercase()) {
                                    "completed" -> Color(0xFF2E7D32)
                                    "cancelled" -> Color(0xFFC62828)
                                    else -> Color(0xFFF57C00)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.patientName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Box(
                                                modifier = Modifier
                                                    .background(statusColor, shape = RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(item.status?.uppercase() ?: "PENDING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text("Type: ${item.emergencyType.uppercase()} • Severity: ${item.severityLevel.uppercase()}", fontSize = 12.sp, color = Color.Gray)
                                        if (!item.driverName.isNullOrBlank()) {
                                            Text("Assigned Driver: 🚑 ${item.driverName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Text("Time: ${item.createdAt ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (auditLogs.isEmpty()) {
                        Text("No audit logs found.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(auditLogs) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(log["action"].toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("By: ${log["performedBy"]}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text(log["createdAt"].toString(), fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
