package com.tamer.studentphotomatcher.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tamer.studentphotomatcher.core.model.DashboardStats
import com.tamer.studentphotomatcher.core.model.ExportSummaryReport
import com.tamer.studentphotomatcher.core.model.MatchClassification
import com.tamer.studentphotomatcher.core.model.NamingTemplateOption
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.RenamingConfig
import com.tamer.studentphotomatcher.core.model.ReviewItem
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.data.local.AppDatabase
import com.tamer.studentphotomatcher.data.local.entity.PhotoEntity
import com.tamer.studentphotomatcher.data.local.entity.SessionEntity
import com.tamer.studentphotomatcher.data.local.entity.StudentEntity
import com.tamer.studentphotomatcher.excel.FastXlsxReader
import com.tamer.studentphotomatcher.executor.ExecutionEngine
import com.tamer.studentphotomatcher.executor.ExportProgress
import com.tamer.studentphotomatcher.matcher.StudentPhotoMatcherEngine
import com.tamer.studentphotomatcher.ocr.OcrEngineManager
import com.tamer.studentphotomatcher.organizer.PhotoRenamingEngine
import com.tamer.studentphotomatcher.scanner.PhotoScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReviewFilter(val labelAr: String) {
    ALL("الكل"),
    CONFIRMED("مطابقة مؤكدة"),
    REVIEW_NEEDED("تحتاج مراجعة"),
    CONFLICTS("تعارض في البيانات"),
    UNMATCHED_PHOTOS("صور بلا سجل"),
    WITHOUT_PHOTOS("طلاب بلا صور")
}

data class UiState(
    val excelUri: Uri? = null,
    val photosFolderUri: Uri? = null,
    val outputFolderUri: Uri? = null,
    val selectedSheetName: String = "",
    val availableSheets: List<String> = emptyList(),
    val students: List<Student> = emptyList(),
    val photos: List<PhotoItem> = emptyList(),
    val renamingConfig: RenamingConfig = RenamingConfig(),
    val dashboardStats: DashboardStats = DashboardStats(),
    val isOcrProcessing: Boolean = false,
    val ocrProgressText: String = "",
    val isExporting: Boolean = false,
    val exportProgress: ExportProgress? = null,
    val exportReport: ExportSummaryReport? = null,
    val currentReviewIndex: Int = 0,
    val isBatchReviewMode: Boolean = false,
    val reviewFilter: ReviewFilter = ReviewFilter.ALL,
    val searchQuery: String = "",
    val snackBarMessage: String? = null
)

class StudentPhotoMatcherViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val ocrManager = OcrEngineManager(application)
    private val scannerEngine = PhotoScannerEngine(application)
    private val executionEngine = ExecutionEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        restoreSessionIfAvailable()
    }

    /**
     * استيراد ملف Excel بصيغة XLSX
     */
    fun loadExcelFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val result = FastXlsxReader.readWorkbook(stream)
                    val parsedStudents = result.students

                    _uiState.update { state ->
                        state.copy(
                            excelUri = uri,
                            selectedSheetName = result.selectedSheetName,
                            availableSheets = result.sheets.map { it.name },
                            students = parsedStudents,
                            snackBarMessage = "تم تحميل ${parsedStudents.size} طالب بنجاح من ورقة [${result.selectedSheetName}]"
                        )
                    }

                    recalculateDashboardStats()
                    saveSessionToDb()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackBarMessage = "فشل قراءة ملف الإكسيل: ${e.message}") }
            }
        }
    }

    /**
     * استيراد مجلد الصور عبر Storage Access Framework
     */
    fun loadPhotosFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scanned = scannerEngine.scanFolder(treeUri)
                _uiState.update { state ->
                    state.copy(
                        photosFolderUri = treeUri,
                        photos = scanned,
                        snackBarMessage = "تم العثور على ${scanned.size} صورة في المجلد المحدد"
                    )
                }

                recalculateDashboardStats()
                saveSessionToDb()
            } catch (e: Exception) {
                _uiState.update { it.copy(snackBarMessage = "فشل فحص مجلد الصور: ${e.message}") }
            }
        }
    }

    /**
     * تشغيل نظام التعرف الضوئي OCR على دفعات والمطابقة الذكية متعددة المراحل
     */
    fun runOcrAndMatching() {
        val currentPhotos = _uiState.value.photos
        val currentStudents = _uiState.value.students

        if (currentPhotos.isEmpty() || currentStudents.isEmpty()) {
            _uiState.update { it.copy(snackBarMessage = "يرجى اختيار ملف الإكسيل ومجلد الصور أولاً") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isOcrProcessing = true, ocrProgressText = "بدء فحص الصور بالذكاء الاصطناعي...") }

            val analyzedPhotos = ArrayList<PhotoItem>(currentPhotos.size)
            val total = currentPhotos.size

            for ((index, photo) in currentPhotos.withIndex()) {
                _uiState.update {
                    it.copy(ocrProgressText = "معالجة الصورة ${index + 1} من $total: ${photo.originalName}")
                }

                val analysis = ocrManager.analyzeImage(photo.uri)
                val updatedPhoto = photo.copy(
                    detectedRawText = analysis.rawText,
                    detectedName = analysis.detectedName,
                    detectedNationalId = analysis.detectedNationalId,
                    detectedCode = analysis.detectedCode,
                    ocrConfidence = analysis.confidenceScore
                )
                analyzedPhotos.add(updatedPhoto)
            }

            _uiState.update { it.copy(ocrProgressText = "تشغيل خوارزمية المطابقة متعددة المراحل...") }

            // تشغيل محرك المطابقة المراحل 1 و 2 و 3 و 4
            val matchedStudents = StudentPhotoMatcherEngine.matchAll(currentStudents, analyzedPhotos)

            _uiState.update { state ->
                state.copy(
                    photos = analyzedPhotos,
                    students = matchedStudents,
                    isOcrProcessing = false,
                    ocrProgressText = "",
                    snackBarMessage = "اكتمل فحص ومطابقة جميع الصور بنجاح!"
                )
            }

            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * اعتماد مطابقة طالب
     */
    fun approveMatch(studentCode: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.students.map {
                    if (it.code == studentCode) it.copy(isApproved = true, isRejected = false) else it
                }
                state.copy(students = updated)
            }
            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * رفض مطابقة طالب
     */
    fun rejectMatch(studentCode: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.students.map {
                    if (it.code == studentCode) it.copy(isApproved = false, isRejected = true) else it
                }
                state.copy(students = updated)
            }
            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * تعديل النص المكتشف يدوياً بواسطة المستخدم
     */
    fun editDetectedData(studentCode: String, editedName: String, editedNationalId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.students.map { st ->
                    if (st.code == studentCode && st.matchedPhoto != null) {
                        val updatedPhoto = st.matchedPhoto.copy(
                            detectedName = editedName.ifBlank { st.matchedPhoto.detectedName },
                            detectedNationalId = editedNationalId.ifBlank { st.matchedPhoto.detectedNationalId }
                        )
                        st.copy(matchedPhoto = updatedPhoto, isApproved = true, isRejected = false)
                    } else st
                }
                state.copy(students = updated)
            }
            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * إسناد صورة معينة لطالب يدوياً
     */
    fun assignPhotoToStudent(studentCode: String, photo: PhotoItem) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.students.map { st ->
                    if (st.code == studentCode) {
                        st.copy(
                            matchedPhoto = photo.copy(assignedStudentCode = studentCode),
                            classification = MatchClassification.APPROVED_MANUAL,
                            confidenceScore = 100,
                            matchReason = "تم التعيين اليدوي بواسطة المستخدم",
                            isApproved = true,
                            isRejected = false
                        )
                    } else if (st.matchedPhoto?.id == photo.id) {
                        st.copy(matchedPhoto = null, classification = MatchClassification.RECORD_WITHOUT_PHOTO, isApproved = false)
                    } else st
                }
                state.copy(students = updated)
            }
            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * إلغاء ربط صورة الطالب
     */
    fun unlinkPhoto(studentCode: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.students.map { st ->
                    if (st.code == studentCode) {
                        st.copy(
                            matchedPhoto = null,
                            classification = MatchClassification.RECORD_WITHOUT_PHOTO,
                            confidenceScore = 0,
                            matchReason = "تم إلغاء الربط",
                            isApproved = false,
                            isRejected = false
                        )
                    } else st
                }
                state.copy(students = updated)
            }
            recalculateDashboardStats()
            saveSessionToDb()
        }
    }

    /**
     * تحديث خيار التسمية من الـ 6 المحددة
     */
    fun setNamingOption(option: NamingTemplateOption) {
        _uiState.update { state ->
            state.copy(renamingConfig = state.renamingConfig.copy(selectedOption = option))
        }
    }

    fun setReviewFilter(filter: ReviewFilter) {
        _uiState.update { it.copy(reviewFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setBatchReviewMode(isBatch: Boolean) {
        _uiState.update { it.copy(isBatchReviewMode = isBatch) }
    }

    fun nextReviewItem() {
        _uiState.update { state ->
            val max = (state.students.size - 1).coerceAtLeast(0)
            state.copy(currentReviewIndex = (state.currentReviewIndex + 1).coerceAtMost(max))
        }
    }

    fun prevReviewItem() {
        _uiState.update { state ->
            state.copy(currentReviewIndex = (state.currentReviewIndex - 1).coerceAtLeast(0))
        }
    }

    /**
     * تنفيذ التصدير الآمن
     */
    fun executeExport(outputFolderUri: Uri) {
        val state = _uiState.value
        val excelUri = state.excelUri ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExporting = true, outputFolderUri = outputFolderUri) }

            try {
                val report = executionEngine.executeExport(
                    students = state.students,
                    renamingConfig = state.renamingConfig,
                    outputFolderUri = outputFolderUri,
                    originalExcelUri = excelUri
                ) { progress ->
                    _uiState.update { it.copy(exportProgress = progress) }
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportReport = report,
                        snackBarMessage = "اكتمل التصدير بنجاح! تم تصدير ${report.approvedAndExportedCount} صورة وإنشاء نسخة Excel المحدثة"
                    )
                }

                recalculateDashboardStats()
                saveSessionToDb()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        snackBarMessage = "خطأ أثناء التصدير: ${e.message}"
                    )
                }
            }
        }
    }

    private fun recalculateDashboardStats() {
        val state = _uiState.value
        val totalStudents = state.students.size
        val totalPhotos = state.photos.size

        val matched = state.students.count { it.matchedPhoto != null }
        val pending = state.students.count { it.matchedPhoto != null && !it.isApproved && !it.isRejected }
        val withoutPhotos = state.students.count { it.matchedPhoto == null }
        val assignedPhotoIds = state.students.mapNotNull { it.matchedPhoto?.id }.toSet()
        val unmatchedPhotos = state.photos.count { !assignedPhotoIds.contains(it.id) }
        val approved = state.students.count { it.isApproved }

        val progress = if (totalStudents > 0) (approved.toFloat() / totalStudents.toFloat()) else 0f

        val stats = DashboardStats(
            totalStudents = totalStudents,
            totalPhotos = totalPhotos,
            matchedPhotos = matched,
            pendingReviewCount = pending,
            unmatchedPhotosCount = unmatchedPhotos,
            studentsWithoutPhotosCount = withoutPhotos,
            exportedPhotosCount = approved,
            progressPercentage = progress
        )

        _uiState.update { it.copy(dashboardStats = stats) }
    }

    private fun saveSessionToDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val session = SessionEntity(
                excelUri = state.excelUri?.toString(),
                photosFolderUri = state.photosFolderUri?.toString(),
                outputFolderUri = state.outputFolderUri?.toString(),
                sheetName = state.selectedSheetName,
                namingOption = state.renamingConfig.selectedOption.name,
                totalStudents = state.students.size,
                totalPhotos = state.photos.size,
                matchedCount = state.dashboardStats.matchedPhotos,
                approvedCount = state.dashboardStats.exportedPhotosCount
            )
            db.sessionDao().saveSession(session)

            val studentEntities = state.students.map {
                StudentEntity(
                    code = it.code,
                    nationalId = it.nationalId,
                    name = it.name,
                    serialNo = it.serialNo,
                    rowIndex = it.rowIndex,
                    classification = it.classification.name,
                    confidenceScore = it.confidenceScore,
                    matchReason = it.matchReason,
                    matchedPhotoUri = it.matchedPhoto?.uriString,
                    isApproved = it.isApproved,
                    isRejected = it.isRejected,
                    exportedFileName = it.exportedFileName,
                    notes = it.notes
                )
            }
            db.studentDao().insertStudents(studentEntities)
        }
    }

    private fun restoreSessionIfAvailable() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = db.sessionDao().getSession() ?: return@launch
            val savedStudents = db.studentDao().getAllStudents()

            if (savedStudents.isNotEmpty()) {
                val restored = savedStudents.map {
                    Student(
                        serialNo = it.serialNo,
                        code = it.code,
                        nationalId = it.nationalId,
                        name = it.name,
                        rowIndex = it.rowIndex,
                        classification = try { MatchClassification.valueOf(it.classification) } catch (_: Exception) { MatchClassification.RECORD_WITHOUT_PHOTO },
                        confidenceScore = it.confidenceScore,
                        matchReason = it.matchReason,
                        isApproved = it.isApproved,
                        isRejected = it.isRejected,
                        exportedFileName = it.exportedFileName,
                        notes = it.notes,
                        matchedPhoto = if (it.matchedPhotoUri != null) {
                            PhotoItem(
                                id = it.matchedPhotoUri,
                                uriString = it.matchedPhotoUri,
                                originalName = it.matchedPhotoUri.substringAfterLast('/'),
                                extension = it.matchedPhotoUri.substringAfterLast('.', "jpg")
                            )
                        } else null
                    )
                }

                _uiState.update { state ->
                    state.copy(
                        excelUri = session.excelUri?.let { Uri.parse(it) },
                        photosFolderUri = session.photosFolderUri?.let { Uri.parse(it) },
                        outputFolderUri = session.outputFolderUri?.let { Uri.parse(it) },
                        selectedSheetName = session.sheetName ?: "",
                        students = restored,
                        renamingConfig = state.renamingConfig.copy(
                            selectedOption = try { NamingTemplateOption.valueOf(session.namingOption) } catch (_: Exception) { NamingTemplateOption.NAME_AND_CODE }
                        )
                    )
                }

                recalculateDashboardStats()
            }
        }
    }

    fun clearSession() {
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().clearSession()
            db.studentDao().clearStudents()
            db.photoDao().clearPhotos()
            _uiState.value = UiState()
        }
    }

    fun dismissSnackBar() {
        _uiState.update { it.copy(snackBarMessage = null) }
    }
}
