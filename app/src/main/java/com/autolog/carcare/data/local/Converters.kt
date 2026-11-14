package com.autolog.carcare.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromExpenseCategory(category: ExpenseCategory): String {
        return category.name
    }

    @TypeConverter
    fun toExpenseCategory(name: String): ExpenseCategory {
        return runCatching { ExpenseCategory.valueOf(name) }.getOrDefault(ExpenseCategory.OTHER)
    }
}
