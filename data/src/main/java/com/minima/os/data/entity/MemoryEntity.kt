package com.minima.os.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core memory table. Every fact the OS learns is a row here.
 * tier: STM (short-term, <48h) | MTM (mid-term, <30d) | LTM (long-term, permanent)
 * category: person | place | preference | pattern | fact | context
 */
@Entity(
    tableName = "memories",
    indices = [
        Index("tier"),
        Index("category"),
        Index("key"),
        Index("lastAccessedAt")
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val key: String,                    // e.g. "user.name", "person.sarah.phone", "preference.music"
    val value: String,                  // the actual memory content
    val category: String,               // person, place, preference, pattern, fact, context
    val tier: String,                   // STM, MTM, LTM
    val confidence: Float = 1.0f,       // 0.0-1.0, decays over time for STM/MTM
    val source: String,                 // how we learned this: "explicit", "inferred", "task:uuid"
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long,
    val accessCount: Int = 1,
    val expiresAt: Long? = null         // null = never expires (LTM)
)

/**
 * People the user interacts with.
 */
@Entity(
    tableName = "people",
    indices = [Index("name")]
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relationship: String? = null,   // "mom", "boss", "friend", "coworker"
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,          // free-form context
    val interactionCount: Int = 0,
    val lastInteractionAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Places the user mentions or visits.
 */
@Entity(
    tableName = "places",
    indices = [Index("name")]
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String? = null,           // "home", "work", "gym", "restaurant"
    val address: String? = null,
    val notes: String? = null,
    val visitCount: Int = 0,
    val lastMentionedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Usage patterns detected over time.
 */
@Entity(
    tableName = "patterns",
    indices = [Index("type")]
)
data class PatternEntity(
    @PrimaryKey val id: String,
    val type: String,                   // "routine", "preference", "habit"
    val description: String,            // "checks calendar every morning around 8am"
    val frequency: Int = 1,             // how many times observed
    val confidence: Float = 0.5f,
    val lastObservedAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)
