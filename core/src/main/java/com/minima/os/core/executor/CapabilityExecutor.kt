package com.minima.os.core.executor

import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult

interface CapabilityExecutor {
    suspend fun execute(step: ActionStep): StepResult
}
