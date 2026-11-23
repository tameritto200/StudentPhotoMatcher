package com.tamer.studentphotomatcher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tamer.studentphotomatcher.data.local.entity.PhotoEntity
import com.tamer.studentphotomatcher.data.local.entity.SessionEntity
import com.tamer.studentphotomatcher.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM project_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String = "CURRENT_SESSION"): SessionEntity?

    @Query("SELECT * FROM project_sessions WHERE id = :id LIMIT 1")
    fun observeSession(id: String = "CURRENT_SESSION"): Flow<SessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)

    @Query("DELETE FROM project_sessions")
    suspend fun clearSession()
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY rowIndex ASC")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT * FROM students ORDER BY rowIndex ASC")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE code = :code LIMIT 1")
    suspend fun getStudentByCode(code: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Query("UPDATE students SET isApproved = :approved, isRejected = :rejected WHERE code = :code")
    suspend fun updateApproval(code: String, approved: Boolean, rejected: Boolean)

    @Query("DELETE FROM students")
    suspend fun clearStudents()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY originalName ASC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM photos ORDER BY originalName ASC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos")
    suspend fun clearPhotos()
}
