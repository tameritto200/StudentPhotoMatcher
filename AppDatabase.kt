package com.tamer.studentphotomatcher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tamer.studentphotomatcher.data.local.dao.PhotoDao
import com.tamer.studentphotomatcher.data.local.dao.SessionDao
import com.tamer.studentphotomatcher.data.local.dao.StudentDao
import com.tamer.studentphotomatcher.data.local.entity.PhotoEntity
import com.tamer.studentphotomatcher.data.local.entity.SessionEntity
import com.tamer.studentphotomatcher.data.local.entity.StudentEntity

@Database(
    entities = [
        SessionEntity::class,
        StudentEntity::class,
        PhotoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun studentDao(): StudentDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_photo_matcher_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
