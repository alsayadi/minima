package com.minima.os.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.minima.os.data.dao.ActionDao
import com.minima.os.data.dao.MemoryDao
import com.minima.os.data.dao.OutcomeDao
import com.minima.os.data.dao.TaskDao
import com.minima.os.data.dao.TuningChangeDao
import com.minima.os.data.entity.ActionRecordEntity
import com.minima.os.data.entity.MemoryEntity
import com.minima.os.data.entity.PatternEntity
import com.minima.os.data.entity.PersonEntity
import com.minima.os.data.entity.PlaceEntity
import com.minima.os.data.entity.TaskEntity
import com.minima.os.data.entity.TaskOutcomeEntity
import com.minima.os.data.entity.TuningChangeEntity

@Database(
    entities = [
        TaskEntity::class,
        ActionRecordEntity::class,
        MemoryEntity::class,
        PersonEntity::class,
        PlaceEntity::class,
        PatternEntity::class,
        TaskOutcomeEntity::class,
        TuningChangeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MinimaDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun actionDao(): ActionDao
    abstract fun memoryDao(): MemoryDao
    abstract fun outcomeDao(): OutcomeDao
    abstract fun tuningChangeDao(): TuningChangeDao

    companion object {
        fun create(context: Context): MinimaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MinimaDatabase::class.java,
                "minima.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
