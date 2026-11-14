package com.autolog.carcare.data

import android.content.Context
import com.autolog.carcare.data.local.AutoLogDatabase
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CarRepository(
    private val db: AutoLogDatabase,
    private val appContext: Context? = null
) {
    private val vehicleDao = db.vehicleDao()
    private val expenseDao = db.expenseDao()
    private val reminderDao = db.reminderDao()

    // Vehicles
    fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun getAllVehiclesSnapshot(): List<Vehicle> = vehicleDao.getAllVehiclesSnapshot()

    suspend fun deleteAllVehicles() = vehicleDao.deleteAllVehicles()

    suspend fun getVehicleById(id: Long): Vehicle? = vehicleDao.getVehicleById(id)

    suspend fun getDefaultVehicle(): Vehicle? = vehicleDao.getDefaultVehicle()

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        return vehicleDao.insertVehicle(vehicle)
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.updateVehicle(vehicle)
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.deleteVehicle(vehicle)
    }

    suspend fun setDefaultVehicle(id: Long) {
        vehicleDao.clearDefaultFlags()
        vehicleDao.setDefaultVehicle(id)
    }

    suspend fun updateVehicleMileage(vehicleId: Long, mileage: Int) {
        vehicleDao.updateMileage(vehicleId, mileage)
    }

    // Expenses
    fun getExpensesForVehicle(vehicleId: Long): Flow<List<CarExpense>> =
        expenseDao.getExpensesForVehicle(vehicleId)

    suspend fun getAllExpensesSnapshot(): List<CarExpense> =
        expenseDao.getAllExpensesSnapshot()

    fun getExpensesByCategory(vehicleId: Long, category: ExpenseCategory): Flow<List<CarExpense>> =
        expenseDao.getExpensesByCategory(vehicleId, category)

    suspend fun insertExpense(expense: CarExpense, createReminderIfRequested: Boolean = false): Long {
        val id = expenseDao.insertExpense(expense)
        // Automatically bump vehicle current mileage if this expense had a higher odometer reading
        if (expense.odometer != null && expense.odometer > 0) {
            val vehicle = vehicleDao.getVehicleById(expense.vehicleId)
            if (vehicle != null && expense.odometer > vehicle.currentMileage) {
                vehicleDao.updateMileage(expense.vehicleId, expense.odometer)
            }
        }
        return id
    }

    suspend fun updateExpense(expense: CarExpense) {
        expenseDao.updateExpense(expense)
        if (expense.odometer != null && expense.odometer > 0) {
            val vehicle = vehicleDao.getVehicleById(expense.vehicleId)
            if (vehicle != null && expense.odometer > vehicle.currentMileage) {
                vehicleDao.updateMileage(expense.vehicleId, expense.odometer)
            }
        }
    }

    suspend fun deleteExpense(expense: CarExpense) {
        if (appContext != null && expense.receiptFileName.isNotBlank()) {
            ReceiptStorageManager.deleteReceiptFile(appContext, expense.receiptFileName)
        }
        expenseDao.deleteExpense(expense)
    }

    // Reminders
    fun getRemindersForVehicle(vehicleId: Long): Flow<List<Reminder>> =
        reminderDao.getRemindersForVehicle(vehicleId)

    suspend fun getAllRemindersSnapshot(): List<Reminder> =
        reminderDao.getAllRemindersSnapshot()

    fun getActiveReminders(vehicleId: Long): Flow<List<Reminder>> =
        reminderDao.getActiveReminders(vehicleId)

    suspend fun insertReminder(reminder: Reminder): Long =
        reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: Reminder) =
        reminderDao.updateReminder(reminder)

    suspend fun deleteReminder(reminder: Reminder) =
        reminderDao.deleteReminder(reminder)

    suspend fun setReminderCompleted(id: Long, completed: Boolean) =
        reminderDao.setCompleted(id, completed)

    // Preload realistic Romanian sample data (Metric: km, L; Currency: RON; with attached sample invoices)
    suspend fun preloadSampleData() {
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.US)

        // Remove any previous demo vehicles so re-seeding stays clean
        val existingList = vehicleDao.getAllVehicles().firstOrNull() ?: emptyList()
        existingList.filter { it.licensePlate == "7XYZ890" || it.licensePlate == "B 123 ABC" }.forEach {
            vehicleDao.deleteVehicle(it)
        }

        vehicleDao.clearDefaultFlags()
        val vehicle = Vehicle(
            name = "My Daily Civic",
            make = "Honda",
            model = "Civic EX",
            year = 2023,
            licensePlate = "B 123 ABC",
            vin = "1HGCR2F84NA029481",
            fuelType = "Gasoline",
            currentMileage = 38500,
            isDefault = true
        )
        val vehicleId = vehicleDao.insertVehicle(vehicle)

        val oilInvoiceFile = appContext?.let { ctx ->
            ReceiptStorageManager.generateSampleInvoiceImage(
                context = ctx,
                internalFileName = "sample_invoice_revizie_ulei.jpg",
                invoiceNumber = "RO-SRV-2026-0418",
                vendorName = "Service Auto Autorizat SRL",
                serviceTitle = "Revizie Ulei Sintetic 0W-20 & Filtre OEM",
                amountRon = 850.00,
                odometerKm = 32000,
                licensePlate = "B 123 ABC",
                dateString = dateFmt.format(Date(now - (75 * dayMillis)))
            )
        } ?: ""

        val rcaInvoiceFile = appContext?.let { ctx ->
            ReceiptStorageManager.generateSampleInvoiceImage(
                context = ctx,
                internalFileName = "sample_invoice_rca_casco.jpg",
                invoiceNumber = "RO-GRP-2026-8892",
                vendorName = "Groupama Asigurări SA",
                serviceTitle = "Poliță RCA + CASCO (6 Luni) + Decontare Directă",
                amountRon = 2650.00,
                odometerKm = 35400,
                licensePlate = "B 123 ABC",
                dateString = dateFmt.format(Date(now - (45 * dayMillis)))
            )
        } ?: ""

        val itpInvoiceFile = appContext?.let { ctx ->
            ReceiptStorageManager.generateSampleInvoiceImage(
                context = ctx,
                internalFileName = "sample_invoice_statie_itp.jpg",
                invoiceNumber = "RO-ITP-2026-1104",
                vendorName = "Stație ITP Autorizată RAR",
                serviceTitle = "Inspecția Tehnică Periodică (ITP Categorie B)",
                amountRon = 180.00,
                odometerKm = 37000,
                licensePlate = "B 123 ABC",
                dateString = dateFmt.format(Date(now - (25 * dayMillis)))
            )
        } ?: ""

        // Seed realistic Expenses in RON and kilometers (km)
        val expenses = listOf(
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.SERVICE,
                title = "Revizie Ulei Sintetic & Filtre (Oil & Filters)",
                amount = 850.00,
                dateMillis = now - (75 * dayMillis),
                odometer = 32000,
                vendor = "Service Auto Autorizat",
                notes = "0W-20 Mobil 1 + filtru ulei, aer și polen OEM",
                receiptFileName = oilInvoiceFile,
                receiptOriginalName = if (oilInvoiceFile.isNotBlank()) "Factura_Revizie_Ulei_850RON.jpg" else ""
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.SERVICE,
                title = "Schimb Anvelope & Echilibrat Roți",
                amount = 220.00,
                dateMillis = now - (75 * dayMillis),
                odometer = 32000,
                vendor = "AutoSoft Service Roți",
                notes = "Echilibrare 4 roți + presiune 2.3 bar"
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.PAYMENT,
                title = "Rată Lunară Auto / Leasing",
                amount = 1850.00,
                dateMillis = now - (60 * dayMillis),
                odometer = null,
                vendor = "BCR Leasing / Finanțare Auto",
                notes = "Rata #14 din 48",
                isRecurring = true
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.PAYMENT,
                title = "Rată Lunară Auto / Leasing",
                amount = 1850.00,
                dateMillis = now - (30 * dayMillis),
                odometer = null,
                vendor = "BCR Leasing / Finanțare Auto",
                notes = "Rata #15 din 48",
                isRecurring = true
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.PAYMENT,
                title = "Rată Lunară Auto / Leasing",
                amount = 1850.00,
                dateMillis = now - (2 * dayMillis),
                odometer = null,
                vendor = "BCR Leasing / Finanțare Auto",
                notes = "Rata #16 din 48",
                isRecurring = true
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.INSPECTION,
                title = "Inspecția Tehnică Periodică (ITP)",
                amount = 180.00,
                dateMillis = now - (25 * dayMillis),
                odometer = 37000,
                vendor = "Stație ITP Autorizată RAR",
                notes = "ITP admis (noxe + frâne + direcție). Valabil 2 ani.",
                receiptFileName = itpInvoiceFile,
                receiptOriginalName = if (itpInvoiceFile.isNotBlank()) "Certificat_Si_Factura_ITP_RAR.jpg" else ""
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.INSURANCE,
                title = "Asigurare Auto RCA + CASCO (6 Luni)",
                amount = 2650.00,
                dateMillis = now - (45 * dayMillis),
                odometer = null,
                vendor = "Groupama Asigurări",
                notes = "Poliță RCA + decontare directă și asistență rutieră",
                isRecurring = true,
                receiptFileName = rcaInvoiceFile,
                receiptOriginalName = if (rcaInvoiceFile.isNotBlank()) "Polita_Factura_RCA_CASCO_Groupama.jpg" else ""
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.REGISTRATION,
                title = "Rovinietă Anuală (12 Luni)",
                amount = 140.00,
                dateMillis = now - (40 * dayMillis),
                odometer = null,
                vendor = "CNAIR - erovinieta.ro",
                notes = "Rovinietă categoria A valabilă 12 luni"
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.FUEL,
                title = "Alimentare Benzină (45 L)",
                amount = 335.00,
                dateMillis = now - (7 * dayMillis),
                odometer = null,
                vendor = "OMV Petrom",
                notes = "45 L Benzină Standard 95 @ 7.44 RON/L"
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.WASH_DETAIL,
                title = "Spălătorie Interior / Exterior & Ceară",
                amount = 110.00,
                dateMillis = now - (14 * dayMillis),
                odometer = null,
                vendor = "Spălătorie Auto Detailing",
                notes = "Spălare activă + aspirare interior + tratament anvelope"
            ),
            CarExpense(
                vehicleId = vehicleId,
                category = ExpenseCategory.TOLL_PARKING,
                title = "Parcare Termen Lung Aeroport Otopeni (3 zile)",
                amount = 210.00,
                dateMillis = now - (18 * dayMillis),
                odometer = null,
                vendor = "Aeroport Henri Coandă",
                notes = "Taxă parcare + trecere pod Fetești"
            )
        )

        expenses.forEach { expenseDao.insertExpense(it) }

        // Seed realistic Reminders in km
        val reminders = listOf(
            Reminder(
                vehicleId = vehicleId,
                title = "Următoarea Revizie Ulei & Filtre",
                category = ExpenseCategory.SERVICE,
                dueDateMillis = now + (20 * dayMillis),
                dueMileage = 42000,
                notes = "La fiecare 10,000 km (scadent la 42,000 km)"
            ),
            Reminder(
                vehicleId = vehicleId,
                title = "Următoarea Rată Auto / Leasing",
                category = ExpenseCategory.PAYMENT,
                dueDateMillis = now + (28 * dayMillis),
                notes = "Debit automat cont curent RON"
            ),
            Reminder(
                vehicleId = vehicleId,
                title = "Reînnoire Poliță RCA / CASCO",
                category = ExpenseCategory.INSURANCE,
                dueDateMillis = now + (135 * dayMillis),
                notes = "Verificare oferte RCA cu decontare directă înainte de expirare"
            ),
            Reminder(
                vehicleId = vehicleId,
                title = "Expirare ITP & Rovinietă",
                category = ExpenseCategory.INSPECTION,
                dueDateMillis = now + (325 * dayMillis),
                notes = "Programare stație ITP și reînnoire rovinietă CNAIR"
            )
        )

        reminders.forEach { reminderDao.insertReminder(it) }
    }

    /**
     * Ensures that if the demo Civic was already seeded before v2 (receipts),
     * its key service/ITP/RCA expenses have sample invoices generated and linked.
     */
    suspend fun ensureSampleInvoicesAttached() {
        val ctx = appContext ?: return
        val allExpenses = expenseDao.getAllExpensesSnapshot()
        if (allExpenses.isEmpty()) return

        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        allExpenses.forEach { exp ->
            when {
                exp.category == ExpenseCategory.TOLL_PARKING && exp.receiptOriginalName == "Polita_Factura_RCA_CASCO_Groupama.jpg" -> {
                    expenseDao.updateExpense(exp.copy(receiptFileName = "", receiptOriginalName = ""))
                }
                exp.category == ExpenseCategory.SERVICE && exp.title.contains("Revizie Ulei", ignoreCase = true) && exp.receiptFileName.isBlank() -> {
                    val file = ReceiptStorageManager.generateSampleInvoiceImage(
                        context = ctx,
                        internalFileName = "sample_invoice_revizie_ulei.jpg",
                        invoiceNumber = "RO-SRV-2026-0418",
                        vendorName = "Service Auto Autorizat SRL",
                        serviceTitle = "Revizie Ulei Sintetic 0W-20 & Filtre OEM",
                        amountRon = exp.amount,
                        odometerKm = exp.odometer ?: 32000,
                        licensePlate = "B 123 ABC",
                        dateString = dateFmt.format(Date(exp.dateMillis))
                    )
                    expenseDao.updateExpense(
                        exp.copy(
                            receiptFileName = file,
                            receiptOriginalName = "Factura_Revizie_Ulei_850RON.jpg"
                        )
                    )
                }
                exp.category == ExpenseCategory.INSURANCE && exp.receiptFileName.isBlank() -> {
                    val file = ReceiptStorageManager.generateSampleInvoiceImage(
                        context = ctx,
                        internalFileName = "sample_invoice_rca_casco.jpg",
                        invoiceNumber = "RO-GRP-2026-8892",
                        vendorName = "Groupama Asigurări SA",
                        serviceTitle = "Poliță RCA + CASCO (6 Luni) + Decontare Directă",
                        amountRon = exp.amount,
                        odometerKm = exp.odometer ?: 35400,
                        licensePlate = "B 123 ABC",
                        dateString = dateFmt.format(Date(exp.dateMillis))
                    )
                    expenseDao.updateExpense(
                        exp.copy(
                            receiptFileName = file,
                            receiptOriginalName = "Polita_Factura_RCA_CASCO_Groupama.jpg"
                        )
                    )
                }
                exp.category == ExpenseCategory.INSPECTION && exp.receiptFileName.isBlank() -> {
                    val file = ReceiptStorageManager.generateSampleInvoiceImage(
                        context = ctx,
                        internalFileName = "sample_invoice_statie_itp.jpg",
                        invoiceNumber = "RO-ITP-2026-1104",
                        vendorName = "Stație ITP Autorizată RAR",
                        serviceTitle = "Inspecția Tehnică Periodică (ITP Categorie B)",
                        amountRon = exp.amount,
                        odometerKm = exp.odometer ?: 37000,
                        licensePlate = "B 123 ABC",
                        dateString = dateFmt.format(Date(exp.dateMillis))
                    )
                    expenseDao.updateExpense(
                        exp.copy(
                            receiptFileName = file,
                            receiptOriginalName = "Certificat_Si_Factura_ITP_RAR.jpg"
                        )
                    )
                }
            }
        }
    }
}
