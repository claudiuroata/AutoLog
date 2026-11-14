package com.autolog.carcare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarExpenseDao {
    @Query("SELECT * FROM car_expenses WHERE vehicleId = :vehicleId ORDER BY dateMillis DESC, id DESC")
    fun getExpensesForVehicle(vehicleId: Long): Flow<List<CarExpense>>

    @Query("SELECT * FROM car_expenses WHERE vehicleId = :vehicleId AND category = :category ORDER BY dateMillis DESC")
    fun getExpensesByCategory(vehicleId: Long, category: ExpenseCategory): Flow<List<CarExpense>>

    @Query("SELECT * FROM car_expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): CarExpense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: CarExpense): Long

    @Update
    suspend fun updateExpense(expense: CarExpense)

    @Delete
    suspend fun deleteExpense(expense: CarExpense)

    @Query("SELECT * FROM car_expenses ORDER BY dateMillis DESC, id DESC")
    suspend fun getAllExpensesSnapshot(): List<CarExpense>

    @Query("SELECT SUM(amount) FROM car_expenses WHERE vehicleId = :vehicleId")
    suspend fun getTotalSpentForVehicle(vehicleId: Long): Double?

    @Query("SELECT MAX(odometer) FROM car_expenses WHERE vehicleId = :vehicleId AND odometer IS NOT NULL")
    suspend fun getMaxOdometer(vehicleId: Long): Int?
}
