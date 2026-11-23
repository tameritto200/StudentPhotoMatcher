package com.tamer.studentphotomatcher.ocr

import java.util.regex.Pattern

/**
 * مستخرج ومحلل النصوص العربية والبيانات الرسمية من نتائج OCR
 * مسؤول عن:
 * 1. استخراج الرقم القومي (14 رقماً بدقة تامة دون تخمين أو اختلاق)
 * 2. استخراج كود الطالب
 * 3. استخراج اسم الطالب وتجاهل الشعارات والتواريخ والنصوص الدخيلة
 * 4. تحويل الأرقام الهندية / العربية إلى أرقام قياسية
 */
object ArabicTextExtractor {

    // كلمات ومصطلحات رسمية يتم تجاهلها واستبعادها من أسماء الطلاب
    private val IGNORED_NOISE_TERMS = setOf(
        "جمهورية", "مصر", "العربية", "وزارة", "التربية", "والتعليم", "التعليم", "الفني",
        "مديرية", "إدارة", "مدرسة", "مدرسه", "ابتدائية", "إعدادية", "ثانوية", "رسمية", "لغات",
        "الصف", "الفصل", "شعبة", "المرحلة", "تاريخ", "الميلاد", "العام", "الدراسي", "كود",
        "القومي", "الرقم", "الجلوس", "رقم", "طالب", "تلميذ", "محافظة", "قطاع", "كنترول",
        "student", "name", "school", "grade", "class", "date", "birth", "national", "id", "code"
    )

    // نمط الرقم القومي المصري الرسمي: 14 رقماً يبدأ بـ 2 (مواليد 1900-1999) أو 3 (مواليد 2000-2099)
    private val NATIONAL_ID_STRICT_PATTERN = Pattern.compile("\\b([23]\\d{13})\\b")
    // نمط احتياطي لأي رقم مكون من 14 خانة
    private val NATIONAL_ID_ANY_14_PATTERN = Pattern.compile("\\b(\\d{14})\\b")

    // نمط كود الطالب: من 5 إلى 10 خانات
    private val STUDENT_CODE_PATTERN = Pattern.compile("\\b(\\d{5,10})\\b")

    /**
     * تحويل الأرقام المشرقية / الهندية (٠١٢٣٤٥٦٧٨٩) إلى أرقام ASCII (0123456789)
     */
    fun normalizeDigits(text: String): String {
        val easternArabicDigits = "٠١٢٣٤٥٦٧٨٩"
        val asciiDigits = "0123456789"
        val sb = StringBuilder()

        for (ch in text) {
            val idx = easternArabicDigits.indexOf(ch)
            if (idx != -1) {
                sb.append(asciiDigits[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * استخراج الرقم القومي من النص
     */
    fun extractNationalId(rawText: String): String? {
        val normalized = normalizeDigits(rawText)

        // 1. البحث عن رقم قومي مصري محدد بدقة (14 رقماً يبدأ بـ 2 أو 3)
        val strictMatcher = NATIONAL_ID_STRICT_PATTERN.matcher(normalized)
        if (strictMatcher.find()) {
            return strictMatcher.group(1)
        }

        // 2. فحص وجود 14 رقماً متصلة
        val generalMatcher = NATIONAL_ID_ANY_14_PATTERN.matcher(normalized)
        if (generalMatcher.find()) {
            return generalMatcher.group(1)
        }

        return null
    }

    /**
     * استخراج كود الطالب من النص
     */
    fun extractStudentCode(rawText: String, nationalIdFound: String?): String? {
        val normalized = normalizeDigits(rawText)
        val matcher = STUDENT_CODE_PATTERN.matcher(normalized)

        while (matcher.find()) {
            val candidate = matcher.group(1)
            // نتأكد أن الكود ليس جزءاً من الرقم القومي المكتشف
            if (nationalIdFound != null && nationalIdFound.contains(candidate)) {
                continue
            }
            // استبعاد السنوات مثل 2023, 2024
            if (candidate.length == 4 && (candidate.startsWith("20") || candidate.startsWith("19"))) {
                continue
            }
            return candidate
        }

        return null
    }

    /**
     * استخراج اسم الطالب من بين نصوص الصورة بعد فلترة الكلمات الدخيلة
     */
    fun extractStudentName(lines: List<String>): String? {
        val candidates = ArrayList<Pair<String, Int>>() // اسم الطالب ودرجة ثقة

        for (line in lines) {
            val clean = cleanTextLine(line)
            if (clean.isBlank()) continue

            val words = clean.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (words.size in 2..5) {
                // فحص نسبة الكلمات العربية النقية
                val isAllArabic = words.all { isArabicWord(it) }
                val hasNoiseWords = words.any { IGNORED_NOISE_TERMS.contains(it.lowercase()) }

                if (isAllArabic && !hasNoiseWords) {
                    // وزن الاسم بناء على عدد الكلمات (الأسماء الثلاثية والرباعية هي الأرجح)
                    val score = when (words.size) {
                        3 -> 100
                        4 -> 95
                        2 -> 80
                        5 -> 70
                        else -> 50
                    }
                    candidates.add(Pair(words.joinToString(" "), score))
                }
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    /**
     * تنظيف سطر النص من علامات الترقيم والأرقام الدخيلة
     */
    fun cleanTextLine(line: String): String {
        return line.replace(Regex("[0-9٠-٩:;.,_\\-—/\\\\*#&|()[\\]{}]"), " ")
            .replace(Regex("[\u064B-\u065F\u0670]"), "") // إزالة التشكيل
            .trim()
    }

    fun isArabicWord(word: String): Boolean {
        return word.all { ch ->
            (ch in '\u0600'..'\u06FF') || (ch in '\u0750'..'\u077F') || (ch in '\u08A0'..'\u08FF')
        }
    }
}
