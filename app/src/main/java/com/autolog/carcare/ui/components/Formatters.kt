package com.autolog.carcare.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val subtitle: String
) {
    RON(
        code = "RON",
        symbol = "RON",
        displayName = "RON (Leu Românesc)",
        subtitle = "Primary currency in Romania"
    ),
    EUR(
        code = "EUR",
        symbol = "€",
        displayName = "EUR (Euro)",
        subtitle = "Converted at BNR official daily exchange rate"
    )
}

data class CurrencyState(
    val currency: AppCurrency = AppCurrency.RON,
    val eurToRonRate: Double = 5.3368,
    val rateDate: String = "2026-10-16",
    val rateSource: String = "BNR Official (bnr.ro)",
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Converts a base amount (stored in RON) to the currently selected display currency.
     */
    fun convertFromRon(amountInRon: Double): Double {
        return when (currency) {
            AppCurrency.RON -> amountInRon
            AppCurrency.EUR -> if (eurToRonRate > 0.0) amountInRon / eurToRonRate else amountInRon
        }
    }

    /**
     * Converts an amount from the given input currency into canonical RON for database storage.
     */
    fun convertToRon(amount: Double, inputCurrency: AppCurrency): Double {
        return when (inputCurrency) {
            AppCurrency.RON -> amount
            AppCurrency.EUR -> amount * eurToRonRate
        }
    }

    fun format(amountInRon: Double): String {
        val converted = convertFromRon(amountInRon)
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return when (currency) {
            AppCurrency.RON -> "${nf.format(converted)} RON"
            AppCurrency.EUR -> "€${nf.format(converted)}"
        }
    }

    fun formatRate(): String {
        return String.format(Locale.US, "%.4f", eurToRonRate)
    }
}

val LocalCurrencyState = compositionLocalOf { CurrencyState() }

@Composable
fun Double.toCurrency(): String {
    return LocalCurrencyState.current.format(this)
}

fun Double.toCurrency(state: CurrencyState): String {
    return state.format(this)
}

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toShortDateString(): String {
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Int.toOdometerString(): String {
    val format = NumberFormat.getIntegerInstance(Locale.US)
    return "${format.format(this)} km"
}
