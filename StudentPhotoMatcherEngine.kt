package com.tamer.studentphotomatcher.matcher

import com.tamer.studentphotomatcher.core.model.MatchClassification
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.Student

/**
 * المحرك الذكي متعدد المراحل لمطابقة صور الطلاب مع سجلات Excel
 * ينفذ المراحل الأربعة بدقة:
 * المرحلة الأولى: كود الطالب في اسم ملف الصورة
 * المرحلة الثانية: مقارنة وتطبيع الاسم العربي
 * المرحلة الثالثة: مقارنة الرقم القومي كسلسلة نصية
 * المرحلة الرابعة: حساب درجة الثقة وكشف التعارضات والتصنيف
 *
 * تنبيه نظامي: يُمنع منعاً باتاً استخدام التعرف على الوجوه لتحديد هوية الطالب.
 */
object StudentPhotoMatcherEngine {

    data class MatchEvaluation(
        val student: Student,
        val photo: PhotoItem?,
        val classification: MatchClassification,
        val confidenceScore: Int,
        val reason: String
    )

    /**
     * تشغيل خوارزمية المطابقة متعددة المراحل الشاملة
     */
    fun matchAll(
        students: List<Student>,
        photos: List<PhotoItem>
    ): List<Student> {
        val studentByCode = students.filter { it.code.isNotBlank() }.associateBy { it.code }
        val studentByNationalId = students.filter { it.nationalId.isNotBlank() }.associateBy { it.nationalId }

        val evaluatedStudents = HashMap<String, MatchEvaluation>()
        val assignedPhotoIds = HashSet<String>()

        for (photo in photos) {
            val eval = evaluatePhotoMatch(photo, students, studentByCode, studentByNationalId)
            if (eval != null && eval.photo != null) {
                val studentId = eval.student.id
                val existing = evaluatedStudents[studentId]

                if (existing == null || eval.confidenceScore > existing.confidenceScore) {
                    if (existing?.photo != null) {
                        assignedPhotoIds.remove(existing.photo.id)
                    }
                    evaluatedStudents[studentId] = eval
                    assignedPhotoIds.add(photo.id)
                }
            }
        }

        return students.map { originalStudent ->
            val eval = evaluatedStudents[originalStudent.id]
            if (eval != null && eval.photo != null) {
                originalStudent.copy(
                    matchedPhoto = eval.photo.copy(assignedStudentCode = originalStudent.code),
                    classification = eval.classification,
                    confidenceScore = eval.confidenceScore,
                    matchReason = eval.reason,
                    // لا يتم الاعتماد التلقائي إذا كانت هناك شبهة تعارض أو تحتاج مراجعة
                    isApproved = eval.classification == MatchClassification.CONFIRMED_MATCH && eval.confidenceScore >= 95
                )
            } else {
                originalStudent.copy(
                    matchedPhoto = null,
                    classification = MatchClassification.RECORD_WITHOUT_PHOTO,
                    confidenceScore = 0,
                    matchReason = "لم يتم العثور على صورة تطابق بيانات هذا الطالب",
                    isApproved = false
                )
            }
        }
    }

    /**
     * تقييم وتصنيف مطابقة صورة مع قائمة الطلاب
     */
    fun evaluatePhotoMatch(
        photo: PhotoItem,
        students: List<Student>,
        studentByCode: Map<String, Student>,
        studentByNationalId: Map<String, Student>
    ): MatchEvaluation? {
        // --- المرحلة الأولى: فحص كود الطالب في اسم ملف الصورة ---
        val codeInFilename = extractCandidateCodeFromFileName(photo.originalName)
        val studentByFilename = if (codeInFilename != null) studentByCode[codeInFilename] else null

        // --- المرحلة الثالثة مسبقاً للبحث المباشر: فحص الرقم القومي المستخرج ---
        val detectedNid = photo.detectedNationalId
        val studentByNid = if (detectedNid != null) studentByNationalId[detectedNid] else null

        // --- المرحلة الأولى (تكملة): كود الطالب المستخرج بواسطة OCR ---
        val detectedCode = photo.detectedCode
        val studentByOcrCode = if (detectedCode != null) studentByCode[detectedCode] else null

        // --- فحص التعارضات في البيانات (DATA_CONFLICT) ---
        // مثال: الكود يشير لطالب (أ)، بينما الرقم القومي يشير لطالب (ب)
        if (studentByFilename != null && studentByNid != null && studentByFilename.id != studentByNid.id) {
            return MatchEvaluation(
                student = studentByFilename,
                photo = photo,
                classification = MatchClassification.DATA_CONFLICT,
                confidenceScore = 30,
                reason = "تعارض بيانات خطير: اسم الملف يحتوي على كود الطالب (${studentByFilename.name}) بينما الرقم القومي المكتشف يخص الطالب (${studentByNid.name})"
            )
        }

        if (studentByOcrCode != null && studentByNid != null && studentByOcrCode.id != studentByNid.id) {
            return MatchEvaluation(
                student = studentByOcrCode,
                photo = photo,
                classification = MatchClassification.DATA_CONFLICT,
                confidenceScore = 30,
                reason = "تعارض بيانات: الكود المكتشف يخص (${studentByOcrCode.name}) بينما الرقم القومي يخص (${studentByNid.name})"
            )
        }

        // تحديد الطالب المرشح الرئيسي
        var candidateStudent: Student? = studentByNid ?: studentByOcrCode ?: studentByFilename

        // --- المرحلة الثانية: فحص ومقارنة الاسم العربي ---
        var nameMatchScore = 0
        var bestNameCandidate: Student? = null

        val detectedName = photo.detectedName
        if (detectedName != null && detectedName.isNotBlank()) {
            var maxSimilarity = 0
            for (st in students) {
                val similarity = calculateArabicNameSimilarity(st.name, detectedName)
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity
                    bestNameCandidate = st
                }
            }
            nameMatchScore = maxSimilarity
        }

        // التحقق من تعارض الاسم مع الكود أو الرقم القومي
        if (candidateStudent != null && bestNameCandidate != null && candidateStudent.id != bestNameCandidate.id && nameMatchScore >= 80) {
            return MatchEvaluation(
                student = candidateStudent,
                photo = photo,
                classification = MatchClassification.DATA_CONFLICT,
                confidenceScore = 40,
                reason = "تعارض: الرقم القومي/الكود يخص (${candidateStudent.name}) بينما الاسم المكتشف بالصورة يطابق (${bestNameCandidate.name})"
            )
        }

        if (candidateStudent == null && bestNameCandidate != null && nameMatchScore >= 70) {
            candidateStudent = bestNameCandidate
        }

        if (candidateStudent == null) {
            return null
        }

        // --- المرحلة الرابعة: تقييم درجة الثقة وتحديد التصنيف النهائي ---
        var confidence = 0
        val reasons = ArrayList<String>()

        val hasNationalIdMatch = detectedNid != null && detectedNid == candidateStudent.nationalId
        val hasCodeMatch = (codeInFilename != null && codeInFilename == candidateStudent.code) ||
                (detectedCode != null && detectedCode == candidateStudent.code)
        val hasNameMatch = nameMatchScore >= 75

        if (hasNationalIdMatch) {
            confidence += 60
            reasons.add("تطابق تام في الرقم القومي")
        }
        if (hasCodeMatch) {
            confidence += 30
            reasons.add("تطابق في كود الطالب")
        }
        if (hasNameMatch) {
            confidence += (nameMatchScore * 0.4).toInt()
            reasons.add("تطابق الاسم بنسبة $nameMatchScore%")
        }

        confidence = confidence.coerceIn(0, 100)

        val classification = when {
            // مطابقة مؤكدة: تطابق الرقم القومي مع الاسم أو الكود مع الاسم أو الرقم القومي وحده مع ثقة عالية
            (hasNationalIdMatch && hasNameMatch) || (hasCodeMatch && hasNameMatch) || (hasNationalIdMatch && hasCodeMatch) -> {
                MatchClassification.CONFIRMED_MATCH
            }
            confidence >= 75 -> {
                MatchClassification.CONFIRMED_MATCH
            }
            confidence in 45..74 -> {
                MatchClassification.POTENTIAL_MATCH_REVIEW_NEEDED
            }
            else -> {
                MatchClassification.POTENTIAL_MATCH_REVIEW_NEEDED
            }
        }

        return MatchEvaluation(
            student = candidateStudent,
            photo = photo,
            classification = classification,
            confidenceScore = confidence,
            reason = reasons.joinToString(" + ")
        )
    }

    /**
     * استخراج كود الطالب المحتمل من اسم الملف (مثال: 12345.jpg أو DSC_12345.jpg)
     */
    fun extractCandidateCodeFromFileName(fileName: String): String? {
        val baseName = fileName.substringBeforeLast('.')
        val digitsRegex = Regex("\\b\\d{4,10}\\b")
        val match = digitsRegex.find(baseName)
        return match?.value
    }

    /**
     * حساب نسبة التشابه بين اسمين عربيين (0 إلى 100)
     * مع تطبيع الهمزات والتشكيل والتاء المربوطة والمقارنة بالكلمات (Token Overlap)
     */
    fun calculateArabicNameSimilarity(nameA: String, nameB: String): Int {
        val cleanA = normalizeArabicText(nameA)
        val cleanB = normalizeArabicText(nameB)

        if (cleanA.isBlank() || cleanB.isBlank()) return 0
        if (cleanA == cleanB) return 100

        val wordsA = cleanA.split("\\s+".toRegex()).filter { it.length >= 2 }
        val wordsB = cleanB.split("\\s+".toRegex()).filter { it.length >= 2 }

        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0

        var matchedWordsCount = 0
        for (wA in wordsA) {
            if (wordsB.any { wB -> isWordSimilar(wA, wB) }) {
                matchedWordsCount++
            }
        }

        val maxWords = maxOf(wordsA.size, wordsB.size)
        val tokenSimilarity = (matchedWordsCount.toFloat() / maxWords.toFloat() * 100).toInt()

        // حساب تشابه ليفنشتاين الكلي للنص
        val levSim = calculateLevenshteinSimilarity(cleanA, cleanB)

        return maxOf(tokenSimilarity, levSim)
    }

    private fun isWordSimilar(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        if (w1.length >= 4 && w2.length >= 4) {
            val dist = levenshteinDistance(w1, w2)
            if (dist <= 1) return true
        }
        return false
    }

    fun normalizeArabicText(text: String): String {
        var str = text.trim()
        // حذف التشكيل بالكامل
        str = str.replace(Regex("[\u064B-\u065F\u0670]"), "")
        // حذف التطويل
        str = str.replace("ـ", "")
        // توحيد الهمزات والألف
        str = str.replace(Regex("[أإآ]"), "ا")
        str = str.replace("ؤ", "و")
        str = str.replace("ئ", "ي")
        // توحيد الياء
        str = str.replace("ى", "ي")
        // توحيد التاء المربوطة
        str = str.replace("ة", "ه")
        // دمج المسافات
        str = str.replace(Regex("\\s+"), " ")
        return str.trim()
    }

    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Int {
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100
        val sim = ((1.0 - dist.toDouble() / maxLen.toDouble()) * 100).toInt()
        return sim.coerceIn(0, 100)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
