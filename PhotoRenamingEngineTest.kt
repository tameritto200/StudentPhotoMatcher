package com.tamer.studentphotomatcher

import com.tamer.studentphotomatcher.core.model.NamingTemplateOption
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.RenamingConfig
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.organizer.PhotoRenamingEngine
import org.junit.Assert.*
import org.junit.Test

class PhotoRenamingEngineTest {

    private val sampleStudent = Student(
        code = "102501",
        nationalId = "30501011234567",
        name = "أحمد محمد علي"
    )

    private val samplePhoto = PhotoItem(
        id = "1",
        uriString = "content://photo/1",
        originalName = "DSC_001.JPG",
        extension = "jpg"
    )

    @Test
    fun testTheSixNamingOptions() {
        // 1. اسم الطالب فقط
        val config1 = RenamingConfig(selectedOption = NamingTemplateOption.STUDENT_NAME_ONLY)
        val name1 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config1)
        assertEquals("احمد_محمد_علي.jpg", name1)

        // 2. الرقم القومي فقط
        val config2 = RenamingConfig(selectedOption = NamingTemplateOption.NATIONAL_ID_ONLY)
        val name2 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config2)
        assertEquals("30501011234567.jpg", name2)

        // 3. كود الطالب فقط
        val config3 = RenamingConfig(selectedOption = NamingTemplateOption.STUDENT_CODE_ONLY)
        val name3 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config3)
        assertEquals("102501.jpg", name3)

        // 4. اسم الطالب + كود الطالب
        val config4 = RenamingConfig(selectedOption = NamingTemplateOption.NAME_AND_CODE)
        val name4 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config4)
        assertEquals("احمد_محمد_علي_102501.jpg", name4)

        // 5. اسم الطالب + الرقم القومي
        val config5 = RenamingConfig(selectedOption = NamingTemplateOption.NAME_AND_NATIONAL_ID)
        val name5 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config5)
        assertEquals("احمد_محمد_علي_30501011234567.jpg", name5)

        // 6. الرقم القومي + كود الطالب
        val config6 = RenamingConfig(selectedOption = NamingTemplateOption.NATIONAL_ID_AND_CODE)
        val name6 = PhotoRenamingEngine.generateFileName(sampleStudent, samplePhoto, config6)
        assertEquals("30501011234567_102501.jpg", name6)
    }

    @Test
    fun testDuplicatePreventionSuffix() {
        val config = RenamingConfig(selectedOption = NamingTemplateOption.STUDENT_NAME_ONLY)
        val counts = HashMap<String, Int>()

        val studentA = sampleStudent
        val studentB = sampleStudent.copy(code = "999999") // نفس الاسم

        val nameA = PhotoRenamingEngine.generateFileName(studentA, samplePhoto, config, counts)
        val nameB = PhotoRenamingEngine.generateFileName(studentB, samplePhoto, config, counts)

        assertEquals("احمد_محمد_علي.jpg", nameA)
        assertEquals("احمد_محمد_علي_2.jpg", nameB)
        assertNotEquals(nameA, nameB)
    }

    @Test
    fun testSanitizesIllegalCharacters() {
        val studentWithBadChars = sampleStudent.copy(name = "أحمد/محمد*علي:حسن?")
        val config = RenamingConfig(selectedOption = NamingTemplateOption.STUDENT_NAME_ONLY)
        val fileName = PhotoRenamingEngine.generateFileName(studentWithBadChars, samplePhoto, config)

        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains("*"))
        assertFalse(fileName.contains(":"))
        assertFalse(fileName.contains("?"))
    }
}
