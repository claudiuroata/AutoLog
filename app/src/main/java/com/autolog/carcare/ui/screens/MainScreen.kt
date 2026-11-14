package com.autolog.carcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import com.autolog.carcare.ui.theme.ThemeMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autolog.carcare.R
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.Vehicle
import com.autolog.carcare.ui.CarViewModel
import kotlinx.coroutines.launch

sealed class NavTab(val title: String, val icon: ImageVector) {
    object Dashboard : NavTab("Dashboard", Icons.Default.Dashboard)
    object Expenses : NavTab("Logs", Icons.AutoMirrored.Filled.ReceiptLong)
    object Reminders : NavTab("Reminders", Icons.Default.Alarm)
    object Analytics : NavTab("Analytics", Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: CarViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val currentTab = pagerState.currentPage

    val showExpenseDialog by viewModel.showExpenseDialog.collectAsState()
    val expenseToEdit by viewModel.expenseToEdit.collectAsState()
    val expenseReceiptToView by viewModel.expenseReceiptToView.collectAsState()

    val showReminderDialog by viewModel.showReminderDialog.collectAsState()
    val reminderToEdit by viewModel.reminderToEdit.collectAsState()

    val showVehicleDialog by viewModel.showVehicleDialog.collectAsState()
    val vehicleToEdit by viewModel.vehicleToEdit.collectAsState()

    val showOdometerDialog by viewModel.showOdometerDialog.collectAsState()
    val showThemeDialog by viewModel.showThemeDialog.collectAsState()
    val showCurrencyDialog by viewModel.showCurrencyDialog.collectAsState()
    val showBackupDialog by viewModel.showBackupDialog.collectAsState()
    val isBackupBusy by viewModel.isBackupBusy.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val backupIsError by viewModel.backupIsError.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val currencyState by viewModel.currencyState.collectAsState()

    var showVehiclePickerMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // Deletion confirmation
    var itemToDelete by remember { mutableStateOf<Any?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (uiState.vehicles.isNotEmpty()) {
                                showVehiclePickerMenu = true
                            } else {
                                viewModel.vehicleToEdit.value = null
                                viewModel.showVehicleDialog.value = true
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_background),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "AutoLog Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uiState.selectedVehicle?.name ?: "AutoLog",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (uiState.vehicles.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Switch Vehicle",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (uiState.selectedVehicle != null) {
                                Text(
                                    text = "${uiState.selectedVehicle!!.year} ${uiState.selectedVehicle!!.make} ${uiState.selectedVehicle!!.model}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Vehicle picker dropdown
                        DropdownMenu(
                            expanded = showVehiclePickerMenu,
                            onDismissRequest = { showVehiclePickerMenu = false }
                        ) {
                            uiState.vehicles.forEach { v ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(v.fullDisplayName, modifier = Modifier.weight(1f))
                                            if (v.id == uiState.selectedVehicle?.id) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectVehicle(v)
                                        showVehiclePickerMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Add New Vehicle", fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showVehiclePickerMenu = false
                                    viewModel.vehicleToEdit.value = null
                                    viewModel.showVehicleDialog.value = true
                                }
                            )
                        }
                    }
                },
                actions = {
                    // Currency (RON / EUR) Quick Action Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.showCurrencyDialog.value = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = "Currency: ${currencyState.currency.code}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currencyState.currency.code,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Backup / Export & Import Quick Action
                    IconButton(
                        onClick = {
                            viewModel.clearBackupStatus()
                            viewModel.showBackupDialog.value = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Backup & Restore (Export / Import)",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Theme Quick-Toggle Action
                    IconButton(onClick = { viewModel.showThemeDialog.value = true }) {
                        val icon = when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            else -> Icons.Default.DarkMode
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "App Theme: ${themeMode.title}",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export / Import Backup (.zip)") },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.clearBackupStatus()
                                    viewModel.showBackupDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Currency & BNR Rate (${currencyState.currency.code})") },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.showCurrencyDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CurrencyExchange, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("App Theme (${themeMode.title})") },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.showThemeDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(
                                        when (themeMode) {
                                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                            ThemeMode.LIGHT -> Icons.Default.LightMode
                                            else -> Icons.Default.DarkMode
                                        },
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Vehicle") },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.vehicleToEdit.value = null
                                    viewModel.showVehicleDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            )
                            if (uiState.selectedVehicle != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit Current Vehicle") },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.vehicleToEdit.value = uiState.selectedVehicle
                                        viewModel.showVehicleDialog.value = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Vehicle", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showTopMenu = false
                                        itemToDelete = uiState.selectedVehicle
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Load Demo Sample Data") },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.seedDemoData()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val tabs = listOf(
                    NavTab.Dashboard,
                    NavTab.Expenses,
                    NavTab.Reminders,
                    NavTab.Analytics
                )
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.selectedVehicle != null) {
                FloatingActionButton(
                    onClick = {
                        if (currentTab == 2) {
                            viewModel.reminderToEdit.value = null
                            viewModel.showReminderDialog.value = true
                        } else {
                            viewModel.expenseToEdit.value = null
                            viewModel.showExpenseDialog.value = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (currentTab == 2) "Add Reminder" else "Log Expense"
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { pageIndex ->
            when (pageIndex) {
                0 -> DashboardScreen(
                    uiState = uiState,
                    onNavigateToExpenses = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onNavigateToReminders = {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    },
                    onAddExpense = {
                        viewModel.expenseToEdit.value = null
                        viewModel.showExpenseDialog.value = true
                    },
                    onEditExpense = { exp ->
                        viewModel.expenseToEdit.value = exp
                        viewModel.showExpenseDialog.value = true
                    },
                    onDeleteExpense = { exp -> itemToDelete = exp },
                    onEditOdometer = { viewModel.showOdometerDialog.value = true },
                    onAddVehicle = {
                        viewModel.vehicleToEdit.value = null
                        viewModel.showVehicleDialog.value = true
                    },
                    onSeedDemoData = { viewModel.seedDemoData() },
                    onToggleReminder = { rem, done -> viewModel.setReminderCompleted(rem, done) },
                    onEditReminder = { rem ->
                        viewModel.reminderToEdit.value = rem
                        viewModel.showReminderDialog.value = true
                    },
                    onDeleteReminder = { rem -> itemToDelete = rem },
                    onToggleCurrency = { viewModel.toggleCurrency() },
                    onOpenCurrencyDialog = { viewModel.showCurrencyDialog.value = true },
                    onRefreshBnrRate = { viewModel.refreshBnrRate() },
                    onViewReceipt = { exp -> viewModel.expenseReceiptToView.value = exp }
                )
                1 -> ExpensesScreen(
                    uiState = uiState,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                    onDateFilterChange = { viewModel.setDateFilter(it) },
                    onAddExpense = {
                        viewModel.expenseToEdit.value = null
                        viewModel.showExpenseDialog.value = true
                    },
                    onEditExpense = { exp ->
                        viewModel.expenseToEdit.value = exp
                        viewModel.showExpenseDialog.value = true
                    },
                    onDeleteExpense = { exp -> itemToDelete = exp },
                    onViewReceipt = { exp -> viewModel.expenseReceiptToView.value = exp }
                )
                2 -> RemindersScreen(
                    uiState = uiState,
                    onAddReminder = {
                        viewModel.reminderToEdit.value = null
                        viewModel.showReminderDialog.value = true
                    },
                    onToggleCompleted = { rem, done -> viewModel.setReminderCompleted(rem, done) },
                    onEditReminder = { rem ->
                        viewModel.reminderToEdit.value = rem
                        viewModel.showReminderDialog.value = true
                    },
                    onDeleteReminder = { rem -> itemToDelete = rem }
                )
                3 -> AnalyticsScreen(
                    uiState = uiState
                )
            }
        }
    }

    // Add / Edit Expense Dialog
    if (showExpenseDialog) {
        AddEditExpenseDialog(
            initialExpense = expenseToEdit,
            currentVehicle = uiState.selectedVehicle,
            existingExpenses = uiState.allExpenses,
            themePreferences = viewModel.getThemePreferences(),
            onDismiss = {
                viewModel.showExpenseDialog.value = false
                viewModel.expenseToEdit.value = null
            },
            onSave = { id, cat, title, amt, date, odo, vendor, notes, recurring, receiptFile, receiptOrig ->
                viewModel.saveExpense(id, cat, title, amt, date, odo, vendor, notes, recurring, receiptFile, receiptOrig)
            }
        )
    }

    // Receipt / Invoice Full-Screen Viewer Dialog
    if (expenseReceiptToView != null) {
        ReceiptViewerDialog(
            expense = expenseReceiptToView!!,
            onDismiss = { viewModel.expenseReceiptToView.value = null }
        )
    }

    // Backup & Restore (Export / Import) Dialog
    if (showBackupDialog) {
        val receiptsCount = uiState.allExpenses.count { it.receiptFileName.isNotBlank() }
        BackupRestoreDialog(
            vehiclesCount = uiState.vehicles.size,
            expensesCount = uiState.allExpenses.size,
            receiptsCount = receiptsCount,
            remindersCount = uiState.reminders.size,
            isBusy = isBackupBusy,
            statusMessage = backupStatusMessage,
            isError = backupIsError,
            onExportToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
            onExportToDownloads = { viewModel.exportBackupToDownloads(context) },
            onImportFromUri = { uri, replace -> viewModel.importBackupFromUri(context, uri, replace) },
            onDismiss = { viewModel.showBackupDialog.value = false }
        )
    }

    // Add / Edit Reminder Dialog
    if (showReminderDialog) {
        AddEditReminderDialog(
            initialReminder = reminderToEdit,
            currentVehicle = uiState.selectedVehicle,
            existingReminders = uiState.reminders,
            existingExpenses = uiState.allExpenses,
            onDismiss = {
                viewModel.showReminderDialog.value = false
                viewModel.reminderToEdit.value = null
            },
            onSave = { id, title, cat, dueDate, dueMileage, notes ->
                viewModel.saveReminder(id, title, cat, dueDate, dueMileage, notes)
            }
        )
    }

    // Add / Edit Vehicle Dialog
    if (showVehicleDialog) {
        AddEditVehicleDialog(
            initialVehicle = vehicleToEdit,
            onDismiss = {
                viewModel.showVehicleDialog.value = false
                viewModel.vehicleToEdit.value = null
            },
            onSave = { id, name, make, model, year, plate, vin, fuel, mileage, isDef ->
                viewModel.saveVehicle(id, name, make, model, year, plate, vin, fuel, mileage, isDef)
            }
        )
    }

    // Update Odometer Dialog
    if (showOdometerDialog && uiState.selectedVehicle != null) {
        UpdateOdometerDialog(
            vehicle = uiState.selectedVehicle!!,
            onDismiss = { viewModel.showOdometerDialog.value = false },
            onUpdate = { newMileage -> viewModel.updateCurrentMileage(newMileage) }
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Confirm Delete") },
            text = {
                val desc = when (val item = itemToDelete) {
                    is CarExpense -> "Are you sure you want to delete '${item.title}'?"
                    is Reminder -> "Are you sure you want to delete reminder '${item.title}'?"
                    is Vehicle -> "Are you sure you want to delete '${item.fullDisplayName}'? All associated expenses and reminders will also be deleted."
                    else -> "Are you sure you want to delete this item?"
                }
                Text(desc)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val item = itemToDelete) {
                            is CarExpense -> viewModel.deleteExpense(item)
                            is Reminder -> viewModel.deleteReminder(item)
                            is Vehicle -> viewModel.deleteVehicle(item)
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onSelectTheme = { selectedMode ->
                viewModel.setThemeMode(selectedMode)
            },
            onDismiss = { viewModel.showThemeDialog.value = false }
        )
    }

    // Currency & BNR Rate Dialog
    if (showCurrencyDialog) {
        CurrencyExchangeDialog(
            currencyState = currencyState,
            onSelectCurrency = { selectedCurrency ->
                viewModel.setCurrency(selectedCurrency)
            },
            onRefreshBnrRate = { viewModel.refreshBnrRate() },
            onDismiss = { viewModel.showCurrencyDialog.value = false }
        )
    }
}
