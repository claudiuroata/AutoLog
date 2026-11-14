package com.autolog.carcare.ui

import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CarAnalyticsAndFilterTest {

    private fun utcMillis(year: Int, monthZeroBased: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun filterExpenses_filtersByCategoryQueryAndDateWindow() {
        val nowMillis = utcMillis(2026, Calendar.NOVEMBER, 14)
        val e1 = CarExpense(
            id = 1,
            vehicleId = 1,
            category = ExpenseCategory.SERVICE,
            title = "Oil Change",
            amount = 850.0,
            dateMillis = utcMillis(2026, Calendar.NOVEMBER, 2),
            vendor = "Service Auto",
            notes = "0W-20 synthetic"
        )
        val e2 = CarExpense(
            id = 2,
            vehicleId = 1,
            category = ExpenseCategory.FUEL,
            title = "Benzina 95",
            amount = 335.0,
            dateMillis = utcMillis(2026, Calendar.SEPTEMBER, 15),
            vendor = "OMV Petrom"
        )
        val e3 = CarExpense(
            id = 3,
            vehicleId = 1,
            category = ExpenseCategory.INSURANCE,
            title = "Polita RCA",
            amount = 1400.0,
            dateMillis = utcMillis(2025, Calendar.DECEMBER, 20),
            vendor = "Groupama"
        )
        val all = listOf(e1, e2, e3)

        assertEquals(listOf(e2), CarViewModel.filterExpenses(all, ExpenseCategory.FUEL, "", DateFilterOption.ALL, nowMillis))
        assertEquals(listOf(e1), CarViewModel.filterExpenses(all, null, "synthetic", DateFilterOption.ALL, nowMillis))
        assertEquals(listOf(e1), CarViewModel.filterExpenses(all, null, "", DateFilterOption.THIS_MONTH, nowMillis))
        assertEquals(listOf(e1, e2), CarViewModel.filterExpenses(all, null, "", DateFilterOption.LAST_3_MONTHS, nowMillis))
        assertEquals(listOf(e1, e2), CarViewModel.filterExpenses(all, null, "", DateFilterOption.THIS_YEAR, nowMillis))
    }

    @Test
    fun calculateStats_computesSpendOdometerDeltaCostPerKmAndUrgentReminders() {
        val nowMillis = utcMillis(2026, Calendar.NOVEMBER, 14)
        val dayMillis = 24 * 60 * 60 * 1000L
        val vehicle = Vehicle(
            id = 1,
            name = "Civic",
            make = "Honda",
            model = "Civic",
            year = 2023,
            currentMileage = 38500
        )
        val expenses = listOf(
            CarExpense(
                id = 1,
                vehicleId = 1,
                category = ExpenseCategory.SERVICE,
                title = "Revizie",
                amount = 1000.0,
                dateMillis = utcMillis(2026, Calendar.NOVEMBER, 5),
                odometer = 32000
            ),
            CarExpense(
                id = 2,
                vehicleId = 1,
                category = ExpenseCategory.FUEL,
                title = "Benzina",
                amount = 500.0,
                dateMillis = utcMillis(2026, Calendar.MAY, 10),
                odometer = 37000
            ),
            CarExpense(
                id = 3,
                vehicleId = 1,
                category = ExpenseCategory.INSURANCE,
                title = "Prior Year RCA",
                amount = 1000.0,
                dateMillis = utcMillis(2025, Calendar.OCTOBER, 1)
            )
        )
        val activeReminders = listOf(
            Reminder(id = 1, vehicleId = 1, title = "Due by date", category = ExpenseCategory.INSPECTION, dueDateMillis = nowMillis + 7 * dayMillis),
            Reminder(id = 2, vehicleId = 1, title = "Due by mileage", category = ExpenseCategory.SERVICE, dueMileage = 39000),
            Reminder(id = 3, vehicleId = 1, title = "Far away", category = ExpenseCategory.REGISTRATION, dueDateMillis = nowMillis + 60 * dayMillis, dueMileage = 45000)
        )

        val stats = CarViewModel.calculateStats(expenses, vehicle, activeReminders, nowMillis)

        assertEquals(2500.0, stats.totalSpent, 0.001)
        assertEquals(1000.0, stats.thisMonthSpent, 0.001)
        assertEquals(1500.0, stats.thisYearSpent, 0.001)
        assertEquals(5000, stats.milesTracked) // 37000 - 32000
        assertNotNull(stats.costPerMile)
        assertEquals(0.5, stats.costPerMile!!, 0.001) // 2500 / 5000 km
        assertEquals(3, stats.activeRemindersCount)
        assertEquals(2, stats.urgentRemindersCount)
    }

    @Test
    fun calculateStats_fallsBackToVehicleMileageWhenExpenseOdometersMissing() {
        val nowMillis = utcMillis(2026, Calendar.NOVEMBER, 14)
        val vehicle = Vehicle(id = 1, name = "Car", make = "VW", model = "Golf", year = 2020, currentMileage = 10000)
        val stats = CarViewModel.calculateStats(
            expenses = listOf(
                CarExpense(id = 1, vehicleId = 1, category = ExpenseCategory.FUEL, title = "Fuel", amount = 200.0, dateMillis = nowMillis)
            ),
            vehicle = vehicle,
            activeReminders = emptyList(),
            nowMillis = nowMillis
        )
        assertEquals(10000, stats.milesTracked)
        assertEquals(0.02, stats.costPerMile!!, 0.0001)

        val emptyStats = CarViewModel.calculateStats(emptyList(), null, emptyList(), nowMillis)
        assertEquals(0, emptyStats.milesTracked)
        assertNull(emptyStats.costPerMile)
    }
}
