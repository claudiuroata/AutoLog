package com.autolog.carcare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BnrExchangeRateServiceTest {

    @Test
    fun parseBnrDailySeriesJson_picksLatestIsoDateAndParsesCommaDecimal() {
        val payload = """
            {
              "data": {
                "2026-11-10": { "EUR": { "valuta": "4,9710" } },
                "2026-11-14": { "EUR": { "valuta": "4,9765" } },
                "invalid-date": { "EUR": { "valuta": "9,9999" } },
                "2026-11-12": { "EUR": { "valuta": "4,9742" } }
              }
            }
        """.trimIndent()

        val result = BnrExchangeRateService.parseBnrDailySeriesJson(payload)
        assertNotNull(result)
        assertEquals(4.9765, result!!.eurToRonRate, 0.0001)
        assertEquals("2026-11-14", result.date)
        assertEquals("BNR Official (bnr.ro)", result.source)
    }

    @Test
    fun parseFrankfurterJson_parsesValidPayload() {
        val payload = """
            {
              "amount": 1.0,
              "base": "EUR",
              "date": "2026-11-14",
              "rates": { "RON": 4.9781 }
            }
        """.trimIndent()

        val result = BnrExchangeRateService.parseFrankfurterJson(payload)
        assertNotNull(result)
        assertEquals(4.9781, result!!.eurToRonRate, 0.0001)
        assertEquals("2026-11-14", result.date)
        assertEquals("BNR / ECB Official Reference", result.source)
    }

    @Test
    fun isValidRate_andParsersRejectOutOfBoundsOrMalformedData() {
        assertTrue(BnrExchangeRateService.isValidRate(4.97))
        assertFalse(BnrExchangeRateService.isValidRate(null))
        assertFalse(BnrExchangeRateService.isValidRate(0.5))
        assertFalse(BnrExchangeRateService.isValidRate(30.0))
        assertFalse(BnrExchangeRateService.isValidRate(Double.NaN))
        assertFalse(BnrExchangeRateService.isValidRate(Double.POSITIVE_INFINITY))

        val outOfBoundsBnr = """{ "data": { "2026-11-14": { "EUR": { "valuta": "99,5" } } } }"""
        assertNull(BnrExchangeRateService.parseBnrDailySeriesJson(outOfBoundsBnr))

        val badDateFrankfurter = """{ "date": "14-11-2026", "rates": { "RON": 4.98 } }"""
        assertNull(BnrExchangeRateService.parseFrankfurterJson(badDateFrankfurter))

        assertNull(BnrExchangeRateService.parseBnrDailySeriesJson("{not-json"))
        assertNull(BnrExchangeRateService.parseFrankfurterJson("{not-json"))
    }
}
