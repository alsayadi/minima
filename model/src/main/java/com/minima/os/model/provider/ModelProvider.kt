package com.minima.os.model.provider

import com.minima.os.core.model.ClassifiedIntent

interface ModelProvider {
    val name: String
    val isLocal: Boolean

    suspend fun classify(input: String): ClassifiedIntent
    suspend fun draft(prompt: String): String
    suspend fun plan(context: String): String
    suspend fun isAvailable(): Boolean
}
