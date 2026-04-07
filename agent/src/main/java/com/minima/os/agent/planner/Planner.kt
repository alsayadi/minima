package com.minima.os.agent.planner

import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.ClassifiedIntent

interface Planner {
    suspend fun plan(taskId: String, intent: ClassifiedIntent): List<ActionStep>
}
