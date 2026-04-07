package com.minima.os.agent.executor

import com.minima.os.agent.approval.ApprovalEngine
import com.minima.os.agent.classifier.IntentClassifier
import com.minima.os.agent.logger.ActionLogger
import com.minima.os.data.memory.MemoryExtractor
import com.minima.os.data.memory.MemoryManager
import com.minima.os.agent.planner.Planner
import com.minima.os.core.executor.CapabilityExecutor
import com.minima.os.core.model.*
import com.minima.os.data.dao.TaskDao
import com.minima.os.data.entity.TaskEntity
import com.minima.os.model.router.ModelRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskExecutor @Inject constructor(
    private val classifier: IntentClassifier,
    private val planner: Planner,
    private val approvalEngine: ApprovalEngine,
    private val actionLogger: ActionLogger,
    private val capabilityExecutor: CapabilityExecutor,
    private val taskDao: TaskDao,
    private val modelRouter: ModelRouter,
    private val memoryManager: MemoryManager,
    private val memoryExtractor: MemoryExtractor
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _currentTask = MutableStateFlow<Task?>(null)
    val currentTask: StateFlow<Task?> = _currentTask.asStateFlow()

    private val _taskHistory = MutableStateFlow<List<Task>>(emptyList())
    val taskHistory: StateFlow<List<Task>> = _taskHistory.asStateFlow()

    suspend fun execute(input: String): Task {
        val taskId = UUID.randomUUID().toString()
        var task = Task(id = taskId, input = input)
        updateTask(task)

        try {
            // Retrieve memory context
            val memoryContext = try {
                memoryManager.getContextString(input)
            } catch (_: Exception) { "" }

            if (memoryContext.isNotBlank()) {
                android.util.Log.d("TaskExecutor", "Memory context: $memoryContext")
            }

            // Run memory maintenance in background
            try { memoryManager.maintain() } catch (_: Exception) {}

            // Classify — AI first, deterministic fallback
            task = task.copy(state = TaskState.CLASSIFYING, updatedAt = now())
            updateTask(task)

            var intent: ClassifiedIntent

            // Try AI (GPT-4o) first — smarter, more flexible
            try {
                val enrichedInput = if (memoryContext.isNotBlank()) {
                    "$memoryContext\n\nUser request: $input"
                } else input
                android.util.Log.d("TaskExecutor", "Trying LLM first...")
                intent = modelRouter.classify(enrichedInput)
                android.util.Log.d("TaskExecutor", "LLM returned: ${intent.type}/${intent.confidence} params=${intent.params}")
            } catch (e: Exception) {
                // AI unavailable (no key, no network) — fall back to deterministic
                android.util.Log.d("TaskExecutor", "LLM unavailable (${e.message}), falling back to deterministic")
                intent = classifier.classify(input)
                android.util.Log.d("TaskExecutor", "Deterministic returned: ${intent.type}/${intent.confidence}")
            }

            // If AI returned UNKNOWN, try deterministic
            if (intent.type == IntentType.UNKNOWN) {
                val deterministicIntent = classifier.classify(input)
                if (deterministicIntent.type != IntentType.UNKNOWN) {
                    android.util.Log.d("TaskExecutor", "AI said UNKNOWN, deterministic caught it: ${deterministicIntent.type}")
                    intent = deterministicIntent
                }
            }

            // If still UNKNOWN, treat it as ANSWER — never leave the user hanging
            if (intent.type == IntentType.UNKNOWN) {
                android.util.Log.d("TaskExecutor", "Still UNKNOWN, falling back to ANSWER")
                intent = ClassifiedIntent(
                    type = IntentType.ANSWER,
                    confidence = Confidence.LOW,
                    params = mapOf("query" to input),
                    rawInput = input
                )
            }

            task = task.copy(intent = intent, updatedAt = now())

            // Plan
            task = task.copy(state = TaskState.PLANNING, updatedAt = now())
            updateTask(task)

            val steps = planner.plan(taskId, intent)
            task = task.copy(steps = steps, updatedAt = now())

            if (steps.isEmpty()) {
                task = task.copy(
                    state = TaskState.FAILED,
                    error = "No actions available for this request.",
                    updatedAt = now()
                )
                updateTask(task)
                return task
            }

            // Execute steps
            task = task.copy(state = TaskState.EXECUTING, updatedAt = now())
            updateTask(task)

            val completedSteps = mutableListOf<ActionStep>()

            for (step in steps) {
                // Evaluate approval
                val decision = approvalEngine.evaluate(step)

                if (!decision.approved) {
                    val outcome = if (decision.level == ApprovalLevel.BLOCK) {
                        ActionOutcome.BLOCKED
                    } else {
                        ActionOutcome.REJECTED
                    }
                    actionLogger.log(step, outcome)

                    val updatedStep = step.copy(status = StepStatus.REJECTED)
                    completedSteps.add(updatedStep)

                    task = task.copy(
                        state = TaskState.CANCELLED,
                        steps = completedSteps,
                        error = decision.reason ?: "Action was not approved",
                        updatedAt = now()
                    )
                    updateTask(task)
                    return task
                }

                // Execute
                val startTime = System.currentTimeMillis()
                val executingStep = step.copy(status = StepStatus.EXECUTING)

                try {
                    val result = capabilityExecutor.execute(executingStep)
                    val duration = System.currentTimeMillis() - startTime

                    val finishedStep = executingStep.copy(
                        status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                        result = result
                    )
                    completedSteps.add(finishedStep)

                    val outcome = if (result.success) ActionOutcome.SUCCESS else ActionOutcome.FAILED
                    actionLogger.log(step, outcome, duration, result.error)

                    if (!result.success) {
                        task = task.copy(
                            state = TaskState.FAILED,
                            steps = completedSteps,
                            error = result.error ?: "Step failed",
                            updatedAt = now()
                        )
                        updateTask(task)
                        return task
                    }
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    actionLogger.log(step, ActionOutcome.FAILED, duration, e.message)

                    val failedStep = executingStep.copy(
                        status = StepStatus.FAILED,
                        result = StepResult(success = false, error = e.message)
                    )
                    completedSteps.add(failedStep)

                    task = task.copy(
                        state = TaskState.FAILED,
                        steps = completedSteps,
                        error = e.message ?: "Unexpected error",
                        updatedAt = now()
                    )
                    updateTask(task)
                    return task
                }
            }

            // All steps completed
            task = task.copy(
                state = TaskState.COMPLETED,
                steps = completedSteps,
                completedAt = now(),
                updatedAt = now()
            )
            updateTask(task)

            // Extract memories from completed task
            try {
                memoryExtractor.extractFromTask(task)
                android.util.Log.d("TaskExecutor", "Memories extracted from task ${task.id}")
            } catch (e: Exception) {
                android.util.Log.w("TaskExecutor", "Memory extraction failed: ${e.message}")
            }

            return task

        } catch (e: Exception) {
            task = task.copy(
                state = TaskState.FAILED,
                error = e.message ?: "Unexpected error",
                updatedAt = now()
            )
            updateTask(task)
            return task
        }
    }

    private suspend fun updateTask(task: Task) {
        _currentTask.value = task

        val history = _taskHistory.value.toMutableList()
        history.removeAll { it.id == task.id }
        history.add(0, task)
        _taskHistory.value = history.take(50)

        // Persist
        taskDao.insert(
            TaskEntity(
                id = task.id,
                input = task.input,
                state = task.state.name,
                intentType = task.intent?.type?.name,
                intentParams = task.intent?.params?.let { json.encodeToString(it) },
                stepsJson = json.encodeToString(task.steps),
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                completedAt = task.completedAt,
                error = task.error
            )
        )
    }

    private fun now() = System.currentTimeMillis()
}
