package com.tamer.studentphotomatcher.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamer.studentphotomatcher.core.model.ExportSummaryReport
import com.tamer.studentphotomatcher.core.model.NamingTemplateOption
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.organizer.PhotoRenamingEngine
import com.tamer.studentphotomatcher.ui.UiState
import com.tamer.studentphotomatcher.ui.theme.AccentGreen
import com.tamer.studentphotomatcher.ui.theme.PrimaryBlue

@Composable
fun ExportSettingsScreen(
    uiState: UiState,
    onSelectNamingOption: (NamingTemplateOption) -> Unit,
    onPickOutputFolderClick: () -> Unit,
    onStartExportClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val renamingConfig = uiState.renamingConfig

    // عينة طالب للعرض المباشر لاسم الملف
    val sampleStudent = uiState.students.firstOrNull { it.matchedPhoto != null } ?: Student(
        name = "أحمد محمد علي حسن",
        code = "104523",
        nationalId = "30501011234567"
    )
    val samplePhoto = sampleStudent.matchedPhoto ?: PhotoItem(
        id = "sample",
        uriString = "",
        originalName = "IMG_001.JPG",
        extension = "jpg"
    )

    val previewSampleName = PhotoRenamingEngine.generateFileName(
        student = sampleStudent,
        photo = samplePhoto,
        config = renamingConfig
    )

    val approvedCount = uiState.students.count { it.isApproved && it.matchedPhoto != null && !it.isRejected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // بطاقة المعاينة الفورية لاسم الملف
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Preview, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "معاينة اسم الصورة الناتج", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = previewSampleName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "سيتم تطبيق هذه القاعدة وتطهير الأسماء من أي رموز ممنوعة وتجنب التكرار.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // قائمة خيارات التسمية الـ 6 الرسمية
        Text(text = "خيارات تسمية الصور (المحددة رسمياً)", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                NamingTemplateOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (renamingConfig.selectedOption == option),
                                onClick = { onSelectNamingOption(option) }
                            )
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (renamingConfig.selectedOption == option),
                            onClick = { onSelectNamingOption(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.labelAr,
                            fontWeight = if (renamingConfig.selectedOption == option) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // اختيار مجلد الإخراج
        Text(text = "مجلد حفظ الصور المنظمة وملف Excel المحدث", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        Card(
            onClick = onPickOutputFolderClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.outputFolderUri != null) AccentGreen.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (uiState.outputFolderUri != null) Icons.Default.FolderSpecial else Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = if (uiState.outputFolderUri != null) AccentGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.outputFolderUri != null) "تم تحديد مجلد الإخراج بنجاح" else "اختر مجلد الحفظ (Output Folder)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (uiState.outputFolderUri != null) uiState.outputFolderUri.lastPathSegment ?: "مجلد محدد" else "اضغط لاختيار مجلد مستقل عبر نظام Android SAF",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // بطاقة ضمانات الأمان وعدم المساس بالأصول
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "ضمانات الأمان والتنفيذ:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                SecurityBullet("نسخ الصور المعتمدة فقط ($approvedCount صورة معتمدة جاهزة).")
                SecurityBullet("عدم تعديل أو نقل أو حذف أي ملف أو صورة في المجلد الأصلي.")
                SecurityBullet("إنشاء نسخة جديدة كاملة من ملف Excel مع تعبئة العمود K و L.")
                SecurityBullet("الحفاظ على ترتيب الصفوف وتنسيقات ومعادلات الإكسيل.")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // زر البدء النهائي في التصدير
        Button(
            onClick = onStartExportClick,
            enabled = uiState.outputFolderUri != null && uiState.excelUri != null && approvedCount > 0 && !uiState.isExporting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "بدء التصدير الآمن ($approvedCount صورة)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }

    // حوار تقدم التصدير
    if (uiState.isExporting && uiState.exportProgress != null) {
        val prog = uiState.exportProgress
        AlertDialog(
            onDismissRequest = {},
            title = { Text("جاري تصدير وتحديث الصور...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "تم نسخ: ${prog.current} من ${prog.total}")
                    Text(text = "الملف الحالي: ${prog.currentItemName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(
                        progress = { if (prog.total > 0) prog.current.toFloat() / prog.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {}
        )
    }

    // حوار ملخص نتائج التصدير
    if (uiState.exportReport != null) {
        val report = uiState.exportReport
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اكتمل التصدير بنجاح!")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الصور المعتمدة والمُصدّرة: ${report.approvedAndExportedCount}", fontWeight = FontWeight.Bold)
                    Text("الطلاب بلا صور / متخطون: ${report.studentsWithoutPhotosCount + report.skippedCount}")
                    Text("ملف الإكسيل الجديد: ${report.newExcelFileName}", color = MaterialTheme.colorScheme.primary)
                    Text("تم حفظ تقرير مفصل بصيغة CSV في نفس المجلد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { /* إغلاق التقرير */ }) {
                    Text("حسناً")
                }
            }
        )
    }
}

@Composable
fun SecurityBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("✓ ", color = AccentGreen, fontWeight = FontWeight.Bold)
        Text(text = text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
