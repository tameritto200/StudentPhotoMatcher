package com.tamer.studentphotomatcher

import com.tamer.studentphotomatcher.core.model.ColumnMapping
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.excel.FastXlsxReader
import com.tamer.studentphotomatcher.excel.FastXlsxUpdater
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FastXlsxAndUpdaterTest {

    /**
     * إنشاء ملف إكسيل وهمي في الذاكرة يحاكي شيتات وزارة التربية والتعليم:
     * ورقة العمل: "بيانات الطلاب"
     * الصف 4 (Index 3): عناوين الأعمدة
     * الصف 5 (Index 4): بداية بيانات الطلاب
     */
    private fun createMockStudentWorkbookBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(baos)

        // 1. xl/workbook.xml
        zipOut.putNextEntry(ZipEntry("xl/workbook.xml"))
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheets>
                    <sheet name="بيانات الطلاب" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                </sheets>
            </workbook>
        """.trimIndent()
        zipOut.write(workbookXml.toByteArray(StandardCharsets.UTF_8))
        zipOut.closeEntry()

        // 2. xl/sharedStrings.xml
        val strings = listOf(
            "مدرسة النور الابتدائية",       // 0
            "إدارة التعليم",              // 1
            "العام الدراسي 2024",         // 2
            "م",                          // 3 (A4)
            "كود التلميذ",                // 4 (B4)
            "الرقم القومي",               // 5 (C4)
            "اسم التلميذ",                // 6 (D4)
            "اسم ملف الصورة الناتج",      // 7 (K4)
            "ملاحظات",                    // 8 (L4)
            "1",                          // 9 (A5)
            "102501",                     // 10 (B5)
            "03050101123456",             // 11 (C5 - رقم قومي يبدأ بـ 0 للتأكد من حفظه كـ String)
            "أحمد محمد علي حسن",          // 12 (D5)
            "2",                          // 13 (A6)
            "102502",                     // 14 (B6)
            "30502021234567",             // 15 (C6)
            "فاطمة محمود إبراهيم"         // 16 (D6)
        )

        zipOut.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
        val ssBuilder = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst count="${strings.size}" uniqueCount="${strings.size}" xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        for (s in strings) {
            ssBuilder.append("<si><t>$s</t></si>")
        }
        ssBuilder.append("</sst>")
        zipOut.write(ssBuilder.toString().toByteArray(StandardCharsets.UTF_8))
        zipOut.closeEntry()

        // 3. xl/worksheets/sheet1.xml
        zipOut.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>1</v></c></row>
                    <row r="3"><c r="A3" t="s"><v>2</v></c></row>
                    <row r="4">
                        <c r="A4" t="s"><v>3</v></c>
                        <c r="B4" t="s"><v>4</v></c>
                        <c r="C4" t="s"><v>5</v></c>
                        <c r="D4" t="s"><v>6</v></c>
                        <c r="K4" t="s"><v>7</v></c>
                        <c r="L4" t="s"><v>8</v></c>
                    </row>
                    <row r="5">
                        <c r="A5" t="s"><v>9</v></c>
                        <c r="B5" t="s"><v>10</v></c>
                        <c r="C5" t="s"><v>11</v></c>
                        <c r="D5" t="s"><v>12</v></c>
                    </row>
                    <row r="6">
                        <c r="A6" t="s"><v>13</v></c>
                        <c r="B6" t="s"><v>14</v></c>
                        <c r="C6" t="s"><v>15</v></c>
                        <c r="D6" t="s"><v>16</v></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()
        zipOut.write(sheetXml.toByteArray(StandardCharsets.UTF_8))
        zipOut.closeEntry()

        zipOut.finish()
        return baos.toByteArray()
    }

    @Test
    fun testReadMockXlsxWorkbook() {
        val mockBytes = createMockStudentWorkbookBytes()
        val result = FastXlsxReader.readWorkbook(ByteArrayInputStream(mockBytes))

        assertEquals("بيانات الطلاب", result.selectedSheetName)
        assertEquals(3, result.columnMapping.headerRowIndex) // الصف 4 هو صف العناوين
        assertEquals(4, result.columnMapping.dataStartRowIndex) // الصف 5 هو بداية الطلاب

        // التحقق من استخراج الطلاب
        assertEquals(2, result.students.size)

        val student1 = result.students[0]
        assertEquals("1", student1.serialNo)
        assertEquals("102501", student1.code)
        // التحقق الحاسم من الحفاظ على الرقم القومي كـ String وعدم فقد الصفر البادئ
        assertEquals("03050101123456", student1.nationalId)
        assertTrue(student1.nationalId.startsWith("0"))
        assertEquals("أحمد محمد علي حسن", student1.name)
        assertEquals(4, student1.rowIndex) // الصف 5 (index 4)

        val student2 = result.students[1]
        assertEquals("2", student2.serialNo)
        assertEquals("102502", student2.code)
        assertEquals("30502021234567", student2.nationalId)
        assertEquals("فاطمة محمود إبراهيم", student2.name)
        assertEquals(5, student2.rowIndex) // الصف 6 (index 5)
    }

    @Test
    fun testFastXlsxUpdaterCreatesCopyWithoutTouchingOriginal() {
        val originalBytes = createMockStudentWorkbookBytes()
        val updatedStudents = listOf(
            Student(
                serialNo = "1",
                code = "102501",
                nationalId = "03050101123456",
                name = "أحمد محمد علي حسن",
                rowIndex = 4,
                isApproved = true,
                exportedFileName = "102501_أحمد_محمد.jpg"
            ),
            Student(
                serialNo = "2",
                code = "102502",
                nationalId = "30502021234567",
                name = "فاطمة محمود إبراهيم",
                rowIndex = 5,
                isApproved = false,
                exportedFileName = null
            )
        )

        val outputBaos = ByteArrayOutputStream()
        FastXlsxUpdater.createUpdatedExcelCopy(
            originalInputStream = ByteArrayInputStream(originalBytes),
            targetOutputStream = outputBaos,
            students = updatedStudents,
            targetSheetPath = "xl/worksheets/sheet1.xml",
            columnMapping = ColumnMapping()
        )

        val updatedBytes = outputBaos.toByteArray()
        assertTrue(updatedBytes.isNotEmpty())

        // قراءة الملف المحدث والتحقق من تعبئة العمود K و L
        val readResult = FastXlsxReader.readWorkbook(ByteArrayInputStream(updatedBytes))
        assertEquals(2, readResult.students.size)

        val updatedSt1 = readResult.students[0]
        assertEquals("102501", updatedSt1.code)
        assertEquals("102501_أحمد_محمد.jpg", updatedSt1.exportedFileName)
        assertNotNull(updatedSt1.notes)
        assertTrue(updatedSt1.notes!!.contains("تم الاعتماد"))

        // نتأكد أن البيانات الأصلية للرقم القومي والاسم لم تتأثر
        assertEquals("03050101123456", updatedSt1.nationalId)
        assertEquals("أحمد محمد علي حسن", updatedSt1.name)
    }
}
