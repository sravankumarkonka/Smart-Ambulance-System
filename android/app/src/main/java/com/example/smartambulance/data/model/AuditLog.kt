package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditLog(
    val id: String = "",
    val action: String = "",
    val performedBy: String = "",
    val targetUid: String? = null,
    val createdAt: String? = null
)
