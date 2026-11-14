package com.autolog.carcare.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAndCategoryTest {

    @Test
    fun vehicleFullDisplayName_usesCustomNameWhenDistinct() {
        val vehicle = Vehicle(
            name = "My Daily Civic",
            make = "Honda",
            model = "Civic EX",
            year = 2023
        )
        assertEquals("My Daily Civic (2023 Honda Civic EX)", vehicle.fullDisplayName)
    }

    @Test
    fun vehicleFullDisplayName_fallsBackWhenBlankOrMatchingDefault() {
        val blankNamed = Vehicle(name = "   ", make = "Dacia", model = "Duster", year = 2024)
        val sameNamed = Vehicle(name = "2024 Dacia Duster", make = "Dacia", model = "Duster", year = 2024)

        assertEquals("2024 Dacia Duster", blankNamed.fullDisplayName)
        assertEquals("2024 Dacia Duster", sameNamed.fullDisplayName)
    }

    @Test
    fun converters_roundTripsAllCategoriesAndFallsBackForUnknown() {
        val converters = Converters()
        ExpenseCategory.entries.forEach { category ->
            val stored = converters.fromExpenseCategory(category)
            assertEquals(category, converters.toExpenseCategory(stored))
        }
        assertEquals(ExpenseCategory.OTHER, converters.toExpenseCategory("UNKNOWN_LEGACY_CATEGORY"))
    }

    @Test
    fun expenseCategories_haveNonBlankMetadata() {
        assertEquals(10, ExpenseCategory.entries.size)
        ExpenseCategory.entries.forEach { category ->
            assertTrue("${category.name} missing displayName", category.displayName.isNotBlank())
            assertTrue("${category.name} missing description", category.description.isNotBlank())
        }
    }
}
