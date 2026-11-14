package com.autolog.carcare.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Manages local-only storage of expense receipts and invoices (Images & PDFs)
 * inside the app's private internal sandbox (/data/data/com.autolog.carcare/files/receipts).
 *
 * Security Hardening:
 * - Zero external storage permissions required.
 * - Strict filename sanitization (blocks path traversal / "../").
 * - Enforces a 15 MB per-attachment size limit to prevent storage/memory exhaustion.
 * - Renders both images and PDF invoices in-process using Android's native APIs.
 */
object ReceiptStorageManager {

    private const val RECEIPTS_DIR_NAME = "receipts"
    private const val MAX_RECEIPT_BYTES = 15L * 1024L * 1024L // 15 MB max per receipt

    fun getReceiptsDir(context: Context): File {
        val dir = File(context.filesDir, RECEIPTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Resolves a receipt file safely inside the receipts directory.
     * Prevents any path traversal outside the sandbox directory.
     */
    fun getReceiptFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.isBlank()) return null
        val dir = getReceiptsDir(context)
        val file = File(dir, sanitized)
        // Verify canonical path resides inside receipts dir
        return try {
            if (file.canonicalPath.startsWith(dir.canonicalPath)) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeFileName(rawName: String): String {
        val baseName = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
        return baseName.ifBlank { "receipt_file" }
    }

    /**
     * Copies a user-selected content:// URI (image or PDF) into private internal storage.
     * Returns Pair(internalFileName, displayOriginalName).
     */
    suspend fun importReceiptFromUri(context: Context, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                val originalName = queryDisplayName(context, uri) ?: defaultNameForMime(mimeType)

                val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US).let {
                    if (it in listOf("jpg", "jpeg", "png", "webp", "pdf")) it
                    else if (mimeType.contains("pdf", ignoreCase = true)) "pdf"
                    else if (mimeType.contains("png", ignoreCase = true)) "png"
                    else if (mimeType.contains("webp", ignoreCase = true)) "webp"
                    else "jpg"
                }

                val safeOrig = sanitizeFileName(originalName.substringBeforeLast('.'))
                val internalFileName = "receipt_${System.currentTimeMillis()}_${safeOrig.take(32)}.$ext"
                val destFile = File(getReceiptsDir(context), internalFileName)

                contentResolver.openInputStream(uri)?.use { input ->
                    copyBounded(input, destFile, MAX_RECEIPT_BYTES)
                } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected file"))

                Result.success(internalFileName to originalName)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Saves a captured camera Bitmap as a receipt JPEG in internal storage.
     */
    suspend fun saveBitmapAsReceipt(context: Context, bitmap: Bitmap, titleHint: String = "Camera_Receipt"): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val safeHint = sanitizeFileName(titleHint).take(24)
                val displayName = "${safeHint}_${System.currentTimeMillis().toString().takeLast(6)}.jpg"
                val internalFileName = "receipt_${System.currentTimeMillis()}_$safeHint.jpg"
                val destFile = File(getReceiptsDir(context), internalFileName)

                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                Result.success(internalFileName to displayName)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun deleteReceiptFile(context: Context, fileName: String) {
        val file = getReceiptFile(context, fileName) ?: return
        if (file.exists()) {
            file.delete()
        }
    }

    fun isPdf(fileName: String): Boolean {
        return fileName.lowercase(Locale.US).endsWith(".pdf")
    }

    fun getReceiptFileSizeFormatted(context: Context, fileName: String): String {
        val file = getReceiptFile(context, fileName) ?: return ""
        if (!file.exists()) return ""
        val bytes = file.length()
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Returns the number of pages in the receipt attachment (1 for images, or pageCount for PDFs).
     */
    suspend fun getReceiptPageCount(
        context: Context,
        fileName: String
    ): Int = withContext(Dispatchers.IO) {
        val file = getReceiptFile(context, fileName) ?: return@withContext 0
        if (!file.exists()) return@withContext 0

        try {
            if (isPdf(fileName)) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.pageCount.coerceAtLeast(1)
                    }
                }
            } else {
                1
            }
        } catch (_: Exception) {
            1
        }
    }

    /**
     * Loads a preview Bitmap for either an image receipt or a PDF invoice (renders page 1).
     */
    suspend fun loadReceiptPreviewBitmap(
        context: Context,
        fileName: String,
        maxDimension: Int = 1400
    ): Bitmap? = loadReceiptPageBitmap(context, fileName, pageIndex = 0, maxDimension = maxDimension)

    /**
     * Loads a rendered Bitmap for a specific 0-based page index of a PDF (or page 0 for an image).
     */
    suspend fun loadReceiptPageBitmap(
        context: Context,
        fileName: String,
        pageIndex: Int,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = getReceiptFile(context, fileName) ?: return@withContext null
        if (!file.exists()) return@withContext null

        try {
            if (isPdf(fileName)) {
                renderPdfPage(file, pageIndex, maxDimension)
            } else {
                decodeSampledBitmap(file, maxDimension)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var inSampleSize = 1
        while (options.outWidth / inSampleSize > maxDimension || options.outHeight / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    private fun renderPdfPage(file: File, pageIndex: Int, maxDimension: Int): Bitmap? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return null
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = (maxDimension.toFloat() / maxOf(page.width, page.height, 1)).coerceAtMost(2.5f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultNameForMime(mimeType: String): String {
        return when {
            mimeType.contains("pdf", ignoreCase = true) -> "Invoice_Document.pdf"
            mimeType.contains("png", ignoreCase = true) -> "Receipt_Image.png"
            else -> "Receipt_Photo.jpg"
        }
    }

    private fun copyBounded(input: InputStream, destFile: File, maxBytes: Long) {
        var totalBytes = 0L
        val buffer = ByteArray(8192)
        FileOutputStream(destFile).use { output ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                totalBytes += read
                if (totalBytes > maxBytes) {
                    output.close()
                    destFile.delete()
                    throw IllegalArgumentException("Attachment exceeds 15 MB limit")
                }
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    /**
     * Generates a realistic Romanian Fiscal Invoice / Service Receipt image in internal storage
     * so demo sample data includes inspectable invoices right out of the box.
     */
    fun generateSampleInvoiceImage(
        context: Context,
        internalFileName: String,
        invoiceNumber: String,
        vendorName: String,
        serviceTitle: String,
        amountRon: Double,
        odometerKm: Int,
        licensePlate: String,
        dateString: String
    ): String {
        val file = File(getReceiptsDir(context), sanitizeFileName(internalFileName))
        if (file.exists() && file.length() > 0) return file.name

        val width = 760
        val height = 980
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paper background
        canvas.drawColor(Color.parseColor("#FAFAFA"))

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 150f, headerPaint)

        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val whiteSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 20f
        }
        canvas.drawText("FACTURĂ FISCALĂ / DEVIZ SERVICE", 40f, 68f, whiteBold)
        canvas.drawText("$vendorName • România", 40f, 110f, whiteSub)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 20f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 22f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
        }

        // Invoice metadata
        canvas.drawText("Serie / Nr. Factură:", 40f, 210f, labelPaint)
        canvas.drawText(invoiceNumber, 40f, 242f, valuePaint)

        canvas.drawText("Data Emiterii:", 440f, 210f, labelPaint)
        canvas.drawText(dateString, 440f, 242f, valuePaint)

        canvas.drawText("Număr Înmatriculare:", 40f, 305f, labelPaint)
        canvas.drawText(licensePlate, 40f, 337f, valuePaint)

        canvas.drawText("Kilometraj la Intrare:", 440f, 305f, labelPaint)
        canvas.drawText("$odometerKm km", 440f, 337f, valuePaint)

        canvas.drawLine(40f, 375f, (width - 40).toFloat(), 375f, dividerPaint)

        // Line items header
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, 400f, (width - 40).toFloat(), 455f, tableHeaderPaint)
        canvas.drawText("Denumire Serviciu / Piese", 60f, 435f, valuePaint)
        canvas.drawText("Total (RON)", 540f, 435f, valuePaint)

        // Item row
        val netRon = amountRon / 1.19
        val vatRon = amountRon - netRon
        canvas.drawText(serviceTitle.take(36), 60f, 510f, regularPaint)
        canvas.drawText(String.format(Locale.US, "%.2f RON", netRon), 540f, 510f, regularPaint)

        canvas.drawText("TVA 19% (Cota Standard RO)", 60f, 565f, labelPaint)
        canvas.drawText(String.format(Locale.US, "%.2f RON", vatRon), 540f, 565f, labelPaint)

        canvas.drawLine(40f, 610f, (width - 40).toFloat(), 610f, dividerPaint)

        // Total box
        val totalBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, 640f, (width - 40).toFloat(), 740f, totalBoxPaint)

        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D4ED8")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TOTAL DE PLATĂ (CU TVA):", 65f, 700f, valuePaint)
        canvas.drawText(String.format(Locale.US, "%.2f RON", amountRon), 495f, 700f, totalPaint)

        // Stamp & footer
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16A34A")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("✓ ACHITAT INTEGRAL • BON / FACTURĂ ATAȘATĂ ÎN AUTOLOG", 60f, 820f, stampPaint)
        canvas.drawText("Garanție manoperă și piese conform legislației RAR în vigoare.", 60f, 870f, labelPaint)
        canvas.drawText("Stocat 100% local pe dispozitiv.", 60f, 905f, labelPaint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.name
    }
}
