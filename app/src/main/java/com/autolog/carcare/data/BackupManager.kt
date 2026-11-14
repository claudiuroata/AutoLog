package com.autolog.carcare.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.ThemePreferences
import com.autolog.carcare.data.local.Vehicle
import com.autolog.carcare.ui.components.AppCurrency
import com.autolog.carcare.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val vehiclesCount: Int,
    val expensesCount: Int,
    val remindersCount: Int,
    val receiptsCount: Int,
    val fileLabel: String
)

/**
 * Handles full local Export & Import of all AutoLog data (Vehicles, Expenses, Reminders,
 * Preferences, and attached Receipt/Invoice files) as a self-contained .zip archive
 * ready for manual upload to Google Drive or local offline storage.
 *
 * Security Hardening:
 * - Zero cloud SDKs or background network calls; uses Android's system Storage Access Framework.
 * - Strict Zip-Slip traversal prevention when extracting receipt files.
 * - Bounded stream reads to prevent Zip-Bomb / memory exhaustion attacks.
 */
object BackupManager {

    private const val BACKUP_JSON_ENTRY = "autolog_backup.json"
    private const val RECEIPTS_ZIP_PREFIX = "receipts/"
    private const val MAX_JSON_BYTES = 10 * 1024 * 1024 // 10 MB max for JSON metadata
    private const val MAX_SINGLE_RECEIPT_BYTES = 15L * 1024L * 1024L // 15 MB per receipt
    private const val MAX_TOTAL_ARCHIVE_BYTES = 150L * 1024L * 1024L // 150 MB total archive

    fun defaultBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "AutoLog_Backup_$dateStr.zip"
    }

    /**
     * Exports all database records, preferences, and attached receipt files to a user-chosen Uri
     * (from ActivityResultContracts.CreateDocument).
     */
    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        repository: CarRepository,
        themePreferences: ThemePreferences
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to open destination file"))
            out.use { stream ->
                writeBackupZipToStream(
                    context = context,
                    outputStream = stream,
                    repository = repository,
                    themePreferences = themePreferences,
                    fileLabel = "Selected File / Google Drive"
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Quick-saves a complete backup .zip directly into the device's public Downloads folder
     * using scoped MediaStore APIs (zero storage permissions required on Android 10+).
     */
    suspend fun exportToDownloads(
        context: Context,
        repository: CarRepository,
        themePreferences: ThemePreferences
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val fileName = defaultBackupFileName()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Could not create file in Downloads"))

                val summaryResult = resolver.openOutputStream(itemUri)?.use { stream ->
                    writeBackupZipToStream(
                        context = context,
                        outputStream = stream,
                        repository = repository,
                        themePreferences = themePreferences,
                        fileLabel = "Downloads/$fileName"
                    )
                } ?: Result.failure(IllegalStateException("Could not open Downloads stream"))

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
                summaryResult
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, fileName)
                FileOutputStream(outFile).use { stream ->
                    writeBackupZipToStream(
                        context = context,
                        outputStream = stream,
                        repository = repository,
                        themePreferences = themePreferences,
                        fileLabel = "Downloads/$fileName"
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeBackupZipToStream(
        context: Context,
        outputStream: OutputStream,
        repository: CarRepository,
        themePreferences: ThemePreferences,
        fileLabel: String
    ): Result<BackupSummary> {
        val vehicles = repository.getAllVehiclesSnapshot()
        val expenses = repository.getAllExpensesSnapshot()
        val reminders = repository.getAllRemindersSnapshot()
        val currencyState = themePreferences.currencyState.value
        val themeMode = themePreferences.themeMode.value

        val rootJson = JSONObject().apply {
            put("app", "AutoLog")
            put("schemaVersion", 2)
            put("exportedAtMillis", System.currentTimeMillis())
            put("exportedAtIso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))

            val prefsJson = JSONObject().apply {
                put("themeMode", themeMode.name)
                put("currency", currencyState.currency.name)
                put("eurToRonRate", currencyState.eurToRonRate)
                put("rateDate", currencyState.rateDate)
                put("rateSource", currencyState.rateSource)
                put("savedPresets", themePreferences.exportSavedPresetsToJson())
            }
            put("preferences", prefsJson)

            val vehiclesArr = JSONArray()
            vehicles.forEach { v ->
                vehiclesArr.put(
                    JSONObject().apply {
                        put("id", v.id)
                        put("name", v.name)
                        put("make", v.make)
                        put("model", v.model)
                        put("year", v.year)
                        put("licensePlate", v.licensePlate)
                        put("vin", v.vin)
                        put("fuelType", v.fuelType)
                        put("currentMileage", v.currentMileage)
                        put("isDefault", v.isDefault)
                        put("createdAt", v.createdAt)
                    }
                )
            }
            put("vehicles", vehiclesArr)

            val expensesArr = JSONArray()
            expenses.forEach { e ->
                expensesArr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("vehicleId", e.vehicleId)
                        put("category", e.category.name)
                        put("title", e.title)
                        put("amount", e.amount)
                        put("dateMillis", e.dateMillis)
                        if (e.odometer != null) put("odometer", e.odometer)
                        put("vendor", e.vendor)
                        put("notes", e.notes)
                        put("isRecurring", e.isRecurring)
                        put("receiptFileName", e.receiptFileName)
                        put("receiptOriginalName", e.receiptOriginalName)
                        put("createdAt", e.createdAt)
                    }
                )
            }
            put("expenses", expensesArr)

            val remindersArr = JSONArray()
            reminders.forEach { r ->
                remindersArr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("vehicleId", r.vehicleId)
                        put("title", r.title)
                        put("category", r.category.name)
                        if (r.dueDateMillis != null) put("dueDateMillis", r.dueDateMillis)
                        if (r.dueMileage != null) put("dueMileage", r.dueMileage)
                        put("notes", r.notes)
                        put("isCompleted", r.isCompleted)
                        put("createdAt", r.createdAt)
                    }
                )
            }
            put("reminders", remindersArr)
        }

        var receiptsIncluded = 0
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            // 1. Write JSON database & preferences snapshot
            val jsonBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
            val jsonEntry = ZipEntry(BACKUP_JSON_ENTRY)
            zos.putNextEntry(jsonEntry)
            zos.write(jsonBytes)
            zos.closeEntry()

            // 2. Package all referenced receipt/invoice files
            val receiptNames = expenses.mapNotNull { exp ->
                exp.receiptFileName.takeIf { it.isNotBlank() }
            }.toSet()

            val buffer = ByteArray(8192)
            for (receiptName in receiptNames) {
                val safeName = ReceiptStorageManager.sanitizeFileName(receiptName)
                val file = ReceiptStorageManager.getReceiptFile(context, safeName)
                if (file != null && file.exists() && file.isFile) {
                    val entry = ZipEntry("$RECEIPTS_ZIP_PREFIX$safeName")
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            zos.write(buffer, 0, read)
                        }
                    }
                    zos.closeEntry()
                    receiptsIncluded++
                }
            }
            zos.finish()
        }

        return Result.success(
            BackupSummary(
                vehiclesCount = vehicles.size,
                expensesCount = expenses.size,
                remindersCount = reminders.size,
                receiptsCount = receiptsIncluded,
                fileLabel = fileLabel
            )
        )
    }

    /**
     * Imports a backup (.zip archive or plain .json file) from a user-selected Uri.
     * Supports either replacing existing data (replaceExisting = true) or merging with current data.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        replaceExisting: Boolean,
        repository: CarRepository,
        themePreferences: ThemePreferences
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val rawStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to open selected backup file"))

            rawStream.use { input ->
                val buffered = BufferedInputStream(input)
                buffered.mark(8)
                val magic = ByteArray(4)
                val readMagic = buffered.read(magic)
                buffered.reset()

                // Check PK\x03\x04 ZIP header
                val isZip = readMagic >= 4 &&
                    magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()

                var jsonPayload: String? = null
                var extractedReceiptsCount = 0

                if (isZip) {
                    var totalExtractedBytes = 0L
                    ZipInputStream(buffered).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name ?: ""
                            // Security check: block any path traversal
                            if (!entry.isDirectory && !name.contains("..")) {
                                if (name == BACKUP_JSON_ENTRY || name.endsWith(".json")) {
                                    val bytes = readBoundedEntry(zis, MAX_JSON_BYTES.toLong())
                                    totalExtractedBytes += bytes.size
                                    if (totalExtractedBytes > MAX_TOTAL_ARCHIVE_BYTES) {
                                        throw IllegalArgumentException("Backup archive exceeds maximum allowed size")
                                    }
                                    jsonPayload = String(bytes, Charsets.UTF_8)
                                } else if (name.startsWith(RECEIPTS_ZIP_PREFIX)) {
                                    val rawFileName = name.removePrefix(RECEIPTS_ZIP_PREFIX)
                                    val safeFileName = ReceiptStorageManager.sanitizeFileName(rawFileName)
                                    if (safeFileName.isNotBlank()) {
                                        val destFile = File(ReceiptStorageManager.getReceiptsDir(context), safeFileName)
                                        val written = writeBoundedEntryToFile(zis, destFile, MAX_SINGLE_RECEIPT_BYTES)
                                        totalExtractedBytes += written
                                        if (totalExtractedBytes > MAX_TOTAL_ARCHIVE_BYTES) {
                                            destFile.delete()
                                            throw IllegalArgumentException("Backup archive exceeds maximum allowed size")
                                        }
                                        extractedReceiptsCount++
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } else {
                    // Fallback: Plain JSON backup file
                    val bytes = readBoundedEntry(buffered, MAX_JSON_BYTES.toLong())
                    jsonPayload = String(bytes, Charsets.UTF_8)
                }

                val jsonStr = jsonPayload
                    ?: return@withContext Result.failure(IllegalArgumentException("No valid AutoLog backup data found in file"))

                restoreFromJson(
                    jsonString = jsonStr,
                    replaceExisting = replaceExisting,
                    extractedReceiptsCount = extractedReceiptsCount,
                    repository = repository,
                    themePreferences = themePreferences
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restoreFromJson(
        jsonString: String,
        replaceExisting: Boolean,
        extractedReceiptsCount: Int,
        repository: CarRepository,
        themePreferences: ThemePreferences
    ): Result<BackupSummary> {
        val root = JSONObject(jsonString)
        val appTag = root.optString("app", "")
        if (appTag != "AutoLog" && !root.has("vehicles")) {
            return Result.failure(IllegalArgumentException("Selected file is not a valid AutoLog backup"))
        }

        // 1. Restore preferences if present
        root.optJSONObject("preferences")?.let { prefs ->
            val themeStr = prefs.optString("themeMode", "")
            if (themeStr.isNotBlank()) {
                runCatching { ThemeMode.valueOf(themeStr) }.getOrNull()?.let {
                    themePreferences.setThemeMode(it)
                }
            }
            val currStr = prefs.optString("currency", "")
            if (currStr.isNotBlank()) {
                runCatching { AppCurrency.valueOf(currStr) }.getOrNull()?.let {
                    themePreferences.setCurrency(it)
                }
            }
            val rate = prefs.optDouble("eurToRonRate", -1.0)
            val rateDate = prefs.optString("rateDate", "")
            val rateSource = prefs.optString("rateSource", "")
            if (rate in 1.0..25.0 && rateDate.isNotBlank()) {
                themePreferences.updateBnrRate(rate, rateDate, rateSource.ifBlank { "Restored Backup" })
            }
            prefs.optJSONObject("savedPresets")?.let { savedPresetsJson ->
                themePreferences.importSavedPresetsFromJson(savedPresetsJson, merge = !replaceExisting)
            }
        }

        if (replaceExisting) {
            repository.deleteAllVehicles()
        }

        // 2. Restore vehicles & map oldVehicleId -> newVehicleId
        val vehicleIdMap = mutableMapOf<Long, Long>()
        val vehiclesArr = root.optJSONArray("vehicles") ?: JSONArray()
        var importedVehicles = 0
        var defaultNewVehicleId: Long? = null

        for (i in 0 until vehiclesArr.length()) {
            val obj = vehiclesArr.optJSONObject(i) ?: continue
            val oldId = obj.optLong("id", 0L)
            val isDef = obj.optBoolean("isDefault", false)
            val vehicle = Vehicle(
                id = 0L, // Let Room assign a clean ID and map children via vehicleIdMap
                name = obj.optString("name", "Vehicle").take(100),
                make = obj.optString("make", "").take(60),
                model = obj.optString("model", "").take(60),
                year = obj.optInt("year", 2023).coerceIn(1900, 2100),
                licensePlate = obj.optString("licensePlate", "").take(30),
                vin = obj.optString("vin", "").take(40),
                fuelType = obj.optString("fuelType", "Gasoline").take(40),
                currentMileage = obj.optInt("currentMileage", 0).coerceAtLeast(0),
                isDefault = isDef,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            val newId = repository.insertVehicle(vehicle)
            vehicleIdMap[oldId] = newId
            if (isDef || defaultNewVehicleId == null) {
                defaultNewVehicleId = newId
            }
            importedVehicles++
        }

        if (replaceExisting && defaultNewVehicleId != null) {
            repository.setDefaultVehicle(defaultNewVehicleId)
        }

        // 3. Restore expenses
        val expensesArr = root.optJSONArray("expenses") ?: JSONArray()
        var importedExpenses = 0
        for (i in 0 until expensesArr.length()) {
            val obj = expensesArr.optJSONObject(i) ?: continue
            val oldVehId = obj.optLong("vehicleId", 0L)
            val targetVehId = vehicleIdMap[oldVehId] ?: defaultNewVehicleId ?: continue
            val catName = obj.optString("category", ExpenseCategory.OTHER.name)
            val category = runCatching { ExpenseCategory.valueOf(catName) }.getOrDefault(ExpenseCategory.OTHER)
            val odo = if (obj.has("odometer") && !obj.isNull("odometer")) obj.optInt("odometer") else null

            val expense = CarExpense(
                id = 0L,
                vehicleId = targetVehId,
                category = category,
                title = obj.optString("title", "Expense").take(150),
                amount = obj.optDouble("amount", 0.0).coerceAtLeast(0.0),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                odometer = odo,
                vendor = obj.optString("vendor", "").take(120),
                notes = obj.optString("notes", "").take(500),
                isRecurring = obj.optBoolean("isRecurring", false),
                receiptFileName = ReceiptStorageManager.sanitizeFileName(obj.optString("receiptFileName", "")).let {
                    if (obj.optString("receiptFileName", "").isBlank()) "" else it
                },
                receiptOriginalName = obj.optString("receiptOriginalName", "").take(150),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            repository.insertExpense(expense)
            importedExpenses++
        }

        // 4. Restore reminders
        val remindersArr = root.optJSONArray("reminders") ?: JSONArray()
        var importedReminders = 0
        for (i in 0 until remindersArr.length()) {
            val obj = remindersArr.optJSONObject(i) ?: continue
            val oldVehId = obj.optLong("vehicleId", 0L)
            val targetVehId = vehicleIdMap[oldVehId] ?: defaultNewVehicleId ?: continue
            val catName = obj.optString("category", ExpenseCategory.SERVICE.name)
            val category = runCatching { ExpenseCategory.valueOf(catName) }.getOrDefault(ExpenseCategory.SERVICE)
            val dueDate = if (obj.has("dueDateMillis") && !obj.isNull("dueDateMillis")) obj.optLong("dueDateMillis") else null
            val dueMileage = if (obj.has("dueMileage") && !obj.isNull("dueMileage")) obj.optInt("dueMileage") else null

            val reminder = Reminder(
                id = 0L,
                vehicleId = targetVehId,
                title = obj.optString("title", "Reminder").take(150),
                category = category,
                dueDateMillis = dueDate,
                dueMileage = dueMileage,
                notes = obj.optString("notes", "").take(500),
                isCompleted = obj.optBoolean("isCompleted", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            repository.insertReminder(reminder)
            importedReminders++
        }

        return Result.success(
            BackupSummary(
                vehiclesCount = importedVehicles,
                expensesCount = importedExpenses,
                remindersCount = importedReminders,
                receiptsCount = extractedReceiptsCount,
                fileLabel = if (replaceExisting) "Restored (Replaced All)" else "Imported (Merged)"
            )
        )
    }

    private fun readBoundedEntry(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Backup entry exceeds maximum size limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun writeBoundedEntryToFile(input: InputStream, destFile: File, maxBytes: Long): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        FileOutputStream(destFile).use { out ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                if (total > maxBytes) {
                    out.close()
                    destFile.delete()
                    throw IllegalArgumentException("Receipt in backup exceeds 15 MB limit")
                }
                out.write(buffer, 0, read)
            }
            out.flush()
        }
        return total
    }
}
