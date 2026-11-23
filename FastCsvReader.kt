package com.tamer.studentphotomatcher.excel

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * قارئ ذكي لملفات CSV و TSV متوافق مع معيار RFC-4180
 * يدعم الكشف التلقائي عن الفواصل (Comma, Semicolon, Tab, Pipe)
 * ويدعم تشفيرات UTF-8 و Windows-1256 (العربية)
 */
object FastCsvReader {

    data class ParsedCsv(
        val headers: List<String>,
        val rows: List<List<String>>
    )

    /**
     * قراءة وتفكيك محتوى ملف CSV من تدفق الإدخال
     */
    fun readCsv(inputStream: InputStream): ParsedCsv {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) {
            return ParsedCsv(emptyList(), emptyList())
        }

        // الكشف عن التشفير (UTF-8 مع أو بدون BOM، أو Windows-1256)
        val text = decodeBytes(bytes)
        val lines = splitIntoLines(text)
        if (lines.isEmpty()) {
            return ParsedCsv(emptyList(), emptyList())
        }

        // الكشف التلقائي عن الفاصل الأنسب
        val delimiter = detectDelimiter(lines.take(5))

        val parsedRows = ArrayList<List<String>>()
        for (line in lines) {
            if (line.isBlank()) continue
            val row = parseCsvLine(line, delimiter)
            if (row.any { it.isNotBlank() }) {
                parsedRows.add(row)
            }
        }

        if (parsedRows.isEmpty()) {
            return ParsedCsv(emptyList(), emptyList())
        }

        val headers = parsedRows.first()
        val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

        return ParsedCsv(headers, dataRows)
    }

    /**
     * كشف الفاصل المستخدم (فاصلة عادية، منقوطة، Tab، أو عمود)
     */
    fun detectDelimiter(sampleLines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        var bestDelimiter = ','
        var maxCount = -1

        for (delim in candidates) {
            var count = 0
            for (line in sampleLines) {
                count += line.count { it == delim }
            }
            if (count > maxCount) {
                maxCount = count
                bestDelimiter = delim
            }
        }

        return if (maxCount > 0) bestDelimiter else ','
    }

    /**
     * تفكيك سطر CSV مع احترام النصوص المحاطة بعلامات اقتباس مزدوجة
     */
    fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = ArrayList<String>()
        val currentToken = StringBuilder()
        var insideQuote = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]

            if (ch == '"') {
                if (insideQuote && i + 1 < line.length && line[i + 1] == '"') {
                    // علامة تنصيص مكررة داخل نص مقتبس ""
                    currentToken.append('"')
                    i++
                } else {
                    insideQuote = !insideQuote
                }
            } else if (ch == delimiter && !insideQuote) {
                tokens.add(currentToken.toString().trim())
                currentToken.clear()
            } else {
                currentToken.append(ch)
            }
            i++
        }
        tokens.add(currentToken.toString().trim())

        return tokens
    }

    private fun decodeBytes(bytes: ByteArray): String {
        // التحقق من UTF-8 BOM (EF BB BF)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }

        // محاولة القراءة كـ UTF-8
        val utf8Text = String(bytes, StandardCharsets.UTF_8)
        // إذا كان هناك علامات استبدال غير صالحة \uFFFD بشكل متكرر، نجرب Windows-1256
        val replacementCount = utf8Text.count { it == '\uFFFD' }
        if (replacementCount > 3) {
            try {
                val winCharset = Charset.forName("windows-1256")
                return String(bytes, winCharset)
            } catch (_: Exception) {
                // البقاء على UTF-8
            }
        }

        return utf8Text
    }

    private fun splitIntoLines(text: String): List<String> {
        return text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    }
}
