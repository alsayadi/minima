package com.minima.os.capability.registry

import com.minima.os.core.executor.CapabilityExecutor
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import javax.inject.Inject
import javax.inject.Singleton

interface CapabilityProvider {
    val id: String
    suspend fun execute(step: ActionStep): StepResult
    fun supportedActions(): List<String>
}

@Singleton
class CapabilityRegistry @Inject constructor(
    private val providers: Map<String, @JvmSuppressWildcards CapabilityProvider>
) : CapabilityExecutor {

    override suspend fun execute(step: ActionStep): StepResult {
        val provider = providers[step.capabilityId]
            ?: return StepResult(
                success = false,
                error = "No capability provider found for '${step.capabilityId}'"
            )

        return try {
            provider.execute(step)
        } catch (e: Exception) {
            StepResult(success = false, error = e.message ?: "Capability execution failed")
        }
    }

    fun listCapabilities(): List<String> = providers.keys.toList()
}
