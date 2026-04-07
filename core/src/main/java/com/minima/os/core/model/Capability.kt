package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Capability(
    val id: String,
    val name: String,
    val description: String,
    val supportedIntents: List<IntentType>,
    val requiredPermissions: List<String> = emptyList(),
    val defaultApprovalLevel: ApprovalLevel = ApprovalLevel.CONFIRM
)
