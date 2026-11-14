package com.autolog.carcare.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import com.autolog.carcare.data.local.CarExpense
import com.autolog.carcare.data.local.ExpenseCategory
import com.autolog.carcare.data.local.Reminder
import com.autolog.carcare.data.local.Vehicle
import com.autolog.carcare.ui.components.toDateString
import java.util.Calendar

@Composable
fun AddEditReminderDialog(
    initialReminder: Reminder?,
    currentVehicle: Vehicle?,
    existingReminders: List<Reminder> = emptyList(),
    existingExpenses: List<CarExpense> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        title: String,
        category: ExpenseCategory,
        dueDateMillis: Long?,
        dueMileage: Int?,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current

    val categoryUsageCounts = remember(existingReminders, existingExpenses) {
        val counts = mutableMapOf<ExpenseCategory, Int>()
        existingExpenses.forEach { counts[it.category] = (counts[it.category] ?: 0) + 1 }
        existingReminders.forEach { counts[it.category] = (counts[it.category] ?: 0) + 1 }
        counts
    }
    val sortedCategories = remember(categoryUsageCounts) {
        ExpenseCategory.values().sortedWith(
            compareByDescending<ExpenseCategory> { categoryUsageCounts[it] ?: 0 }
                .thenBy { it.ordinal }
        )
    }

    var category by remember {
        mutableStateOf(
            initialReminder?.category ?: sortedCategories.firstOrNull() ?: ExpenseCategory.SERVICE
        )
    }
    var title by remember { mutableStateOf(initialReminder?.title ?: "") }
    var dueDateMillis by remember { mutableStateOf(initialReminder?.dueDateMillis) }
    var dueMileageText by remember { mutableStateOf(initialReminder?.dueMileage?.toString() ?: "") }
    var notes by remember { mutableStateOf(initialReminder?.notes ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val calendar = Calendar.getInstance().apply {
        if (dueDateMillis != null) timeInMillis = dueDateMillis!!
    }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            dueDateMillis = selected.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val reminderSuggestions = listOf(
        "Oil & Filter Change (10,000 km)" to ExpenseCategory.SERVICE,
        "ITP Periodic Inspection" to ExpenseCategory.INSPECTION,
        "Car Loan / Lease Payment" to ExpenseCategory.PAYMENT,
        "RCA / CASCO Insurance Renewal" to ExpenseCategory.INSURANCE,
        "Rovinietă & Road Tax Renewal" to ExpenseCategory.REGISTRATION,
        "Seasonal Tire Swap" to ExpenseCategory.SERVICE,
        "Brake Inspection" to ExpenseCategory.SERVICE
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialReminder == null) "New Reminder" else "Edit Reminder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category selector (sorted by most used)
                val categoryListState = rememberLazyListState()
                LaunchedEffect(initialReminder, sortedCategories) {
                    val selectedIndex = sortedCategories.indexOf(category)
                    if (selectedIndex > 0) {
                        categoryListState.scrollToItem(selectedIndex)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (categoryUsageCounts.isNotEmpty()) {
                        Text(
                            text = "Sorted by Most Used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    state = categoryListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sortedCategories, key = { it.name }) { cat ->
                        val isSelected = cat == category
                        val usageCount = categoryUsageCounts[cat] ?: 0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) cat.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, cat.color) else null,
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = cat.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (usageCount > 0) "${cat.displayName} ($usageCount)" else cat.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) cat.color else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title *") },
                    placeholder = { Text("e.g. Next Oil Change, Insurance Renewal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick suggestions
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(reminderSuggestions) { (suggTitle, suggCat) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                title = suggTitle
                                category = suggCat
                            }
                        ) {
                            Text(
                                text = suggTitle,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Due Date selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (dueDateMillis != null) "Due: ${dueDateMillis!!.toDateString()}" else "Pick Due Date"
                        )
                    }

                    if (dueDateMillis != null) {
                        IconButton(onClick = { dueDateMillis = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear date")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Due Odometer (km)
                OutlinedTextField(
                    value = dueMileageText,
                    onValueChange = { dueMileageText = it },
                    label = { Text("Due At Odometer (km)") },
                    placeholder = {
                        val suggest = (currentVehicle?.currentMileage ?: 0) + 10000
                        Text("e.g. $suggest km")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Instructions") },
                    placeholder = { Text("Policy number, shop contact, coupon...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Please enter a reminder title"
                                return@Button
                            }
                            val mileage = dueMileageText.toIntOrNull()
                            if (dueDateMillis == null && mileage == null) {
                                errorMessage = "Please set either a due date or a due mileage"
                                return@Button
                            }
                            errorMessage = null
                            onSave(
                                initialReminder?.id ?: 0L,
                                title,
                                category,
                                dueDateMillis,
                                mileage,
                                notes
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
