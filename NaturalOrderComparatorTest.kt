package com.tamer.studentphotomatcher

import com.tamer.studentphotomatcher.scanner.NaturalOrderComparator
import org.junit.Assert.*
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun testNaturalNumericOrdering() {
        val files = listOf(
            "IMG_10.jpg",
            "IMG_1.jpg",
            "IMG_20.jpg",
            "IMG_2.jpg",
            "IMG_3.jpg"
        )

        val sorted = files.sortedWith(NaturalOrderComparator())
        val expected = listOf(
            "IMG_1.jpg",
            "IMG_2.jpg",
            "IMG_3.jpg",
            "IMG_10.jpg",
            "IMG_20.jpg"
        )

        assertEquals(expected, sorted)
    }
}
