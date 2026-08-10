package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.smartambulance.data.model.User
import com.example.smartambulance.ui.viewmodel.SuperAdminUiState
import com.example.smartambulance.ui.viewmodel.SuperAdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: SuperAdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingAdmins by viewModel.pendingAdmins.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchAllData()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SuperAdminUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is SuperAdminUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val activeAdminsCount = allUsers.count { it.role == "admin" && (it.status == "active" || it.approved) }
    val activeDriversCount = allUsers.count { it.role == "driver" && (it.status == "active" || it.approved) }
    val patientCount = allUsers.count { it.role == "user" || it.role == "patient" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Super Admin Control", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("System Moderation & Approvals", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchAllData() }) {
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Analytics Summary (2x2 Cards Grid)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pending Approvals Card
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Pending Approvals", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${pendingAdmins.size}", color = Color(0xFFB45309), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Active Admins Card
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Active Admins", color = Color(0xFF1976D2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$activeAdminsCount", color = Color(0xFF1976D2), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active Drivers Card
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Active Drivers", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$activeDriversCount", color = Color(0xFF2E7D32), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Registered Patients Card
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Patients", color = Color(0xFF7B1FA2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$patientCount", color = Color(0xFF7B1FA2), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tab Rows
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pending (${pendingAdmins.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("All Users (${allUsers.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Audit Logs (${auditLogs.size})", fontSize = 12.sp) }
                )
            }

            if (uiState is SuperAdminUiState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> PendingAdminsTab(
                    pendingList = pendingAdmins,
                    onApprove = { viewModel.approveAdmin(it) },
                    onReject = { viewModel.rejectAdmin(it) }
                )
                1 -> AllUsersTab(
                    usersList = allUsers,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSuspend = { uid, role -> viewModel.suspendUser(uid, role) },
                    onActivate = { uid, role -> viewModel.activateUser(uid, role) },
                    onDelete = { uid, role -> viewModel.deleteUser(uid, role) }
                )
                2 -> AuditLogsTab(logs = auditLogs)
            }
        }
    }
}

@Composable
fun PendingAdminsTab(
    pendingList: List<User>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (pendingList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending admin approval requests.", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(pendingList, key = { it.uid.ifEmpty { it.email } }) { admin ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(admin.name.ifBlank { "Admin Candidate" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Email: ${admin.email}", fontSize = 13.sp)
                        Text("Phone: ${admin.phone}", fontSize = 13.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onReject(admin.uid) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("REJECT", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onApprove(admin.uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("APPROVE", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllUsersTab(
    usersList: List<User>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSuspend: (String, String) -> Unit,
    onActivate: (String, String) -> Unit,
    onDelete: (String, String) -> Unit
) {
    val filteredList = usersList.filter { u ->
        u.name.contains(searchQuery, ignoreCase = true) ||
        u.email.contains(searchQuery, ignoreCase = true) ||
        u.role.contains(searchQuery, ignoreCase = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search users by name, email, or role...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredList, key = { it.uid.ifEmpty { it.email } }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.name.ifBlank { user.email }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            
                            val roleColor = when (user.role) {
                                "super_admin" -> Color(0xFF7B1FA2)
                                "admin" -> Color(0xFF1976D2)
                                "driver" -> Color(0xFFB45309)
                                else -> Color(0xFF2E7D32)
                            }
                            Text(
                                text = user.role.uppercase(),
                                color = roleColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Text("Email: ${user.email}", fontSize = 12.sp, color = Color.DarkGray)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusColor = when (user.status) {
                                "active" -> Color(0xFF2E7D32)
                                "pending" -> Color(0xFFB45309)
                                else -> Color.Red
                            }
                            Text("Status: ${user.status.uppercase()}", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (user.approved) "✅ Approved" else "❌ Pending", fontSize = 11.sp)
                        }

                        if (user.role != "super_admin") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (user.status == "suspended") {
                                    OutlinedButton(
                                        onClick = { onActivate(user.uid, user.role) },
                                        modifier = Modifier.padding(end = 6.dp).height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Activate", fontSize = 11.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSuspend(user.uid, user.role) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                                        modifier = Modifier.padding(end = 6.dp).height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Suspend", fontSize = 11.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onDelete(user.uid, user.role) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogsTab(logs: List<AuditLog>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No system audit logs found.", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.action.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(log.createdAt ?: "", fontSize = 10.sp, color = Color.Gray)
                        }
                        Text("By: ${log.performedBy}", fontSize = 11.sp, color = Color.DarkGray)
                        log.targetUid?.let {
                            Text("Target UID: $it", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}
