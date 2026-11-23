package com.tamer.studentphotomatcher.scanner

import java.util.Comparator

/**
 * مقارن طبيعي للنصوص والأرقام (Natural Order Comparator)
 * يضمن ترتيب الملفات بطريقة صحيحة:
 * IMG_1, IMG_2, IMG_9, IMG_10, IMG_20
 * بدلاً من الترتيب الأبجدي المعتاد الخاطئ:
 * IMG_1, IMG_10, IMG_2
 */
class NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String?, b: String?): Int {
        if (a == null && b == null) return 0
        if (a == null) return -1
        if (b == null) return 1

        var ia = 0
        var ib = 0
        val nza = a.length
        val nzb = b.length

        while (ia < nza && ib < nzb) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                // استخراج الرقم الكامل
                val numStartA = ia
                while (ia < nza && a[ia].isDigit()) ia++
                val numAStr = a.substring(numStartA, ia)

                val numStartB = ib
                while (ib < nzb && b[ib].isDigit()) ib++
                val numBStr = b.substring(numStartB, ib)

                // مقارنة طول الأرقام دون أصفار البداية
                val cleanA = numAStr.trimStart('0')
                val cleanB = numBStr.trimStart('0')

                if (cleanA.length != cleanB.length) {
                    return cleanA.length.compareTo(cleanB.length)
                }

                val cmp = cleanA.compareTo(cleanB)
                if (cmp != 0) return cmp

                // إذا تساوت الأرقام، نقارن عدد الأصفار البادئة
                if (numAStr.length != numBStr.length) {
                    return numAStr.length.compareTo(numBStr.length)
                }
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }

        return (nza - ia).compareTo(nzb - ib)
    }
}
