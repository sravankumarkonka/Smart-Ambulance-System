package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartambulance.Login
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.User
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    onNavigate: (Any) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var pendingAdmins by remember { mutableStateOf<List<User>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    // Listen to pending admins
    DisposableEffect(Unit) {
        val listener = db.collection("users")
            .whereEqualTo("role", "admin")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    pendingAdmins = snapshot.documents.mapNotNull { doc ->
                        User(
                            uid = doc.id,
                            name = doc.getString("name") ?: doc.getString("displayName") ?: "Admin",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone") ?: "",
                            role = "admin",
                            status = "pending",
                            approved = false
                        )
                    }
                }
                loading = false
            }
        onDispose { listener.remove() }
    }

    // Listen to all users
    DisposableEffect(Unit) {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    allUsers = snapshot.documents.mapNotNull { doc ->
                        User(
                            uid = doc.id,
                            name = doc.getString("name") ?: doc.getString("displayName") ?: "User",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone") ?: "",
                            role = doc.getString("role") ?: "user",
                            status = doc.getString("status") ?: "pending",
                            approved = doc.getBoolean("approved") ?: false
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    fun approveAdmin(uid: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        db.collection("users").document(uid).update(mapOf("status" to "active", "approved" to true, "updatedAt" to now))
            .addOnSuccessListener {
                Toast.makeText(context, "Admin approved!", Toast.LENGTH_SHORT).show()
            }
    }

    fun rejectAdmin(uid: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        db.collection("users").document(uid).update(mapOf("status" to "rejected", "approved" to false, "updatedAt" to now))
            .addOnSuccessListener {
                Toast.makeText(context, "Admin rejected", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Super Admin Control Center", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = {
                        SessionManager.clearSession()
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        onNavigate(Login)
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pending (${pendingAdmins.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("All Users (${allUsers.size})") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else when (selectedTab) {
                0 -> {
                    if (pendingAdmins.isEmpty()) {
                        Text("No pending admin approval requests.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(pendingAdmins) { admin ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(admin.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text("Email: ${admin.email}", fontSize = 14.sp)
                                        Text("Phone: ${admin.phone}", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(
                                                onClick = { admin.uid?.let { approveAdmin(it) } },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Approve")
                                            }
                                            OutlinedButton(
                                                onClick = { admin.uid?.let { rejectAdmin(it) } },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Reject")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(allUsers) { user ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(user.name, fontWeight = FontWeight.Bold)
                                        Text("${user.email} • ${user.role.uppercase()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text(user.status?.uppercase() ?: "UNKNOWN", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
