package com.autolog.carcare.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyAndFormattersTest {

    @Test
    fun convertFromRon_andConvertToRon_workForBothCurrencies() {
        val ronState = CurrencyState(currency = AppCurrency.RON, eurToRonRate = 5.0)
        assertEquals(1000.0, ronState.convertFromRon(1000.0), 0.0001)
        assertEquals(500.0, ronState.convertToRon(500.0, AppCurrency.RON), 0.0001)
        assertEquals(500.0, ronState.convertToRon(100.0, AppCurrency.EUR), 0.0001)

        val eurState = CurrencyState(currency = AppCurrency.EUR, eurToRonRate = 5.0)
        assertEquals(200.0, eurState.convertFromRon(1000.0), 0.0001)
    }

    @Test
    fun convertFromRon_nonPositiveRate_fallsBackSafelyToInputAmount() {
        val invalidRateState = CurrencyState(currency = AppCurrency.EUR, eurToRonRate = 0.0)
        assertEquals(250.0, invalidRateState.convertFromRon(250.0), 0.0001)
    }

    @Test
    fun currencyFormat_andFormatRate_formatExpectedStrings() {
        val ronState = CurrencyState(currency = AppCurrency.RON, eurToRonRate = 5.3368)
        assertEquals("1,850.00 RON", ronState.format(1850.0))
        assertEquals("5.3368", ronState.formatRate())

        val eurState = CurrencyState(currency = AppCurrency.EUR, eurToRonRate = 5.3368)
        assertEquals("€100.00", eurState.format(533.68))
    }

    @Test
    fun odometerFormatting_formatsWithThousandsSeparatorAndKmUnit() {
        assertEquals("38,500 km", 38500.toOdometerString())
        assertEquals("0 km", 0.toOdometerString())
    }
}
