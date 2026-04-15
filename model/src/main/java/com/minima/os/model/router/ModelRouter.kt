package com.minima.os.model.router

import com.minima.os.core.model.ClassifiedIntent
import com.minima.os.model.cache.ResponseCache
import com.minima.os.model.provider.CloudModelProvider
import com.minima.os.model.provider.ModelProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRouter @Inject constructor(
    private val cloudProvider: CloudModelProvider,
    private val cache: ResponseCache
) {

    suspend fun classify(input: String): ClassifiedIntent {
        val cached = cache.getClassification(input)
        if (cached != null) return cached

        val provider = selectProvider(Operation.CLASSIFY)
        val result = provider.classify(input)

        cache.putClassification(input, result)
        return result
    }

    suspend fun draft(prompt: String): String {
        val cached = cache.getDraft(prompt)
        if (cached != null) return cached

        val provider = selectProvider(Operation.DRAFT)
        val result = provider.draft(prompt)

        cache.putDraft(prompt, result)
        return result
    }

    suspend fun plan(context: String): String {
        // Planning is always cloud — too complex for local
        val provider = cloudProvider
        return provider.plan(context)
    }

    private suspend fun selectProvider(operation: Operation): ModelProvider {
        // For now, always use cloud. Local model integration comes in Phase 5.
        // The router architecture is in place for when we add Gemini Nano.
        return cloudProvider
    }

    fun currentProviderName(): String = cloudProvider.currentProvider().name

    fun currentModelName(): String = cloudProvider.currentModel()

    private enum class Operation {
        CLASSIFY,
        DRAFT,
        PLAN
    }
}
