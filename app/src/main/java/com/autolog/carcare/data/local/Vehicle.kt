package com.autolog.carcare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val make: String,
    val model: String,
    val year: Int,
    val licensePlate: String = "",
    val vin: String = "",
    val fuelType: String = "Gasoline",
    val currentMileage: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val fullDisplayName: String
        get() = if (name.isNotBlank() && name != "$year $make $model") {
            "$name ($year $make $model)"
        } else {
            "$year $make $model"
        }
}
