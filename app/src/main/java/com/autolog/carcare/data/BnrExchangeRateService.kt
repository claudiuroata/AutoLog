package com.autolog.carcare.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class BnrRateResult(
    val eurToRonRate: Double,
    val date: String,
    val source: String = "BNR Official (bnr.ro)"
)

object BnrExchangeRateService {

    private const val MAX_RESPONSE_CHARS = 32_768
    private const val MIN_VALID_EUR_RON_RATE = 1.0
    private const val MAX_VALID_EUR_RON_RATE = 25.0
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * Manually fetches the official EUR -> RON exchange rate published by Banca Națională a României (BNR).
     * Never called automatically in the background; invoked strictly when the user taps the Sync BNR button.
     *
     * Security controls:
     * - Enforces HttpsURLConnection (TLS only) and disables redirects (instanceFollowRedirects = false).
     * - Caps response stream size to 32 KB to prevent memory-exhaustion / DoS attacks.
     * - Validates rate bounds (1.0 .. 25.0) and strict YYYY-MM-DD date format before accepting.
     * - Transmits zero user/vehicle data.
     */
    suspend fun fetchLatestEurRate(): Result<BnrRateResult> = withContext(Dispatchers.IO) {
        // 1. Try primary official BNR daily series endpoint
        try {
            val bnrResult = fetchFromBnrDailySeries()
            if (bnrResult != null) {
                return@withContext Result.success(bnrResult)
            }
        } catch (_: Exception) {
            // Fall through to backup
        }

        // 2. Fallback to ECB / Frankfurter reference rate for RON
        try {
            val fallbackResult = fetchFromFrankfurter()
            if (fallbackResult != null) {
                return@withContext Result.success(fallbackResult)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        Result.failure(Exception("Unable to reach BNR exchange rate service"))
    }

    private fun fetchFromBnrDailySeries(): BnrRateResult? {
        val url = URL("https://www.bnr.ro/seriizilnice")
        val conn = (url.openConnection() as? HttpsURLConnection) ?: return null
        conn.apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 8000
            readTimeout = 8000
            useCaches = false
            doOutput = true
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AutoLog/1.0")
        }

        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write("limit=5&valute=EUR")
                writer.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = readBoundedStream(conn) ?: return null
                parseBnrDailySeriesJson(responseText)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFromFrankfurter(): BnrRateResult? {
        val url = URL("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=RON")
        val conn = (url.openConnection() as? HttpsURLConnection) ?: return null
        conn.apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 8000
            readTimeout = 8000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AutoLog/1.0")
        }

        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = readBoundedStream(conn) ?: return null
                parseFrankfurterJson(responseText)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseBnrDailySeriesJson(responseText: String): BnrRateResult? {
        return try {
            val root = JSONObject(responseText)
            val dataObj = root.optJSONObject("data") ?: return null

            val dates = mutableListOf<String>()
            val keys = dataObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (ISO_DATE_REGEX.matches(key)) {
                    dates.add(key)
                }
            }
            if (dates.isEmpty()) return null

            // Sort dates chronologically and pick the most recent official BNR date
            dates.sort()
            val latestDate = dates.last()
            val dateEntry = dataObj.optJSONObject(latestDate)
            val eurEntry = dateEntry?.optJSONObject("EUR")
            val rateStr = eurEntry?.optString("valuta")
            val rate = rateStr?.replace(",", ".")?.toDoubleOrNull()

            if (isValidRate(rate)) {
                BnrRateResult(
                    eurToRonRate = rate!!,
                    date = latestDate,
                    source = "BNR Official (bnr.ro)"
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseFrankfurterJson(responseText: String): BnrRateResult? {
        return try {
            val root = JSONObject(responseText)
            val date = root.optString("date", "")
            val rates = root.optJSONObject("rates")
            val ronRate = rates?.optDouble("RON", -1.0)
            if (ISO_DATE_REGEX.matches(date) && isValidRate(ronRate)) {
                BnrRateResult(
                    eurToRonRate = ronRate!!,
                    date = date,
                    source = "BNR / ECB Official Reference"
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun isValidRate(rate: Double?): Boolean {
        return rate != null &&
            !rate.isNaN() &&
            !rate.isInfinite() &&
            rate in MIN_VALID_EUR_RON_RATE..MAX_VALID_EUR_RON_RATE
    }

    private fun readBoundedStream(conn: HttpsURLConnection): String? {
        InputStreamReader(conn.inputStream, Charsets.UTF_8).use { reader ->
            val sb = StringBuilder()
            val buffer = CharArray(2048)
            var totalRead = 0
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > MAX_RESPONSE_CHARS) {
                    return null // Reject abnormally large responses
                }
                sb.append(buffer, 0, read)
            }
            return sb.toString()
        }
    }
}
