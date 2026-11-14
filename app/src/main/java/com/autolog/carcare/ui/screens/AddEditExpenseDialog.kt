package com.autolog.carcare.ui.screens

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.autolog.carcare.data.ReceiptStorageManager
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.ThemePreferences
import com.autolog.carcare.data.local.Vehicle
import com.autolog.carcare.ui.components.AppCurrency
import com.autolog.carcare.ui.components.LocalCurrencyState
import com.autolog.carcare.ui.components.toDateString
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private data class SuggestionChipItem(
    val text: String,
    val count: Int,
    val isSaved: Boolean,
    val isRemovablePreset: Boolean = false
)

@Composable
fun AddEditExpenseDialog(
    initialExpense: CarExpense?,
    currentVehicle: Vehicle?,
    existingExpenses: List<CarExpense> = emptyList(),
    themePreferences: ThemePreferences? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        category: ExpenseCategory,
        title: String,
        amount: Double,
        dateMillis: Long,
        odometer: Int?,
        vendor: String,
        notes: String,
        isRecurring: Boolean,
        receiptFileName: String,
        receiptOriginalName: String
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currencyState = LocalCurrencyState.current
    val presetsVersion = themePreferences?.savedPresetsVersion?.collectAsState()?.value ?: 0

    // Sort categories by most used (frequency desc -> most recent desc -> default enum order)
    val categoryUsageCounts = remember(existingExpenses) {
        existingExpenses.groupingBy { it.category }.eachCount()
    }
    val categoryLastUsedMillis = remember(existingExpenses) {
        existingExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.maxOfOrNull { it.dateMillis } ?: 0L }
    }
    val sortedCategories = remember(existingExpenses) {
        ExpenseCategory.values().sortedWith(
            compareByDescending<ExpenseCategory> { categoryUsageCounts[it] ?: 0 }
                .thenByDescending { categoryLastUsedMillis[it] ?: 0L }
                .thenBy { it.ordinal }
        )
    }

    // Helper functions to compute saved titles & vendors for any category
    fun computeSavedTitlesFor(cat: ExpenseCategory): List<SuggestionChipItem> {
        val fromPrefs = themePreferences?.getSavedTitles(cat) ?: emptyList()
        val fromHistory = existingExpenses
            .filter { it.category == cat && it.title.isNotBlank() }
            .groupBy { it.title.trim().lowercase() }
            .map { (_, list) ->
                val display = list.maxByOrNull { it.dateMillis }?.title?.trim() ?: list.first().title.trim()
                val count = list.size
                val lastUsed = list.maxOfOrNull { it.dateMillis } ?: 0L
                Triple(display, count, lastUsed)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })

        val result = mutableListOf<SuggestionChipItem>()
        fromHistory.forEach { (txt, cnt, _) ->
            result.add(
                SuggestionChipItem(
                    text = txt,
                    count = cnt,
                    isSaved = true,
                    isRemovablePreset = fromPrefs.any { it.equals(txt, ignoreCase = true) }
                )
            )
        }
        fromPrefs.forEach { prefTxt ->
            if (result.none { it.text.equals(prefTxt, ignoreCase = true) }) {
                result.add(
                    SuggestionChipItem(
                        text = prefTxt,
                        count = 1,
                        isSaved = true,
                        isRemovablePreset = true
                    )
                )
            }
        }
        return result
    }

    fun computeSavedVendorsFor(cat: ExpenseCategory): List<SuggestionChipItem> {
        val fromPrefs = themePreferences?.getSavedVendors(cat) ?: emptyList()
        val fromHistory = existingExpenses
            .filter { it.category == cat && it.vendor.isNotBlank() }
            .groupBy { it.vendor.trim().lowercase() }
            .map { (_, list) ->
                val display = list.maxByOrNull { it.dateMillis }?.vendor?.trim() ?: list.first().vendor.trim()
                val count = list.size
                val lastUsed = list.maxOfOrNull { it.dateMillis } ?: 0L
                Triple(display, count, lastUsed)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })

        val result = mutableListOf<SuggestionChipItem>()
        fromHistory.forEach { (txt, cnt, _) ->
            result.add(
                SuggestionChipItem(
                    text = txt,
                    count = cnt,
                    isSaved = true,
                    isRemovablePreset = fromPrefs.any { it.equals(txt, ignoreCase = true) }
                )
            )
        }
        fromPrefs.forEach { prefTxt ->
            if (result.none { it.text.equals(prefTxt, ignoreCase = true) }) {
                result.add(
                    SuggestionChipItem(
                        text = prefTxt,
                        count = 1,
                        isSaved = true,
                        isRemovablePreset = true
                    )
                )
            }
        }
        return result
    }

    fun findPreferredVendorForTitle(cat: ExpenseCategory, titleStr: String): String? {
        if (titleStr.isBlank()) return null
        val matching = existingExpenses.filter {
            it.category == cat &&
                it.title.trim().equals(titleStr.trim(), ignoreCase = true) &&
                it.vendor.isNotBlank()
        }
        if (matching.isEmpty()) return null
        return matching
            .groupBy { it.vendor.trim().lowercase() }
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<CarExpense>>> { it.value.size }
                    .thenBy { it.value.maxOfOrNull { exp -> exp.dateMillis } ?: 0L }
            )
            ?.value
            ?.maxByOrNull { it.dateMillis }
            ?.vendor
            ?.trim()
    }

    val initialCategory = initialExpense?.category ?: sortedCategories.firstOrNull() ?: ExpenseCategory.SERVICE
    var autoFillSaved by remember { mutableStateOf(themePreferences?.isAutoFillSavedEnabled() ?: true) }
    var category by remember { mutableStateOf(initialCategory) }

    var title by remember {
        val defaultTitle = if (initialExpense != null) {
            initialExpense.title
        } else if (themePreferences?.isAutoFillSavedEnabled() != false) {
            computeSavedTitlesFor(initialCategory).firstOrNull()?.text ?: ""
        } else {
            ""
        }
        mutableStateOf(defaultTitle)
    }

    var vendor by remember {
        val defaultVendor = if (initialExpense != null) {
            initialExpense.vendor
        } else if (themePreferences?.isAutoFillSavedEnabled() != false) {
            val initTitle = computeSavedTitlesFor(initialCategory).firstOrNull()?.text ?: ""
            findPreferredVendorForTitle(initialCategory, initTitle)
                ?: computeSavedVendorsFor(initialCategory).firstOrNull()?.text
                ?: ""
        } else {
            ""
        }
        mutableStateOf(defaultVendor)
    }

    var inputCurrency by remember { mutableStateOf(currencyState.currency) }
    var amountText by remember {
        val initAmt = initialExpense?.amount?.let { amtRon ->
            if (currencyState.currency == AppCurrency.EUR) {
                String.format(Locale.US, "%.2f", currencyState.convertFromRon(amtRon))
            } else {
                String.format(Locale.US, "%.2f", amtRon)
            }
        } ?: ""
        mutableStateOf(initAmt)
    }
    fun shouldDefaultOdometer(cat: ExpenseCategory): Boolean =
        cat == ExpenseCategory.SERVICE ||
            cat == ExpenseCategory.PARTS_UPGRADES ||
            cat == ExpenseCategory.INSPECTION

    fun defaultOdometerForCategory(cat: ExpenseCategory): String =
        if (shouldDefaultOdometer(cat) && currentVehicle != null && currentVehicle.currentMileage > 0) {
            currentVehicle.currentMileage.toString()
        } else {
            ""
        }

    var dateMillis by remember { mutableLongStateOf(initialExpense?.dateMillis ?: System.currentTimeMillis()) }
    var userEditedOdometer by remember { mutableStateOf(initialExpense != null) }
    var odometerText by remember {
        mutableStateOf(
            if (initialExpense != null) {
                initialExpense.odometer?.toString() ?: ""
            } else {
                defaultOdometerForCategory(initialCategory)
            }
        )
    }
    var notes by remember { mutableStateOf(initialExpense?.notes ?: "") }
    var isRecurring by remember {
        mutableStateOf(initialExpense?.isRecurring ?: (initialCategory == ExpenseCategory.PAYMENT))
    }

    var receiptFileName by remember { mutableStateOf(initialExpense?.receiptFileName ?: "") }
    var receiptOriginalName by remember { mutableStateOf(initialExpense?.receiptOriginalName ?: "") }
    var receiptThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var receiptPageCount by remember { mutableStateOf(1) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(receiptFileName) {
        if (receiptFileName.isNotBlank()) {
            receiptThumbnail = ReceiptStorageManager.loadReceiptPreviewBitmap(context, receiptFileName, maxDimension = 480)
            receiptPageCount = ReceiptStorageManager.getReceiptPageCount(context, receiptFileName).coerceAtLeast(1)
        } else {
            receiptThumbnail = null
            receiptPageCount = 1
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val res = ReceiptStorageManager.importReceiptFromUri(context, uri)
                res.fold(
                    onSuccess = { (internalName, origName) ->
                        receiptFileName = internalName
                        receiptOriginalName = origName
                        errorMessage = null
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Could not attach receipt file"
                    }
                )
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            coroutineScope.launch {
                val hint = title.ifBlank { category.displayName }
                val res = ReceiptStorageManager.saveBitmapAsReceipt(context, bitmap, hint)
                res.fold(
                    onSuccess = { (internalName, origName) ->
                        receiptFileName = internalName
                        receiptOriginalName = origName
                        errorMessage = null
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Could not save camera receipt"
                    }
                )
            }
        }
    }

    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            dateMillis = selected.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Built-in fallback suggestions for titles/descriptions (including fuel types)
    val defaultTitleSuggestions = when (category) {
        ExpenseCategory.SERVICE -> listOf("Revizie Ulei & Filtre", "Schimb Anvelope & Echilibrat", "Plăcuțe / Discuri Frână", "Filtru Aer & Polen", "Baterie Auto 12V", "Geometrie Roți")
        ExpenseCategory.PAYMENT -> listOf("Rată Lunară Auto / Leasing", "Rată Credit Auto", "Avans Leasing", "Rambursare Anticipată")
        ExpenseCategory.INSPECTION -> listOf("Inspecția Tehnică Periodică (ITP)", "Omologare / Autentificare RAR", "Verificare Noxe & Frâne")
        ExpenseCategory.INSURANCE -> listOf("Poliță RCA (12 Luni)", "Poliță RCA (6 Luni)", "Rată CASCO", "Asistență Rutieră A24")
        ExpenseCategory.FUEL -> listOf("Benzină Standard 95", "Benzină Extra 99 / 100", "Motorină Standard", "Motorină Extra", "GPL Auto", "Încărcare EV Fast Charge")
        ExpenseCategory.REGISTRATION -> listOf("Rovinietă Anuală (12 Luni)", "Impozit Auto Anual", "Taxă Înmatriculare / Numere")
        ExpenseCategory.TOLL_PARKING -> listOf("Taxă Pod Fetești", "Parcare Oraș", "Abonament Parcare", "Parcare Aeroport")
        ExpenseCategory.WASH_DETAIL -> listOf("Spălătorie Self-Wash / Jet", "Spălătorie Interior & Exterior", "Detailing & Polish", "Protecție Ceramică")
        ExpenseCategory.PARTS_UPGRADES -> listOf("Anvelope Iarnă / Vară", "Ștergătoare Parbriz", "Covorașe Cauciuc", "Cameră Bord (Dashcam)")
        ExpenseCategory.OTHER -> listOf("Trusă Medicală & Stingător", "Tractare Platformă", "Baterie Cheie")
    }

    // Built-in fallback suggestions for vendors (leasing banks, preferred gas stations, insurers, etc.)
    val defaultVendorSuggestions = when (category) {
        ExpenseCategory.FUEL -> listOf("OMV Petrom", "Rompetrol", "MOL Romania", "Petrom", "Lukoil", "SOCAR", "Renovatio e-charge")
        ExpenseCategory.PAYMENT -> listOf("BCR Leasing / Finanțare Auto", "BT Leasing", "Raiffeisen Leasing", "UniCredit Leasing", "Porsche Finance", "BRD Sogelease")
        ExpenseCategory.INSURANCE -> listOf("Groupama Asigurări", "Allianz-Țiriac", "Omniasig VIG", "Generali", "Grawe", "Asirom")
        ExpenseCategory.INSPECTION -> listOf("Stație ITP Autorizată RAR", "RAR Reprezentanță")
        ExpenseCategory.REGISTRATION -> listOf("CNAIR - erovinieta.ro", "Ghișeul.ro / Impozit Auto", "DRPCIV")
        ExpenseCategory.SERVICE -> listOf("Service Auto Autorizat", "AutoSoft Service Roți", "Reprezentanță Auto")
        ExpenseCategory.TOLL_PARKING -> listOf("CNAIR - Pod Fetești", "Parcare Primărie / TPark", "Aeroport Henri Coandă")
        ExpenseCategory.WASH_DETAIL -> listOf("Spălătorie Auto Detailing", "Self-Wash 24/7")
        ExpenseCategory.PARTS_UPGRADES -> listOf("eMAG Auto", "Autokarma", "Epiesa.ro", "Dedeman")
        ExpenseCategory.OTHER -> listOf("Service / Magazin Auto")
    }

    val titleChips = remember(category, existingExpenses, presetsVersion) {
        val saved = computeSavedTitlesFor(category)
        val defaults = defaultTitleSuggestions
            .filter { def -> saved.none { it.text.equals(def, ignoreCase = true) } }
            .map { SuggestionChipItem(text = it, count = 0, isSaved = false) }
        saved + defaults
    }

    val vendorChips = remember(category, existingExpenses, presetsVersion) {
        val savedForCat = computeSavedVendorsFor(category)
        // Also include saved vendors from other categories if not already listed
        val otherSavedVendors = existingExpenses
            .filter { it.category != category && it.vendor.isNotBlank() }
            .groupBy { it.vendor.trim().lowercase() }
            .map { (_, list) ->
                val display = list.maxByOrNull { it.dateMillis }?.vendor?.trim() ?: list.first().vendor.trim()
                SuggestionChipItem(text = display, count = list.size, isSaved = true)
            }
            .filter { other -> savedForCat.none { it.text.equals(other.text, ignoreCase = true) } }

        val combinedSaved = savedForCat + otherSavedVendors
        val defaults = defaultVendorSuggestions
            .filter { def -> combinedSaved.none { it.text.equals(def, ignoreCase = true) } }
            .map { SuggestionChipItem(text = it, count = 0, isSaved = false) }
        combinedSaved + defaults
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialExpense == null) "Log Expense or Work" else "Edit Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category selector (sorted by most used)
                val categoryListState = rememberLazyListState()
                LaunchedEffect(initialExpense, sortedCategories) {
                    val selectedIndex = sortedCategories.indexOf(category)
                    if (selectedIndex > 0) {
                        categoryListState.scrollToItem(selectedIndex)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (categoryUsageCounts.isNotEmpty()) {
                        Text(
                            text = "Sorted by Most Used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    state = categoryListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sortedCategories, key = { it.name }) { cat ->
                        val isSelected = cat == category
                        val usageCount = categoryUsageCounts[cat] ?: 0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) cat.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, cat.color) else null,
                            modifier = Modifier.clickable {
                                category = cat
                                if (cat == ExpenseCategory.PAYMENT) {
                                    isRecurring = true
                                }
                                if (initialExpense == null) {
                                    if (!userEditedOdometer) {
                                        odometerText = defaultOdometerForCategory(cat)
                                    }
                                    if (autoFillSaved) {
                                        val topSavedTitle = computeSavedTitlesFor(cat).firstOrNull()?.text ?: ""
                                        val topSavedVendor = findPreferredVendorForTitle(cat, topSavedTitle)
                                            ?: computeSavedVendorsFor(cat).firstOrNull()?.text
                                            ?: ""
                                        title = topSavedTitle
                                        vendor = topSavedVendor
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = cat.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (usageCount > 0) "${cat.displayName} ($usageCount)" else cat.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) cat.color else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-fill & Auto-save header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Titles & Vendors (Auto-Saved)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (autoFillSaved) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (autoFillSaved) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.clickable {
                            val next = !autoFillSaved
                            autoFillSaved = next
                            themePreferences?.setAutoFillSavedEnabled(next)
                            if (next && initialExpense == null) {
                                val topSavedTitle = computeSavedTitlesFor(category).firstOrNull()?.text ?: ""
                                val topSavedVendor = findPreferredVendorForTitle(category, topSavedTitle)
                                    ?: computeSavedVendorsFor(category).firstOrNull()?.text
                                    ?: ""
                                if (title.isBlank()) title = topSavedTitle
                                if (vendor.isBlank()) vendor = topSavedVendor
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (autoFillSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (autoFillSaved) "Auto-Fill: ON" else "Auto-Fill: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (autoFillSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title / Description
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Description / Fuel Type *") },
                    placeholder = { Text("e.g. Rată Leasing, Benzină 95, Revizie Ulei") },
                    trailingIcon = {
                        if (title.isNotEmpty()) {
                            IconButton(onClick = { title = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear title")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Saved & Suggested Title Chips
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(titleChips, key = { "title_${it.text}" }) { chip ->
                        val isCurrent = title.trim().equals(chip.text, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                chip.isSaved -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            border = when {
                                isCurrent -> androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                chip.isSaved -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                else -> null
                            },
                            modifier = Modifier.clickable {
                                title = chip.text
                                val pairedVendor = findPreferredVendorForTitle(category, chip.text)
                                if (pairedVendor != null && (vendor.isBlank() || autoFillSaved)) {
                                    vendor = pairedVendor
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (chip.isSaved) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Saved",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (chip.count > 1) "${chip.text} (${chip.count})" else chip.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (chip.isSaved || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (chip.isSaved || isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (chip.isRemovablePreset && themePreferences != null) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove saved preset",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable {
                                                themePreferences.removeSavedTitle(category, chip.text)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vendor / Bank / Preferred Gas Station / Shop
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor / Bank / Gas Station / Shop") },
                    placeholder = { Text("e.g. BCR Leasing, OMV Petrom, Groupama") },
                    trailingIcon = {
                        if (vendor.isNotEmpty()) {
                            IconButton(onClick = { vendor = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear vendor")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Saved & Preferred Vendor Chips
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(vendorChips, key = { "vendor_${it.text}" }) { chip ->
                        val isCurrent = vendor.trim().equals(chip.text, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                chip.isSaved -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            border = when {
                                isCurrent -> androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                chip.isSaved -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                else -> null
                            },
                            modifier = Modifier.clickable {
                                vendor = chip.text
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (chip.isSaved) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = "Saved Vendor",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (chip.count > 1) "${chip.text} (${chip.count})" else chip.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (chip.isSaved || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (chip.isSaved || isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (chip.isRemovablePreset && themePreferences != null) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove saved vendor",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable {
                                                themePreferences.removeSavedVendor(category, chip.text)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Currency selector for input (RON / EUR)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Entry Currency (BNR: 1 EUR = ${currencyState.formatRate()} RON)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppCurrency.entries.forEach { curr ->
                            val selected = inputCurrency == curr
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { inputCurrency = curr }
                            ) {
                                Text(
                                    text = curr.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Amount and Odometer in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (${inputCurrency.code}) *") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = odometerText,
                        onValueChange = {
                            userEditedOdometer = true
                            odometerText = it
                        },
                        label = { Text("Odometer (km)") },
                        placeholder = { Text(if (shouldDefaultOdometer(category)) "e.g. 38500" else "Optional") },
                        trailingIcon = {
                            if (odometerText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        userEditedOdometer = true
                                        odometerText = ""
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear odometer",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Live BNR conversion preview below Amount
                val parsedAmt = amountText.toDoubleOrNull()
                if (parsedAmt != null && parsedAmt > 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val previewText = if (inputCurrency == AppCurrency.EUR) {
                        val inRon = parsedAmt * currencyState.eurToRonRate
                        String.format(Locale.US, "≈ %.2f RON (at BNR rate %s)", inRon, currencyState.formatRate())
                    } else {
                        val inEur = if (currencyState.eurToRonRate > 0) parsedAmt / currencyState.eurToRonRate else 0.0
                        String.format(Locale.US, "≈ %.2f EUR (at BNR rate %s)", inEur, currencyState.formatRate())
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Picker button
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: ${dateMillis.toDateString()}")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Warranty") },
                    placeholder = { Text("Part numbers, warranty details, liters/km...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // RECEIPT / INVOICE ATTACHMENT SECTION
                Text(
                    text = "Receipt / Invoice (Photo or PDF)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (receiptFileName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (receiptThumbnail != null) {
                                Image(
                                    bitmap = receiptThumbnail!!.asImageBitmap(),
                                    contentDescription = "Receipt Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = receiptOriginalName.ifBlank { receiptFileName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeStr = ReceiptStorageManager.getReceiptFileSizeFormatted(context, receiptFileName)
                                Text(
                                    text = buildString {
                                        if (ReceiptStorageManager.isPdf(receiptFileName)) {
                                            append(if (receiptPageCount > 1) "PDF Invoice ($receiptPageCount pages)" else "PDF Invoice (1 page)")
                                        } else {
                                            append("Receipt Image")
                                        }
                                        if (sizeStr.isNotBlank()) append(" • $sizeStr")
                                        append(" • Saved Locally")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    receiptFileName = ""
                                    receiptOriginalName = ""
                                    receiptThumbnail = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Receipt",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (receiptFileName.isBlank()) "Attach File / PDF" else "Replace File")
                    }

                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Photo")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Recurring Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecurring = !isRecurring }
                ) {
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Recurring Expense",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Mark as recurring (e.g. monthly loan, RCA/CASCO)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (title.isBlank()) {
                                errorMessage = "Please enter a title or description"
                                return@Button
                            }
                            if (amount == null || amount < 0) {
                                errorMessage = "Please enter a valid amount"
                                return@Button
                            }
                            val amountInRon = if (inputCurrency == AppCurrency.EUR) {
                                amount * currencyState.eurToRonRate
                            } else {
                                amount
                            }
                            val odo = odometerText.toIntOrNull()
                            errorMessage = null
                            onSave(
                                initialExpense?.id ?: 0L,
                                category,
                                title,
                                amountInRon,
                                dateMillis,
                                odo,
                                vendor,
                                notes,
                                isRecurring,
                                receiptFileName,
                                receiptOriginalName
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
