package com.tamer.studentphotomatcher

import com.tamer.studentphotomatcher.core.model.MatchClassification
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.matcher.StudentPhotoMatcherEngine
import org.junit.Assert.*
import org.junit.Test

class StudentPhotoMatcherEngineTest {

    @Test
    fun testArabicNameSimilarityWithNormalization() {
        // اختبار التسامح مع الهمزات والتشكيل والتاء المربوطة
        val nameA = "أحمد محمد علي إبراهيم"
        val nameB = "احمد محمد علي ابراهيم"
        val similarity1 = StudentPhotoMatcherEngine.calculateArabicNameSimilarity(nameA, nameB)
        assertEquals(100, similarity1)

        val nameC = "فاطمة محمود"
        val nameD = "فاطمه محمود"
        val similarity2 = StudentPhotoMatcherEngine.calculateArabicNameSimilarity(nameC, nameD)
        assertEquals(100, similarity2)

        // تطابق 3 كلمات من أصل 4
        val nameE = "أحمد محمد عبد الرحمن حسن"
        val nameF = "أحمد محمد عبد الرحمن"
        val similarity3 = StudentPhotoMatcherEngine.calculateArabicNameSimilarity(nameE, nameF)
        assertTrue(similarity3 >= 75)
    }

    @Test
    fun testConfirmedMatchByNationalIdAndName() {
        val student = Student(
            code = "102501",
            nationalId = "30501011234567",
            name = "أحمد محمد علي حسن"
        )
        val photo = PhotoItem(
            id = "photo_1",
            uriString = "content://media/1",
            originalName = "IMG_001.JPG",
            extension = "jpg",
            detectedName = "احمد محمد علي حسن",
            detectedNationalId = "30501011234567",
            detectedCode = "102501"
        )

        val result = StudentPhotoMatcherEngine.matchAll(listOf(student), listOf(photo))
        assertEquals(1, result.size)

        val matchedStudent = result[0]
        assertEquals(MatchClassification.CONFIRMED_MATCH, matchedStudent.classification)
        assertTrue(matchedStudent.confidenceScore >= 90)
        assertNotNull(matchedStudent.matchedPhoto)
        assertTrue(matchedStudent.isApproved) // ثقة فائقة تؤهل للاعتماد
    }

    @Test
    fun testDataConflictDetectionPreventsAutoApproval() {
        val studentA = Student(code = "102501", nationalId = "30501011234567", name = "أحمد علي")
        val studentB = Student(code = "102502", nationalId = "30502021234567", name = "محمود حسن")

        // صورة كودها في اسم الملف يشير لـ A ولكن الرقم القومي المكتشف يشير لـ B!
        val conflictedPhoto = PhotoItem(
            id = "photo_conflict",
            uriString = "content://media/2",
            originalName = "102501_student.jpg", // كود طالب A
            extension = "jpg",
            detectedNationalId = "30502021234567" // رقم قومي طالب B
        )

        val result = StudentPhotoMatcherEngine.matchAll(listOf(studentA, studentB), listOf(conflictedPhoto))
        val conflictMatches = result.filter { it.classification == MatchClassification.DATA_CONFLICT }

        assertTrue(conflictMatches.isNotEmpty())
        val matchedConflict = conflictMatches.first()
        assertFalse("يمنع اعتماد المطابقة المتعارضة تلقائياً!", matchedConflict.isApproved)
        assertTrue(matchedConflict.matchReason.contains("تعارض بيانات"))
    }

    @Test
    fun testRecordWithoutPhoto() {
        val student = Student(code = "102599", nationalId = "30509091234567", name = "خالد إبراهيم")
        val result = StudentPhotoMatcherEngine.matchAll(listOf(student), emptyList())

        assertEquals(1, result.size)
        assertEquals(MatchClassification.RECORD_WITHOUT_PHOTO, result[0].classification)
        assertNull(result[0].matchedPhoto)
        assertFalse(result[0].isApproved)
    }
}
