package com.tamer.studentphotomatcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tamer.studentphotomatcher.core.model.MatchClassification
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.ui.ReviewFilter
import com.tamer.studentphotomatcher.ui.UiState
import com.tamer.studentphotomatcher.ui.theme.AccentGreen
import com.tamer.studentphotomatcher.ui.theme.ErrorRed
import com.tamer.studentphotomatcher.ui.theme.PrimaryBlue
import com.tamer.studentphotomatcher.ui.theme.WarningAmber

@Composable
fun VisualReviewScreen(
    uiState: UiState,
    onApproveMatch: (studentCode: String) -> Unit,
    onRejectMatch: (studentCode: String) -> Unit,
    onEditDetectedText: (studentCode: String, newName: String, newNid: String) -> Unit,
    onAssignDifferentStudent: (studentCode: String, photo: com.tamer.studentphotomatcher.core.model.PhotoItem) -> Unit,
    onUnlinkPhoto: (studentCode: String) -> Unit,
    onSetFilter: (ReviewFilter) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onToggleBatchMode: (Boolean) -> Unit,
    onNextItem: () -> Unit,
    onPrevItem: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showStudentPickerSheet by remember { mutableStateOf(false) }

    // تصفية الطلاب حسب الفلتر والبحث
    val filteredStudents = remember(uiState.students, uiState.reviewFilter, uiState.searchQuery) {
        uiState.students.filter { student ->
            val matchesFilter = when (uiState.reviewFilter) {
                ReviewFilter.ALL -> true
                ReviewFilter.CONFIRMED -> student.classification == MatchClassification.CONFIRMED_MATCH
                ReviewFilter.REVIEW_NEEDED -> student.classification == MatchClassification.POTENTIAL_MATCH_REVIEW_NEEDED
                ReviewFilter.CONFLICTS -> student.classification == MatchClassification.DATA_CONFLICT
                ReviewFilter.UNMATCHED_PHOTOS -> student.matchedPhoto == null
                ReviewFilter.WITHOUT_PHOTOS -> student.matchedPhoto == null
            }

            val query = uiState.searchQuery.trim()
            val matchesQuery = if (query.isBlank()) true else {
                student.name.contains(query, ignoreCase = true) ||
                        student.code.contains(query) ||
                        student.nationalId.contains(query)
            }

            matchesFilter && matchesQuery
        }
    }

    val currentStudent = filteredStudents.getOrNull(uiState.currentReviewIndex)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // شريط التبديل بين العرض الفردي والعرض الجماعي والبحث
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSetSearchQuery,
                placeholder = { Text("بحث بالاسم أو الكود أو الرقم القومي...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { onToggleBatchMode(!uiState.isBatchReviewMode) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = if (uiState.isBatchReviewMode) Icons.Default.ViewCarousel else Icons.Default.GridView,
                    contentDescription = "تبديل وضع العرض",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // شرائح الفلاتر
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ReviewFilter.values()) { filter ->
                FilterChip(
                    selected = uiState.reviewFilter == filter,
                    onClick = { onSetFilter(filter) },
                    label = { Text(filter.labelAr, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredStudents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد نتائج مطابقة للفلتر المحدد",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.isBatchReviewMode) {
            // --- وضع المراجعة الجماعية (Batch Grid View) ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStudents) { student ->
                    StudentBatchCard(
                        student = student,
                        onApprove = { onApproveMatch(student.code) },
                        onReject = { onRejectMatch(student.code) }
                    )
                }
            }
        } else {
            // --- وضع المراجعة الفردية التفصيلية (Individual Review View) ---
            if (currentStudent != null) {
                IndividualReviewContent(
                    student = currentStudent,
                    currentIndex = uiState.currentReviewIndex,
                    totalCount = filteredStudents.size,
                    onApprove = { onApproveMatch(currentStudent.code) },
                    onReject = { onRejectMatch(currentStudent.code) },
                    onEditClick = { showEditDialog = true },
                    onChooseDifferentStudent = { showStudentPickerSheet = true },
                    onUnlink = { onUnlinkPhoto(currentStudent.code) },
                    onNext = onNextItem,
                    onPrev = onPrevItem
                )
            }
        }
    }

    // نافذة تعديل النصوص المكتشفة يدوياً
    if (showEditDialog && currentStudent != null) {
        var nameInput by remember { mutableStateOf(currentStudent.matchedPhoto?.detectedName ?: currentStudent.name) }
        var nidInput by remember { mutableStateOf(currentStudent.matchedPhoto?.detectedNationalId ?: currentStudent.nationalId) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("تعديل البيانات المكتشفة بالصورة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم الطالب المكتشف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nidInput,
                        onValueChange = { nidInput = it },
                        label = { Text("الرقم القومي (14 خانة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onEditDetectedText(currentStudent.code, nameInput, nidInput)
                    showEditDialog = false
                }) {
                    Text("حفظ واعتماد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun IndividualReviewContent(
    student: Student,
    currentIndex: Int,
    totalCount: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditClick: () -> Unit,
    onChooseDifferentStudent: () -> Unit,
    onUnlink: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // شريط التنقل العلوي بين الصور
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPrev,
                enabled = currentIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text("السابق", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }

            Text(
                text = "${currentIndex + 1} من $totalCount",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Button(
                onClick = onNext,
                enabled = currentIndex < totalCount - 1,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("التالي", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // صورة الطالب مع بطاقة المعاينة
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (student.matchedPhoto != null) {
                    AsyncImage(
                        model = student.matchedPhoto.uri,
                        contentDescription = "صورة الطالب",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("طالب بلا صورة في المجلد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // شارة حالة المطابقة ودرجة الثقة أعلى الصورة
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(student.classification.badgeColorHex)
                ) {
                    Text(
                        text = "${student.classification.labelAr} (${student.confidenceScore}%)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // مقارنة البيانات (Excel مقابل ما اكتشفه OCR)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "مقارنة بيانات الطالب", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)

                DataRow(label = "الاسم في ملف Excel:", value = student.name, isBold = true)
                DataRow(label = "الاسم المكتشف بـ OCR:", value = student.matchedPhoto?.detectedName ?: "—")
                DataRow(label = "كود التلميذ (Excel):", value = student.code)
                DataRow(label = "الرقم القومي (Excel):", value = student.nationalId)
                DataRow(label = "الرقم القومي المكتشف:", value = student.matchedPhoto?.detectedNationalId ?: "—")
                DataRow(label = "اسم ملف الصورة الأصلي:", value = student.matchedPhoto?.originalName ?: "—")
                DataRow(label = "سبب واقتراح المطابقة:", value = student.matchReason)
            }
        }

        // أزرار التحكم الرئيسية والاعتماد
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("اعتماد المطابقة", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("رفض المطابقة", fontWeight = FontWeight.Bold)
            }
        }

        // أزرار العمليات المتقدمة
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تعديل النص", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onUnlink,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("فك الربط", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StudentBatchCard(
    student: Student,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (student.matchedPhoto != null) {
                    AsyncImage(
                        model = student.matchedPhoto.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(student.classification.badgeColorHex)
                ) {
                    Text(
                        text = "${student.confidenceScore}%",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = student.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "كود: ${student.code}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onApprove,
                    modifier = Modifier.size(32.dp).background(AccentGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onReject,
                    modifier = Modifier.size(32.dp).background(ErrorRed.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DataRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value.ifBlank { "—" },
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
