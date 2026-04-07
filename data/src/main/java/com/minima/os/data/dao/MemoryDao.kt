package com.minima.os.data.dao

import androidx.room.*
import com.minima.os.data.entity.MemoryEntity
import com.minima.os.data.entity.PatternEntity
import com.minima.os.data.entity.PersonEntity
import com.minima.os.data.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    // === Memories ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE `key` LIKE :prefix || '%' ORDER BY lastAccessedAt DESC")
    suspend fun getByPrefix(prefix: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY lastAccessedAt DESC")
    suspend fun getByCategory(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE tier = :tier ORDER BY lastAccessedAt DESC")
    suspend fun getByTier(tier: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE value LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' ORDER BY accessCount DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY lastAccessedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY lastAccessedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("UPDATE memories SET lastAccessedAt = :now, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memories WHERE tier = 'STM' AND expiresAt < :now")
    suspend fun expireSTM(now: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET tier = 'MTM', expiresAt = :newExpiry WHERE tier = 'STM' AND accessCount >= :minAccess")
    suspend fun promoteSTMtoMTM(minAccess: Int = 3, newExpiry: Long)

    @Query("UPDATE memories SET tier = 'LTM', expiresAt = NULL WHERE tier = 'MTM' AND accessCount >= :minAccess")
    suspend fun promoteMTMtoLTM(minAccess: Int = 7)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE tier = :tier")
    suspend fun countByTier(tier: String): Int

    // === People ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity)

    @Query("SELECT * FROM people WHERE name LIKE '%' || :name || '%' LIMIT 5")
    suspend fun findPerson(name: String): List<PersonEntity>

    @Query("SELECT * FROM people ORDER BY lastInteractionAt DESC")
    suspend fun getAllPeople(): List<PersonEntity>

    @Query("SELECT * FROM people ORDER BY lastInteractionAt DESC")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("UPDATE people SET interactionCount = interactionCount + 1, lastInteractionAt = :now WHERE id = :id")
    suspend fun touchPerson(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: String)

    // === Places ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: PlaceEntity)

    @Query("SELECT * FROM places WHERE name LIKE '%' || :name || '%' LIMIT 5")
    suspend fun findPlace(name: String): List<PlaceEntity>

    @Query("SELECT * FROM places ORDER BY lastMentionedAt DESC")
    suspend fun getAllPlaces(): List<PlaceEntity>

    @Query("SELECT * FROM places ORDER BY lastMentionedAt DESC")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: String)

    // === Patterns ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(pattern: PatternEntity)

    @Query("SELECT * FROM patterns WHERE type = :type ORDER BY confidence DESC")
    suspend fun getPatterns(type: String): List<PatternEntity>

    @Query("SELECT * FROM patterns ORDER BY confidence DESC")
    suspend fun getAllPatterns(): List<PatternEntity>

    @Query("SELECT * FROM patterns ORDER BY confidence DESC")
    fun observePatterns(): Flow<List<PatternEntity>>

    @Query("DELETE FROM patterns WHERE id = :id")
    suspend fun deletePattern(id: String)
}
