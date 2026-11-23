package com.tamer.studentphotomatcher

import com.tamer.studentphotomatcher.ocr.ArabicTextExtractor
import org.junit.Assert.*
import org.junit.Test

class ArabicTextExtractorTest {

    @Test
    fun testNormalizeDigits() {
        val eastern = "كود الطالب: ١٠٢٥٠١ والرقم القومي: ٣٠٥٠١٠١١٢٣٤٥٦٧"
        val normalized = ArabicTextExtractor.normalizeDigits(eastern)
        assertEquals("كود الطالب: 102501 والرقم القومي: 30501011234567", normalized)
    }

    @Test
    fun testExtractNationalIdStrict() {
        val text = "جمهورية مصر العربية - الرقم القومي: 30501011234567 - قطاع التعليم"
        val nationalId = ArabicTextExtractor.extractNationalId(text)
        assertNotNull(nationalId)
        assertEquals("30501011234567", nationalId)
        assertEquals(14, nationalId!!.length)
        assertTrue(nationalId.startsWith("3"))
    }

    @Test
    fun testExtractNationalIdWithEasternArabicDigits() {
        val text = "بطاقة الطالب الرقم القومي: ٣٠٥٠١٠١١٢٣٤٥٦٧"
        val nationalId = ArabicTextExtractor.extractNationalId(text)
        assertNotNull(nationalId)
        assertEquals("30501011234567", nationalId)
    }

    @Test
    fun testExtractStudentCode() {
        val text = "كود التلميذ: 104523 والرقم القومي: 30501011234567"
        val code = ArabicTextExtractor.extractStudentCode(text, nationalIdFound = "30501011234567")
        assertNotNull(code)
        assertEquals("104523", code)
    }

    @Test
    fun testExtractStudentNameFiltersNoise() {
        val lines = listOf(
            "جمهورية مصر العربية",
            "وزارة التربية والتعليم والتعليم الفني",
            "مدرسة الأمل الابتدائية المشتركة",
            "الصف السادس الابتدائي",
            "أحمد محمد علي حسن",
            "تاريخ الميلاد: 2012/05/15",
            "الرقم القومي: 31205150123456"
        )

        val extractedName = ArabicTextExtractor.extractStudentName(lines)
        assertNotNull(extractedName)
        assertEquals("أحمد محمد علي حسن", extractedName)
    }

    @Test
    fun testNeverFabricatesMissingData() {
        val unclearText = "صورة غير واضحة بدون أرقام أو نصوص صالحة"
        val nationalId = ArabicTextExtractor.extractNationalId(unclearText)
        assertNull(nationalId)

        val code = ArabicTextExtractor.extractStudentCode(unclearText, null)
        assertNull(code)
    }
}
