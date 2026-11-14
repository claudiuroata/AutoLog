package com.autolog.carcare.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "car_expenses",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index("dateMillis"),
        Index("category")
    ]
)
data class CarExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: Long,
    val category: ExpenseCategory,
    val title: String,
    val amount: Double,
    val dateMillis: Long,
    val odometer: Int? = null,
    val vendor: String = "",
    val notes: String = "",
    val isRecurring: Boolean = false,
    val receiptFileName: String = "",
    val receiptOriginalName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
