package com.tamer.studentphotomatcher.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.PhotoSortCriteria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * محرك فحص واستكشاف صور الطلاب وقراءة ميتاداتا الكاميرا والـ EXIF
 */
class PhotoScannerEngine(private val context: Context) {

    private val naturalComparator = NaturalOrderComparator()
    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    /**
     * فحص شجرة مجلد الصور واستخراج جميع الملفات المصورة
     */
    suspend fun scanFolder(
        treeUri: Uri,
        sortCriteria: PhotoSortCriteria = PhotoSortCriteria.NATURAL_FILENAME,
        onProgress: ((scannedCount: Int) -> Unit)? = null
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList<PhotoItem>()

        val photoItems = ArrayList<PhotoItem>()
        val files = rootDoc.listFiles()

        var count = 0
        for (doc in files) {
            if (doc.isFile && isImageFile(doc.name, doc.type)) {
                val item = parsePhotoItem(doc)
                photoItems.add(item)
                count++
                if (count % 10 == 0) {
                    onProgress?.invoke(count)
                }
            }
        }
        onProgress?.invoke(count)

        sortPhotos(photoItems, sortCriteria)
    }

    /**
     * فحص قائمة صور محددة بالـ Uris
     */
    suspend fun scanUris(
        uris: List<Uri>,
        sortCriteria: PhotoSortCriteria = PhotoSortCriteria.NATURAL_FILENAME
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photoItems = ArrayList<PhotoItem>()
        for (uri in uris) {
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc != null && doc.isFile) {
                photoItems.add(parsePhotoItem(doc))
            }
        }
        sortPhotos(photoItems, sortCriteria)
    }

    /**
     * ترتيب الصور حسب المعيار المختار
     */
    fun sortPhotos(photos: List<PhotoItem>, criteria: PhotoSortCriteria): List<PhotoItem> {
        return when (criteria) {
            PhotoSortCriteria.CAPTURE_DATE_EXIF -> {
                photos.sortedWith { a, b ->
                    val dateA = a.captureDateMillis ?: a.lastModifiedMillis
                    val dateB = b.captureDateMillis ?: b.lastModifiedMillis
                    if (dateA != dateB) dateA.compareTo(dateB) else naturalComparator.compare(a.originalName, b.originalName)
                }
            }
            PhotoSortCriteria.MODIFICATION_DATE -> {
                photos.sortedWith { a, b ->
                    if (a.lastModifiedMillis != b.lastModifiedMillis) {
                        a.lastModifiedMillis.compareTo(b.lastModifiedMillis)
                    } else {
                        naturalComparator.compare(a.originalName, b.originalName)
                    }
                }
            }
            PhotoSortCriteria.NATURAL_FILENAME -> {
                photos.sortedWith { a, b -> naturalComparator.compare(a.originalName, b.originalName) }
            }
        }
    }

    private fun parsePhotoItem(doc: DocumentFile): PhotoItem {
        val name = doc.name ?: "IMG_${UUID.randomUUID().toString().take(8)}.jpg"
        val extension = name.substringAfterLast('.', "jpg").lowercase()
        val size = doc.length()
        val lastModified = doc.lastModified()

        var captureDate: Long? = null
        var width = 0
        var height = 0

        try {
            context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                if (dateString != null) {
                    try {
                        captureDate = exifDateFormat.parse(dateString)?.time
                    } catch (_: Exception) {}
                }

                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            }
        } catch (_: Exception) {
            // الاستمرار في حالة تعذر قراءة EXIF لبعض التنسيقات
        }

        return PhotoItem(
            id = doc.uri.toString(),
            uriString = doc.uri.toString(),
            originalName = name,
            extension = extension,
            sizeBytes = size,
            captureDateMillis = captureDate,
            lastModifiedMillis = lastModified,
            width = width,
            height = height
        )
    }

    private fun isImageFile(name: String?, mimeType: String?): Boolean {
        if (mimeType != null && mimeType.startsWith("image/")) return true
        if (name == null) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return supportedExtensions.contains(ext)
    }
}
