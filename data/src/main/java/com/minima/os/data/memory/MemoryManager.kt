package com.minima.os.data.memory

import com.minima.os.data.dao.MemoryDao
import com.minima.os.data.entity.MemoryEntity
import com.minima.os.data.entity.PatternEntity
import com.minima.os.data.entity.PersonEntity
import com.minima.os.data.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central memory manager with 3-tier architecture:
 * - STM (Short-Term Memory): last 48h, auto-expires, low access threshold
 * - MTM (Mid-Term Memory): last 30d, promoted from STM after 3+ accesses
 * - LTM (Long-Term Memory): permanent, promoted from MTM after 7+ accesses, or explicit user statements
 */
@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao
) {

    companion object {
        const val STM = "STM"
        const val MTM = "MTM"
        const val LTM = "LTM"

        private const val STM_TTL_MS = 48 * 60 * 60 * 1000L      // 48 hours
        private const val MTM_TTL_MS = 30 * 24 * 60 * 60 * 1000L  // 30 days
    }

    // === Write operations ===

    /**
     * Remember a fact. Starts as STM unless explicitly marked higher.
     */
    suspend fun remember(
        key: String,
        value: String,
        category: String,
        source: String = "inferred",
        tier: String = STM
    ) {
        val now = System.currentTimeMillis()

        // Check if this key already exists — update instead of duplicate
        val existing = memoryDao.getByKey(key)
        if (existing != null) {
            memoryDao.upsertMemory(
                existing.copy(
                    value = value,
                    updatedAt = now,
                    lastAccessedAt = now,
                    accessCount = existing.accessCount + 1,
                    // Promote tier if new tier is higher
                    tier = higherTier(existing.tier, tier)
                )
            )
            return
        }

        val expiresAt = when (tier) {
            STM -> now + STM_TTL_MS
            MTM -> now + MTM_TTL_MS
            else -> null // LTM never expires
        }

        memoryDao.upsertMemory(
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                key = key,
                value = value,
                category = category,
                tier = tier,
                confidence = if (source == "explicit") 1.0f else 0.7f,
                source = source,
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now,
                expiresAt = expiresAt
            )
        )
    }

    /**
     * Remember a person.
     */
    suspend fun rememberPerson(
        name: String,
        relationship: String? = null,
        phone: String? = null,
        email: String? = null,
        notes: String? = null
    ) {
        val now = System.currentTimeMillis()
        val existing = memoryDao.findPerson(name).firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }

        if (existing != null) {
            memoryDao.upsertPerson(
                existing.copy(
                    relationship = relationship ?: existing.relationship,
                    phone = phone ?: existing.phone,
                    email = email ?: existing.email,
                    notes = if (notes != null) {
                        listOfNotNull(existing.notes, notes).joinToString("; ")
                    } else existing.notes,
                    interactionCount = existing.interactionCount + 1,
                    lastInteractionAt = now,
                    updatedAt = now
                )
            )
        } else {
            memoryDao.upsertPerson(
                PersonEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    relationship = relationship,
                    phone = phone,
                    email = email,
                    notes = notes,
                    interactionCount = 1,
                    lastInteractionAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Remember a place.
     */
    suspend fun rememberPlace(
        name: String,
        type: String? = null,
        address: String? = null,
        notes: String? = null
    ) {
        val now = System.currentTimeMillis()
        val existing = memoryDao.findPlace(name).firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }

        if (existing != null) {
            memoryDao.upsertPlace(
                existing.copy(
                    type = type ?: existing.type,
                    address = address ?: existing.address,
                    notes = notes ?: existing.notes,
                    visitCount = existing.visitCount + 1,
                    lastMentionedAt = now,
                    updatedAt = now
                )
            )
        } else {
            memoryDao.upsertPlace(
                PlaceEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    address = address,
                    notes = notes,
                    visitCount = 1,
                    lastMentionedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Record a usage pattern.
     */
    suspend fun recordPattern(
        type: String,
        description: String
    ) {
        val now = System.currentTimeMillis()
        val existing = memoryDao.getPatterns(type).firstOrNull {
            it.description.equals(description, ignoreCase = true)
        }

        if (existing != null) {
            memoryDao.upsertPattern(
                existing.copy(
                    frequency = existing.frequency + 1,
                    confidence = minOf(1.0f, existing.confidence + 0.1f),
                    lastObservedAt = now,
                    updatedAt = now
                )
            )
        } else {
            memoryDao.upsertPattern(
                PatternEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    description = description,
                    frequency = 1,
                    confidence = 0.3f,
                    lastObservedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    // === Read operations ===

    /**
     * Get all memories relevant to a query — searches keys and values.
     */
    suspend fun recall(query: String, limit: Int = 10): List<MemoryEntity> {
        // Touch accessed memories
        val results = memoryDao.search(query, limit)
        results.forEach { memoryDao.touch(it.id) }
        return results
    }

    /**
     * Get context for the agent — recent memories + relevant people + patterns.
     * This is what the agent reads before classifying/planning.
     */
    suspend fun getContextForAgent(input: String): MemoryContext {
        // 1. Search memories relevant to input
        val relevantMemories = memoryDao.search(input, 5)

        // 2. Extract names from input and find matching people
        val words = input.lowercase().split(" ")
        val relevantPeople = mutableListOf<PersonEntity>()
        for (word in words) {
            if (word.length >= 3) {
                relevantPeople.addAll(memoryDao.findPerson(word))
            }
        }

        // 3. Get user preferences
        val preferences = memoryDao.getByCategory("preference")

        // 4. Get recent patterns
        val patterns = memoryDao.getAllPatterns().take(5)

        // 5. Get user identity facts
        val identity = memoryDao.getByPrefix("user.")

        return MemoryContext(
            identity = identity,
            relevantMemories = relevantMemories,
            relevantPeople = relevantPeople.distinctBy { it.id },
            preferences = preferences,
            patterns = patterns
        )
    }

    /**
     * Build a text summary of memory context for the LLM.
     */
    suspend fun getContextString(input: String): String {
        val ctx = getContextForAgent(input)
        if (ctx.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("[Memory context]")

        if (ctx.identity.isNotEmpty()) {
            sb.appendLine("User: ${ctx.identity.joinToString(", ") { "${it.key}=${it.value}" }}")
        }

        if (ctx.relevantPeople.isNotEmpty()) {
            sb.appendLine("People mentioned: ${ctx.relevantPeople.joinToString(", ") {
                "${it.name}${it.relationship?.let { r -> " ($r)" } ?: ""}"
            }}")
        }

        if (ctx.preferences.isNotEmpty()) {
            sb.appendLine("Preferences: ${ctx.preferences.take(3).joinToString(", ") { "${it.key}: ${it.value}" }}")
        }

        if (ctx.relevantMemories.isNotEmpty()) {
            sb.appendLine("Relevant facts: ${ctx.relevantMemories.take(3).joinToString(", ") { it.value }}")
        }

        if (ctx.patterns.isNotEmpty()) {
            sb.appendLine("Patterns: ${ctx.patterns.take(2).joinToString(", ") { it.description }}")
        }

        return sb.toString().trim()
    }

    // === Maintenance ===

    /**
     * Run memory maintenance — expire STM, promote based on access count.
     */
    suspend fun maintain() {
        val now = System.currentTimeMillis()

        // Expire old STM
        memoryDao.expireSTM(now)

        // Promote frequently accessed STM → MTM
        memoryDao.promoteSTMtoMTM(minAccess = 3, newExpiry = now + MTM_TTL_MS)

        // Promote frequently accessed MTM → LTM
        memoryDao.promoteMTMtoLTM(minAccess = 7)
    }

    // === Observe for UI ===

    fun observeAllMemories(): Flow<List<MemoryEntity>> = memoryDao.observeAll()
    fun observePeople(): Flow<List<PersonEntity>> = memoryDao.observePeople()
    fun observePlaces(): Flow<List<PlaceEntity>> = memoryDao.observePlaces()
    fun observePatterns(): Flow<List<PatternEntity>> = memoryDao.observePatterns()

    // === Delete ===

    suspend fun forgetMemory(id: String) = memoryDao.deleteMemory(id)
    suspend fun forgetPerson(id: String) = memoryDao.deletePerson(id)
    suspend fun forgetPlace(id: String) = memoryDao.deletePlace(id)
    suspend fun forgetPattern(id: String) = memoryDao.deletePattern(id)

    // === Stats ===

    suspend fun getStats(): MemoryStats {
        return MemoryStats(
            totalMemories = memoryDao.countAll(),
            stmCount = memoryDao.countByTier(STM),
            mtmCount = memoryDao.countByTier(MTM),
            ltmCount = memoryDao.countByTier(LTM),
            peopleCount = memoryDao.getAllPeople().size,
            placesCount = memoryDao.getAllPlaces().size,
            patternsCount = memoryDao.getAllPatterns().size
        )
    }

    private fun higherTier(a: String, b: String): String {
        val rank = mapOf(STM to 0, MTM to 1, LTM to 2)
        return if ((rank[b] ?: 0) > (rank[a] ?: 0)) b else a
    }
}

data class MemoryContext(
    val identity: List<MemoryEntity>,
    val relevantMemories: List<MemoryEntity>,
    val relevantPeople: List<PersonEntity>,
    val preferences: List<MemoryEntity>,
    val patterns: List<PatternEntity>
) {
    fun isEmpty() = identity.isEmpty() && relevantMemories.isEmpty() &&
            relevantPeople.isEmpty() && preferences.isEmpty() && patterns.isEmpty()
}

data class MemoryStats(
    val totalMemories: Int,
    val stmCount: Int,
    val mtmCount: Int,
    val ltmCount: Int,
    val peopleCount: Int,
    val placesCount: Int,
    val patternsCount: Int
)
