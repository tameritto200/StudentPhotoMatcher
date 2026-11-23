package com.tamer.studentphotomatcher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * كائن تخزين جلسة العمل الحالية في قاعدة بيانات Room
 * لحفظ واسترجاع حالة العمل دون فقدان أي بيانات
 */
@Entity(tableName = "project_sessions")
data class SessionEntity(
    @PrimaryKey val id: String = "CURRENT_SESSION",
    val excelUri: String? = null,
    val photosFolderUri: String? = null,
    val outputFolderUri: String? = null,
    val sheetName: String? = null,
    val namingOption: String = "NAME_AND_CODE",
    val totalStudents: Int = 0,
    val totalPhotos: Int = 0,
    val matchedCount: Int = 0,
    val approvedCount: Int = 0,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

/**
 * كائن تخزين بيانات الطالب
 */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val code: String,
    val nationalId: String,
    val name: String,
    val serialNo: String,
    val rowIndex: Int,
    val classification: String,
    val confidenceScore: Int,
    val matchReason: String,
    val matchedPhotoUri: String?,
    val isApproved: Boolean,
    val isRejected: Boolean,
    val exportedFileName: String?,
    val notes: String?
)

/**
 * كائن تخزين بيانات الصورة ومخرجات التعرف الضوئي OCR
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val originalName: String,
    val extension: String,
    val sizeBytes: Long,
    val captureDateMillis: Long?,
    val detectedRawText: String,
    val detectedName: String?,
    val detectedNationalId: String?,
    val detectedCode: String?,
    val ocrConfidence: Int,
    val assignedStudentCode: String?,
    val isApproved: Boolean,
    val isSkipped: Boolean
)
