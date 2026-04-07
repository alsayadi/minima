package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ApprovalLevel {
    /** Execute silently, just log */
    AUTO,

    /** Execute and show a toast notification */
    NOTIFY,

    /** Show confirmation sheet, wait for user tap */
    CONFIRM,

    /** Deny the action, explain why */
    BLOCK
}
