package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.AuditLog
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.User
import com.example.smartambulance.ui.viewmodel.AdminUiState
import com.example.smartambulance.ui.viewmodel.AdminViewModel

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emergencies by viewModel.emergencies.collectAsStateWithLifecycle()
    val ambulances by viewModel.ambulances.collectAsStateWithLifecycle()
    val pendingDrivers by viewModel.pendingDrivers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var severityFilter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        viewModel.fetchAllAdminData()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AdminUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is AdminUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Metric Calculations matching Web Admin Dashboard
    val totalCount = emergencies.size
    val activeStatuses = listOf("pending", "waiting", "assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached")
    val activeCount = emergencies.count { activeStatuses.contains(it.status.safeLower()) }
    val criticalCount = emergencies.count { activeStatuses.contains(it.status.safeLower()) && it.severityLevel.safeLower() == "critical" }
    val completedCount = emergencies.count { it.status.safeLower() == "completed" }
    val pendingCount = emergencies.count { it.status.safeLower() == "pending" || it.status.safeLower() == "waiting" }

    val availableAmbs = ambulances.count { it.status.safeLower() == "available" }
    val busyAmbs = ambulances.count { it.status.safeLower() == "busy" }
    val totalDrivers = ambulances.size.coerceAtLeast(1)
    val fleetUtilization = Math.round((busyAmbs.toDouble() / totalDrivers) * 100).toInt()

    // Average Response Time
    val assignedCases = emergencies.filter { !it.assignedAt.isNullOrEmpty() && !it.createdAt.isNullOrEmpty() }
    val avgResponseTimeStr = if (assignedCases.isEmpty()) {
        "0.0 mins"
    } else {
        var totalDiffMs = 0L
        assignedCases.forEach { e ->
            try {
                val created = java.time.Instant.parse(e.createdAt).toEpochMilli()
                val assigned = java.time.Instant.parse(e.assignedAt).toEpochMilli()
                if (assigned > created) totalDiffMs += (assigned - created)
            } catch (_: Exception) {}
        }
        val mins = (totalDiffMs.toDouble() / assignedCases.size) / 60000.0
        String.format("%.1f mins", mins)
    }

    // Priority Queue sorting (Critical -> High -> Medium -> Low)
    val priorityQueue = emergencies
        .filter { listOf("pending", "waiting", "assigned", "arrived").contains(it.status.safeLower()) }
        .sortedWith(compareByDescending<Emergency> { e ->
            when (e.severityLevel.safeLower()) {
                "critical" -> 4
                "high" -> 3
                "medium" -> 2
                "low" -> 1
                else -> 2
            }
        }.thenByDescending { it.createdAt ?: "" })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Emergency monitoring & telemetry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(LiveMap) }) {
                        Icon(Icons.Default.Map, contentDescription = "View Live Map", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.fetchAllAdminData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        SessionManager.clearSession()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        onNavigate(Login)
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pending Driver Registration Requests Banner
            if (pendingDrivers.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "⚠️ Driver Registration Requests (${pendingDrivers.size} Pending Approval)",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        pendingDrivers.forEach { driver ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(driver.name.ifBlank { "Driver Candidate" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                                        Text("Email: ${driver.email} | Phone: ${driver.phone}", fontSize = 11.sp, color = Color.DarkGray)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { viewModel.approveDriver(driver.uid) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6BFF)),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Approve Driver", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.rejectDriver(driver.uid) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                            shape = RoundedCornerShape(20.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8 Metrics Grid (2 Rows of 4 Cards)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(modifier = Modifier.weight(1f), title = "Total Emergencies", value = "$totalCount", subtext = "$completedCount completed", color = Color(0xFF1976D2))
                    MetricCard(modifier = Modifier.weight(1f), title = "Live Active", value = "$activeCount", subtext = "$pendingCount awaiting dispatch", color = Color(0xFFD32F2F))
                    MetricCard(modifier = Modifier.weight(1f), title = "Critical Cases", value = "$criticalCount", subtext = "Immediate dispatch", color = Color(0xFFB71C1C))
                    MetricCard(modifier = Modifier.weight(1f), title = "Resolved Today", value = "$completedCount", subtext = "Completed", color = Color(0xFF2E7D32))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(modifier = Modifier.weight(1f), title = "Available Units", value = "$availableAmbs", subtext = "Ready for call", color = Color(0xFF2E7D32))
                    MetricCard(modifier = Modifier.weight(1f), title = "Busy Units", value = "$busyAmbs", subtext = "On active call", color = Color(0xFF1976D2))
                    MetricCard(modifier = Modifier.weight(1f), title = "Fleet Utilization", value = "$fleetUtilization%", subtext = "$busyAmbs / $totalDrivers active", color = Color(0xFF7B1FA2))
                    MetricCard(modifier = Modifier.weight(1f), title = "Avg Response", value = avgResponseTimeStr, subtext = "$totalDrivers drivers", color = Color(0xFFB45309))
                }
            }

            // Tabs Bar
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Priority Queue (${priorityQueue.size})", fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Analytics Breakdown", fontSize = 12.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Dispatch History (${emergencies.size})", fontSize = 12.sp) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit Logs (${auditLogs.size})", fontSize = 12.sp) })
            }

            if (uiState is AdminUiState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> PriorityQueueTab(queue = priorityQueue, onNavigate = onNavigate)
                1 -> AnalyticsBreakdownTab(emergencies = emergencies)
                2 -> DispatchHistoryTab(
                    emergencies = emergencies,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    statusFilter = statusFilter,
                    onStatusFilterChange = { statusFilter = it },
                    severityFilter = severityFilter,
                    onSeverityFilterChange = { severityFilter = it }
                )
                3 -> AdminAuditLogsTab(logs = auditLogs)
            }
        }
    }
}

@Composable
fun PriorityQueueTab(queue: List<Emergency>, onNavigate: (NavKey) -> Unit) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active priority emergencies pending dispatch.", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(queue, key = { it.id ?: it.hashCode().toString() }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.patientName ?: "Emergency Patient", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            val sevColor = when (item.severityLevel.safeLower()) {
                                "critical" -> Color(0xFFD32F2F)
                                "high" -> Color(0xFFE11D48)
                                "medium" -> Color(0xFFD97706)
                                else -> Color(0xFF16A34A)
                            }
                            Text(
                                item.severityLevel.safeUpper(),
                                color = sevColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Text("Type: ${item.emergencyType.safeUpper()}", fontSize = 12.sp, color = Color.DarkGray)
                        Text("Status: ${item.status.safeUpper()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1976D2))

                        item.driverName?.let {
                            Text("Assigned Driver: 🚑 $it", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        item.id?.let { emergencyId ->
                            Button(
                                onClick = { onNavigate(TrackAmbulance(emergencyId)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("TRACK LIVE AMBULANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsBreakdownTab(emergencies: List<Emergency>) {
    val total = emergencies.size.coerceAtLeast(1)

    val accidentCount = emergencies.count { it.emergencyType.safeLower() == "accident" }
    val cardiacCount = emergencies.count { it.emergencyType.safeLower() == "cardiac" }
    val respiratoryCount = emergencies.count { it.emergencyType.safeLower() == "respiratory" }
    val strokeCount = emergencies.count { it.emergencyType.safeLower() == "stroke" }
    val pregnancyCount = emergencies.count { it.emergencyType.safeLower() == "pregnancy" }
    val otherCount = emergencies.count { it.emergencyType.safeLower() == "other" || (it.emergencyType ?: "").isEmpty() }

    val criticalSev = emergencies.count { it.severityLevel.safeLower() == "critical" }
    val highSev = emergencies.count { it.severityLevel.safeLower() == "high" }
    val mediumSev = emergencies.count { it.severityLevel.safeLower() == "medium" || (it.severityLevel ?: "").isEmpty() }
    val lowSev = emergencies.count { it.severityLevel.safeLower() == "low" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📊 Incident Categories Analytics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    CategoryProgressRow("Accident", accidentCount, total, Color(0xFF1976D2))
                    CategoryProgressRow("Cardiac", cardiacCount, total, Color(0xFFD32F2F))
                    CategoryProgressRow("Respiratory", respiratoryCount, total, Color(0xFF0288D1))
                    CategoryProgressRow("Stroke", strokeCount, total, Color(0xFF7B1FA2))
                    CategoryProgressRow("Pregnancy", pregnancyCount, total, Color(0xFFC2185B))
                    CategoryProgressRow("Other / General", otherCount, total, Color(0xFF757575))
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📈 Severity Level Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    CategoryProgressRow("Critical", criticalSev, total, Color(0xFFB71C1C))
                    CategoryProgressRow("High", highSev, total, Color(0xFFE11D48))
                    CategoryProgressRow("Medium", mediumSev, total, Color(0xFFD97706))
                    CategoryProgressRow("Low", lowSev, total, Color(0xFF16A34A))
                }
            }
        }
    }
}

@Composable
fun DispatchHistoryTab(
    emergencies: List<Emergency>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    severityFilter: String,
    onSeverityFilterChange: (String) -> Unit
) {
    val filtered = emergencies.filter { e ->
        val statusMatch = statusFilter == "all" || e.status.safeLower() == statusFilter.safeLower()
        val severityMatch = severityFilter == "all" || e.severityLevel.safeLower() == severityFilter.safeLower()
        val searchMatch = searchQuery.isEmpty() ||
            (e.patientName ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.driverName ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.emergencyType ?: "").contains(searchQuery, ignoreCase = true) ||
            (e.id ?: "").contains(searchQuery, ignoreCase = true)
        statusMatch && severityMatch && searchMatch
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search patient, driver, or type...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val statuses = listOf("all", "completed", "assigned", "arrived", "waiting", "cancelled")
            items(statuses) { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { onStatusFilterChange(st) },
                    label = { Text(st.uppercase(), fontSize = 10.sp) }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id ?: it.hashCode().toString() }) { item ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.patientName ?: "Emergency Patient", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.severityLevel.safeUpper(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD97706))
                        }
                        Text("Type: ${item.emergencyType ?: "General"} | ID: ${(item.id ?: "").take(8)}...", fontSize = 11.sp, color = Color.Gray)
                        Text("Driver: ${item.driverName ?: "Unassigned"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Status: ${item.status.safeUpper()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogsTab(logs: List<AuditLog>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No system audit logs recorded.", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs, key = { it.id.ifEmpty { it.hashCode().toString() } }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action.safeUpper(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(log.createdAt ?: "", fontSize = 10.sp, color = Color.Gray)
                        }
                        Text("By: ${log.performedBy}", fontSize = 11.sp, color = Color.DarkGray)
                        log.targetUid?.let {
                            Text("Target: $it", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    color: Color
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(subtext, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
        }
    }
}

@Composable
fun CategoryProgressRow(label: String, count: Int, total: Int, color: Color) {
    val pct = Math.round((count.toDouble() / total) * 100).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$count ($pct%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { (count.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}
