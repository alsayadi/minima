package com.minima.os.agent.logger

import com.minima.os.core.model.ActionOutcome
import com.minima.os.core.model.ActionRecord
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.ApprovalLevel
import com.minima.os.data.dao.ActionDao
import com.minima.os.data.entity.ActionRecordEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionLogger @Inject constructor(
    private val actionDao: ActionDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun log(
        step: ActionStep,
        outcome: ActionOutcome,
        durationMs: Long? = null,
        error: String? = null
    ): ActionRecord {
        val record = ActionRecord(
            id = UUID.randomUUID().toString(),
            taskId = step.taskId,
            stepId = step.id,
            capabilityId = step.capabilityId,
            action = step.action,
            params = step.params,
            outcome = outcome,
            approvalLevel = step.approvalLevel,
            durationMs = durationMs,
            error = error
        )

        actionDao.insert(
            ActionRecordEntity(
                id = record.id,
                taskId = record.taskId,
                stepId = record.stepId,
                capabilityId = record.capabilityId,
                action = record.action,
                paramsJson = json.encodeToString(record.params),
                outcome = record.outcome.name,
                approvalLevel = record.approvalLevel.name,
                timestamp = record.timestamp,
                durationMs = record.durationMs,
                error = record.error
            )
        )

        return record
    }
}
