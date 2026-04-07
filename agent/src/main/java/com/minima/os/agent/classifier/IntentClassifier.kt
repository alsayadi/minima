package com.minima.os.agent.classifier

import com.minima.os.core.model.ClassifiedIntent

interface IntentClassifier {
    suspend fun classify(input: String): ClassifiedIntent
}
