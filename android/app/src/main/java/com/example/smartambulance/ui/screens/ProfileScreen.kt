package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.data.SessionManager
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val role = SessionManager.role ?: "user"
    val name = SessionManager.name ?: "User"
    val email = SessionManager.email ?: ""
    val phone = SessionManager.phone ?: ""
    val uid = SessionManager.uid ?: ""

    // Load extra profile data from Firestore
    var profileData by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        profileData = doc.data ?: emptyMap()
                    }
                }
        }
    }

    val roleColor = when (role.lowercase()) {
        "super_admin" -> Color(0xFF7C3AED)
        "admin" -> Color(0xFF2563EB)
        "driver" -> Color(0xFFDC2626)
        else -> Color(0xFF059669)
    }

    val roleLabel = when (role.lowercase()) {
        "super_admin" -> "Super Admin"
        "admin" -> "Administrator"
        "driver" -> "Driver"
        else -> "User"
    }

    val roleIcon = when (role.lowercase()) {
        "super_admin" -> "👑"
        "admin" -> "🛡️"
        "driver" -> "🚑"
        else -> "👤"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = roleColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(roleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(roleIcon, fontSize = 42.sp)
            }

            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            // Role Badge
            Box(
                modifier = Modifier
                    .background(roleColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(roleLabel, color = roleColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Account Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    HorizontalDivider()

                    ProfileRow("📧 Email", email)
                    ProfileRow("📱 Phone", phone.ifEmpty { "Not set" })
                    ProfileRow("🔑 User ID", uid.take(12) + "...")
                    ProfileRow("🏷️ Role", roleLabel)

                    val status = profileData["status"]?.toString() ?: "active"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊 Status: ", fontSize = 14.sp)
                        val statusColor = if (status == "active") Color(0xFF16A34A) else Color(0xFFD97706)
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(status.uppercase(), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Driver-specific info
            if (role.lowercase() == "driver") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚑 Driver Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                        HorizontalDivider()
                        ProfileRow("Callsign", profileData["callsign"]?.toString() ?: "ALS-Unit-01")
                        ProfileRow("Vehicle", profileData["vehicleNumber"]?.toString() ?: "Not assigned")
                        ProfileRow("License", profileData["licenseNumber"]?.toString() ?: "Not set")
                    }
                }
            }

            // Admin-specific info
            if (role.lowercase() == "admin" || role.lowercase() == "super_admin") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛡️ Admin Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2563EB))
                        HorizontalDivider()
                        ProfileRow("Department", profileData["department"]?.toString() ?: "Emergency Services")
                        ProfileRow("Access Level", if (role == "super_admin") "Full Access" else "Standard")
                    }
                }
            }

            // Logout Button
            Button(
                onClick = {
                    SessionManager.clearSession()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    onNavigate(Login)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Icon(Icons.Default.ExitToApp, "Logout", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
