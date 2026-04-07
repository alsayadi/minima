package com.minima.os.model.cache

import com.minima.os.core.model.ClassifiedIntent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseCache @Inject constructor() {

    private val classificationCache = LinkedHashMap<String, CacheEntry<ClassifiedIntent>>(100, 0.75f, true)
    private val draftCache = LinkedHashMap<String, CacheEntry<String>>(50, 0.75f, true)

    private val ttlMs = 30 * 60 * 1000L // 30 minutes

    fun getClassification(input: String): ClassifiedIntent? {
        val key = normalize(input)
        val entry = classificationCache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
            entry.value
        } else {
            classificationCache.remove(key)
            null
        }
    }

    fun putClassification(input: String, result: ClassifiedIntent) {
        val key = normalize(input)
        classificationCache[key] = CacheEntry(result)
        evictIfNeeded(classificationCache, 100)
    }

    fun getDraft(prompt: String): String? {
        val key = normalize(prompt)
        val entry = draftCache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
            entry.value
        } else {
            draftCache.remove(key)
            null
        }
    }

    fun putDraft(prompt: String, result: String) {
        val key = normalize(prompt)
        draftCache[key] = CacheEntry(result)
        evictIfNeeded(draftCache, 50)
    }

    fun clear() {
        classificationCache.clear()
        draftCache.clear()
    }

    private fun normalize(input: String): String {
        return input.trim().lowercase()
    }

    private fun <T> evictIfNeeded(cache: LinkedHashMap<String, CacheEntry<T>>, maxSize: Int) {
        while (cache.size > maxSize) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    private data class CacheEntry<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis()
    )
}
