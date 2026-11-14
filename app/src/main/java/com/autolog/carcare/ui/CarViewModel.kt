package com.autolog.carcare.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.autolog.carcare.data.BackupManager
import com.autolog.carcare.data.BnrExchangeRateService
import com.autolog.carcare.data.CarRepository
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.ThemePreferences
import com.autolog.carcare.data.local.Vehicle
import com.autolog.carcare.ui.components.AppCurrency
import com.autolog.carcare.ui.components.CurrencyState
import com.autolog.carcare.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateFilterOption(val label: String) {
    ALL("All Time"),
    THIS_MONTH("This Month"),
    LAST_3_MONTHS("Last 3 Mos"),
    THIS_YEAR("This Year")
}

data class CarStats(
    val totalSpent: Double = 0.0,
    val thisMonthSpent: Double = 0.0,
    val thisYearSpent: Double = 0.0,
    val categoryTotals: Map<ExpenseCategory, Double> = emptyMap(),
    val totalExpensesCount: Int = 0,
    val milesTracked: Int = 0,
    val costPerMile: Double? = null,
    val activeRemindersCount: Int = 0,
    val urgentRemindersCount: Int = 0
)

data class UiState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val allExpenses: List<CarExpense> = emptyList(),
    val filteredExpenses: List<CarExpense> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val activeReminders: List<Reminder> = emptyList(),
    val stats: CarStats = CarStats(),
    val categoryFilter: ExpenseCategory? = null,
    val searchQuery: String = "",
    val dateFilter: DateFilterOption = DateFilterOption.ALL,
    val isLoading: Boolean = false
)

class CarViewModel(
    private val repository: CarRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val currencyState: StateFlow<CurrencyState> = themePreferences.currencyState

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    private val _expenses = MutableStateFlow<List<CarExpense>>(emptyList())
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())

    private val _categoryFilter = MutableStateFlow<ExpenseCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _dateFilter = MutableStateFlow(DateFilterOption.ALL)
    private val _isLoading = MutableStateFlow(false)

    // Dialog state holders
    var showExpenseDialog = MutableStateFlow(false)
    var expenseToEdit = MutableStateFlow<CarExpense?>(null)
    var expenseReceiptToView = MutableStateFlow<CarExpense?>(null)

    var showReminderDialog = MutableStateFlow(false)
    var reminderToEdit = MutableStateFlow<Reminder?>(null)

    var showVehicleDialog = MutableStateFlow(false)
    var vehicleToEdit = MutableStateFlow<Vehicle?>(null)

    var showOdometerDialog = MutableStateFlow(false)
    var showThemeDialog = MutableStateFlow(false)
    var showCurrencyDialog = MutableStateFlow(false)
    var showBackupDialog = MutableStateFlow(false)

    var isBackupBusy = MutableStateFlow(false)
    var backupStatusMessage = MutableStateFlow<String?>(null)
    var backupIsError = MutableStateFlow(false)

    val uiState: StateFlow<UiState> = combine(
        combine(_vehicles, _selectedVehicle, _expenses, _reminders) { vList, selV, expList, remList ->
            Quad(vList, selV, expList, remList)
        },
        combine(_categoryFilter, _searchQuery, _dateFilter, _isLoading) { cat, query, dateF, loading ->
            Quad(cat, query, dateF, loading)
        }
    ) { (vList, selV, expList, remList), (cat, query, dateF, loading) ->
        val filtered = filterExpenses(expList, cat, query, dateF)
        val activeRems = remList.filter { !it.isCompleted }
        val stats = calculateStats(expList, selV, activeRems)

        UiState(
            vehicles = vList,
            selectedVehicle = selV,
            allExpenses = expList,
            filteredExpenses = filtered,
            reminders = remList,
            activeReminders = activeRems,
            stats = stats,
            categoryFilter = cat,
            searchQuery = query,
            dateFilter = dateF,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getAllVehicles().collect { vList ->
                _vehicles.value = vList
                if (vList.isNotEmpty()) {
                    val current = _selectedVehicle.value
                    if (current == null || vList.none { it.id == current.id }) {
                        val defaultCar = vList.firstOrNull { it.isDefault } ?: vList.first()
                        selectVehicle(defaultCar)
                    } else {
                        // refresh selected vehicle object in case properties changed
                        val updated = vList.firstOrNull { it.id == current.id }
                        if (updated != null) {
                            _selectedVehicle.value = updated
                        }
                    }
                } else {
                    _selectedVehicle.value = null
                    _expenses.value = emptyList()
                    _reminders.value = emptyList()
                }
            }
        }
    }

    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        viewModelScope.launch {
            repository.getExpensesForVehicle(vehicle.id).collect { expList ->
                _expenses.value = expList
            }
        }
        viewModelScope.launch {
            repository.getRemindersForVehicle(vehicle.id).collect { remList ->
                _reminders.value = remList
            }
        }
    }

    fun setCategoryFilter(category: ExpenseCategory?) {
        _categoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateFilter(filter: DateFilterOption) {
        _dateFilter.value = filter
    }

    fun getThemePreferences(): ThemePreferences = themePreferences

    fun saveExpense(
        id: Long = 0L,
        category: ExpenseCategory,
        title: String,
        amount: Double,
        dateMillis: Long,
        odometer: Int?,
        vendor: String,
        notes: String,
        isRecurring: Boolean,
        receiptFileName: String = "",
        receiptOriginalName: String = ""
    ) {
        val currentCar = _selectedVehicle.value ?: return
        viewModelScope.launch {
            val cleanTitle = title.trim()
            val cleanVendor = vendor.trim()
            val expense = CarExpense(
                id = id,
                vehicleId = currentCar.id,
                category = category,
                title = cleanTitle,
                amount = amount,
                dateMillis = dateMillis,
                odometer = odometer,
                vendor = cleanVendor,
                notes = notes.trim(),
                isRecurring = isRecurring,
                receiptFileName = receiptFileName.trim(),
                receiptOriginalName = receiptOriginalName.trim()
            )
            if (id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
            }
            themePreferences.recordSavedExpensePreset(category, cleanTitle, cleanVendor)
            showExpenseDialog.value = false
            expenseToEdit.value = null
        }
    }

    fun deleteExpense(expense: CarExpense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun saveReminder(
        id: Long = 0L,
        title: String,
        category: ExpenseCategory,
        dueDateMillis: Long?,
        dueMileage: Int?,
        notes: String
    ) {
        val currentCar = _selectedVehicle.value ?: return
        viewModelScope.launch {
            val reminder = Reminder(
                id = id,
                vehicleId = currentCar.id,
                title = title.trim(),
                category = category,
                dueDateMillis = dueDateMillis,
                dueMileage = dueMileage,
                notes = notes.trim()
            )
            if (id == 0L) {
                repository.insertReminder(reminder)
            } else {
                repository.updateReminder(reminder)
            }
            showReminderDialog.value = false
            reminderToEdit.value = null
        }
    }

    fun setReminderCompleted(reminder: Reminder, completed: Boolean) {
        viewModelScope.launch {
            repository.setReminderCompleted(reminder.id, completed)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun saveVehicle(
        id: Long = 0L,
        name: String,
        make: String,
        model: String,
        year: Int,
        licensePlate: String,
        vin: String,
        fuelType: String,
        currentMileage: Int,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            val vehicle = Vehicle(
                id = id,
                name = name.ifBlank { "$year $make $model" }.trim(),
                make = make.trim(),
                model = model.trim(),
                year = year,
                licensePlate = licensePlate.trim(),
                vin = vin.trim(),
                fuelType = fuelType,
                currentMileage = currentMileage,
                isDefault = isDefault
            )
            if (id == 0L) {
                val newId = repository.insertVehicle(vehicle)
                if (isDefault || _vehicles.value.isEmpty()) {
                    repository.setDefaultVehicle(newId)
                }
            } else {
                repository.updateVehicle(vehicle)
                if (isDefault) {
                    repository.setDefaultVehicle(id)
                }
            }
            showVehicleDialog.value = false
            vehicleToEdit.value = null
        }
    }

    fun updateCurrentMileage(mileage: Int) {
        val currentCar = _selectedVehicle.value ?: return
        viewModelScope.launch {
            repository.updateVehicleMileage(currentCar.id, mileage)
            showOdometerDialog.value = false
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.preloadSampleData()
            _isLoading.value = false
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun toggleThemeMode() {
        val all = ThemeMode.values()
        val currentIdx = all.indexOf(themeMode.value).coerceAtLeast(0)
        val next = all[(currentIdx + 1) % all.size]
        themePreferences.setThemeMode(next)
    }

    fun setCurrency(currency: AppCurrency) {
        themePreferences.setCurrency(currency)
    }

    fun toggleCurrency() {
        themePreferences.toggleCurrency()
    }

    fun refreshBnrRate() {
        viewModelScope.launch {
            themePreferences.setRefreshingRate(refreshing = true, error = null)
            val result = BnrExchangeRateService.fetchLatestEurRate()
            result.fold(
                onSuccess = { bnrResult ->
                    themePreferences.updateBnrRate(
                        rate = bnrResult.eurToRonRate,
                        date = bnrResult.date,
                        source = bnrResult.source
                    )
                },
                onFailure = { err ->
                    themePreferences.setRefreshingRate(
                        refreshing = false,
                        error = err.message ?: "Failed to fetch BNR rate"
                    )
                }
            )
        }
    }

    fun clearBackupStatus() {
        backupStatusMessage.value = null
        backupIsError.value = false
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            isBackupBusy.value = true
            backupStatusMessage.value = null
            backupIsError.value = false
            val result = BackupManager.exportToUri(context, uri, repository, themePreferences)
            isBackupBusy.value = false
            result.fold(
                onSuccess = { summary ->
                    backupIsError.value = false
                    backupStatusMessage.value =
                        "✓ Exported ${summary.vehiclesCount} vehicle(s), ${summary.expensesCount} expense(s), ${summary.receiptsCount} receipt(s), and ${summary.remindersCount} reminder(s)."
                },
                onFailure = { err ->
                    backupIsError.value = true
                    backupStatusMessage.value = "Export failed: ${err.message ?: "Unknown error"}"
                }
            )
        }
    }

    fun exportBackupToDownloads(context: Context) {
        viewModelScope.launch {
            isBackupBusy.value = true
            backupStatusMessage.value = null
            backupIsError.value = false
            val result = BackupManager.exportToDownloads(context, repository, themePreferences)
            isBackupBusy.value = false
            result.fold(
                onSuccess = { summary ->
                    backupIsError.value = false
                    backupStatusMessage.value =
                        "✓ Saved to ${summary.fileLabel}\n(${summary.vehiclesCount} vehicle(s), ${summary.expensesCount} expense(s), ${summary.receiptsCount} receipt(s), ${summary.remindersCount} reminder(s)). Ready for Google Drive upload!"
                },
                onFailure = { err ->
                    backupIsError.value = true
                    backupStatusMessage.value = "Save failed: ${err.message ?: "Unknown error"}"
                }
            )
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri, replaceExisting: Boolean) {
        viewModelScope.launch {
            isBackupBusy.value = true
            backupStatusMessage.value = null
            backupIsError.value = false
            val result = BackupManager.importFromUri(context, uri, replaceExisting, repository, themePreferences)
            isBackupBusy.value = false
            result.fold(
                onSuccess = { summary ->
                    backupIsError.value = false
                    backupStatusMessage.value =
                        "✓ ${summary.fileLabel}: ${summary.vehiclesCount} vehicle(s), ${summary.expensesCount} expense(s), ${summary.receiptsCount} receipt(s), and ${summary.remindersCount} reminder(s)."
                    // Refresh selected vehicle after restore
                    val allVehs = repository.getAllVehiclesSnapshot()
                    if (allVehs.isNotEmpty()) {
                        val def = allVehs.firstOrNull { it.isDefault } ?: allVehs.first()
                        selectVehicle(def)
                    }
                },
                onFailure = { err ->
                    backupIsError.value = true
                    backupStatusMessage.value = "Import failed: ${err.message ?: "Invalid backup file"}"
                }
            )
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        internal fun filterExpenses(
            list: List<CarExpense>,
            cat: ExpenseCategory?,
            query: String,
            dateFilter: DateFilterOption,
            nowMillis: Long = System.currentTimeMillis()
        ): List<CarExpense> {
            val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val currentMonth = now.get(Calendar.MONTH)
            val currentYear = now.get(Calendar.YEAR)

            val cal = Calendar.getInstance()

            return list.filter { exp ->
                // Category match
                val catMatch = (cat == null || exp.category == cat)

                // Search query match
                val queryMatch = query.isBlank() ||
                    exp.title.contains(query, ignoreCase = true) ||
                    exp.vendor.contains(query, ignoreCase = true) ||
                    exp.notes.contains(query, ignoreCase = true)

                // Date match
                cal.timeInMillis = exp.dateMillis
                val dateMatch = when (dateFilter) {
                    DateFilterOption.ALL -> true
                    DateFilterOption.THIS_MONTH ->
                        cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
                    DateFilterOption.LAST_3_MONTHS -> {
                        val threeMonthsAgo = Calendar.getInstance().apply {
                            timeInMillis = nowMillis
                            add(Calendar.MONTH, -3)
                        }
                        exp.dateMillis >= threeMonthsAgo.timeInMillis
                    }
                    DateFilterOption.THIS_YEAR ->
                        cal.get(Calendar.YEAR) == currentYear
                }

                catMatch && queryMatch && dateMatch
            }
        }

        internal fun calculateStats(
            expenses: List<CarExpense>,
            vehicle: Vehicle?,
            activeReminders: List<Reminder>,
            nowMillis: Long = System.currentTimeMillis()
        ): CarStats {
            val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val currentMonth = now.get(Calendar.MONTH)
            val currentYear = now.get(Calendar.YEAR)
            val cal = Calendar.getInstance()

            var total = 0.0
            var thisMonth = 0.0
            var thisYear = 0.0
            val catMap = mutableMapOf<ExpenseCategory, Double>()

            var minOdometer = Int.MAX_VALUE
            var maxOdometer = Int.MIN_VALUE

            expenses.forEach { exp ->
                total += exp.amount
                catMap[exp.category] = (catMap[exp.category] ?: 0.0) + exp.amount

                cal.timeInMillis = exp.dateMillis
                if (cal.get(Calendar.YEAR) == currentYear) {
                    thisYear += exp.amount
                    if (cal.get(Calendar.MONTH) == currentMonth) {
                        thisMonth += exp.amount
                    }
                }

                if (exp.odometer != null && exp.odometer > 0) {
                    if (exp.odometer < minOdometer) minOdometer = exp.odometer
                    if (exp.odometer > maxOdometer) maxOdometer = exp.odometer
                }
            }

            val milesTracked = if (minOdometer != Int.MAX_VALUE && maxOdometer != Int.MIN_VALUE && maxOdometer > minOdometer) {
                maxOdometer - minOdometer
            } else if (vehicle != null && vehicle.currentMileage > 0) {
                vehicle.currentMileage
            } else {
                0
            }

            val costPerMile = if (milesTracked > 0 && total > 0.0) {
                total / milesTracked
            } else null

            val urgentReminders = activeReminders.count { r ->
                val dueSoonByDate = r.dueDateMillis != null && (r.dueDateMillis - nowMillis) <= (14 * 24 * 60 * 60 * 1000L)
                val dueSoonByMileage = if (r.dueMileage != null && vehicle != null && vehicle.currentMileage > 0) {
                    (r.dueMileage - vehicle.currentMileage) <= 1000
                } else false
                dueSoonByDate || dueSoonByMileage
            }

            return CarStats(
                totalSpent = total,
                thisMonthSpent = thisMonth,
                thisYearSpent = thisYear,
                categoryTotals = catMap,
                totalExpensesCount = expenses.size,
                milesTracked = milesTracked,
                costPerMile = costPerMile,
                activeRemindersCount = activeReminders.size,
                urgentRemindersCount = urgentReminders
            )
        }
    }
}

class CarViewModelFactory(
    private val repository: CarRepository,
    private val themePreferences: ThemePreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CarViewModel(repository, themePreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
