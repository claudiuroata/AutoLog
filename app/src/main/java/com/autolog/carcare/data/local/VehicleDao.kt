package com.autolog.carcare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY isDefault DESC, createdAt DESC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Long): Vehicle?

    @Query("SELECT * FROM vehicles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultVehicle(): Vehicle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    @Query("UPDATE vehicles SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE vehicles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultVehicle(id: Long)

    @Query("SELECT * FROM vehicles ORDER BY isDefault DESC, createdAt DESC")
    suspend fun getAllVehiclesSnapshot(): List<Vehicle>

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    @Query("UPDATE vehicles SET currentMileage = :mileage WHERE id = :id")
    suspend fun updateMileage(id: Long, mileage: Int)
}
