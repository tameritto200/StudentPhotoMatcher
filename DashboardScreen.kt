package com.tamer.studentphotomatcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamer.studentphotomatcher.core.model.DashboardStats
import com.tamer.studentphotomatcher.ui.UiState
import com.tamer.studentphotomatcher.ui.theme.*

@Composable
fun DashboardScreen(
    uiState: UiState,
    onPickExcelClick: () -> Unit,
    onPickPhotosFolderClick: () -> Unit,
    onRunOcrAndMatchClick: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val stats = uiState.dashboardStats
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // بطاقة الترحيب والتقدم
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "مرحباً بك في راصد ومنظم صور الطلاب الذكي",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "مطابقة صور الطلاب، وقراءة الكود والرقم القومي بالذكاء الاصطناعي، وتحديث ملف Excel تلقائياً دون لمس الملفات الأصلية.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // شريط نسبة الإنجاز
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نسبة الإنجاز والاعتماد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(stats.progressPercentage * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { stats.progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
            }
        }

        // بطاقات الإحصائيات (شبكة 2x3)
        Text(
            text = "إحصائيات المطابقة وسجلات الطلاب",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "إجمالي الطلاب",
                value = stats.totalStudents.toString(),
                icon = Icons.Default.People,
                color = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "إجمالي الصور",
                value = stats.totalPhotos.toString(),
                icon = Icons.Default.PhotoLibrary,
                color = AccentGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "مطابقات مؤكدة",
                value = stats.matchedPhotos.toString(),
                icon = Icons.Default.CheckCircle,
                color = AccentGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "تنتظر المراجعة",
                value = stats.pendingReviewCount.toString(),
                icon = Icons.Default.PendingActions,
                color = WarningAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "صور بلا سجل",
                value = stats.unmatchedPhotosCount.toString(),
                icon = Icons.Default.BrokenImage,
                color = Color(0xFF64748B),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "طلاب بلا صور",
                value = stats.studentsWithoutPhotosCount.toString(),
                icon = Icons.Default.PersonOff,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }

        // خطوات العمل السريعة
        Text(
            text = "خطوات معالجة المشروع",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        WorkflowStepCard(
            stepNumber = "1",
            title = "استيراد ملف Excel (.xlsx)",
            description = if (uiState.excelUri != null) "تم تحميل ورقة [${uiState.selectedSheetName}] (${uiState.students.size} طالب)" else "اختر ملف إكسيل يحتوي على كود التلميذ والرقم القومي واسم التلميذ",
            icon = Icons.Default.Description,
            isCompleted = uiState.excelUri != null,
            onClick = onPickExcelClick
        )

        WorkflowStepCard(
            stepNumber = "2",
            title = "اختيار مجلد الصور الأصلي",
            description = if (uiState.photosFolderUri != null) "تم فحص المجلد (${uiState.photos.size} صورة جاهزة)" else "اختر مجلد الصور الشخصية للطلاب من الهاتف",
            icon = Icons.Default.FolderOpen,
            isCompleted = uiState.photosFolderUri != null,
            onClick = onPickPhotosFolderClick
        )

        WorkflowStepCard(
            stepNumber = "3",
            title = "تشغيل الفحص بالذكاء الاصطناعي والمطابقة",
            description = if (uiState.isOcrProcessing) uiState.ocrProgressText else "تحليل كامل الصور واستخراج الأسماء والأرقام القومية والمطابقة التلقائية",
            icon = Icons.Default.Psychology,
            isCompleted = stats.matchedPhotos > 0,
            isLoading = uiState.isOcrProcessing,
            onClick = onRunOcrAndMatchClick
        )

        WorkflowStepCard(
            stepNumber = "4",
            title = "المراجعة البصرية والاعتماد",
            description = "مراجعة كل صورة مع اسم الطالب ورقم الجلوس والرقم القومي واعتمادها أو تعديلها",
            icon = Icons.Default.Visibility,
            isCompleted = stats.exportedPhotosCount > 0,
            onClick = onNavigateToReview
        )

        WorkflowStepCard(
            stepNumber = "5",
            title = "التسمية والتصدير وتحديث Excel",
            description = "تطبيق نمط التسمية المختار وإنتاج الصور المعتمدة مع نسخة إكسيل جديدة تماماً",
            icon = Icons.Default.FileUpload,
            isCompleted = stats.exportedPhotosCount > 0,
            onClick = onNavigateToExport
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun WorkflowStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isCompleted) AccentGreen else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else if (isCompleted) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(text = stepNumber, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
