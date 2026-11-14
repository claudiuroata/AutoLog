package com.autolog.carcare.data.local

import android.content.Context
import android.content.SharedPreferences
import com.autolog.carcare.ui.components.AppCurrency
import com.autolog.carcare.ui.components.CurrencyState
import com.autolog.carcare.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("autolog_preferences", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadInitialTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _currencyState = MutableStateFlow(loadInitialCurrencyState())
    val currencyState: StateFlow<CurrencyState> = _currencyState.asStateFlow()

    private val _savedPresetsVersion = MutableStateFlow(0)
    val savedPresetsVersion: StateFlow<Int> = _savedPresetsVersion.asStateFlow()

    private fun loadInitialTheme(): ThemeMode {
        val savedName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(savedName)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadInitialCurrencyState(): CurrencyState {
        val savedCurrency = prefs.getString(KEY_CURRENCY, AppCurrency.RON.name) ?: AppCurrency.RON.name
        val currency = try {
            AppCurrency.valueOf(savedCurrency)
        } catch (_: Exception) {
            AppCurrency.RON
        }
        val savedRate = prefs.getFloat(KEY_BNR_RATE, DEFAULT_BNR_EUR_RATE.toFloat()).toDouble()
        val savedDate = prefs.getString(KEY_BNR_DATE, DEFAULT_BNR_DATE) ?: DEFAULT_BNR_DATE
        val savedSource = prefs.getString(KEY_BNR_SOURCE, DEFAULT_BNR_SOURCE) ?: DEFAULT_BNR_SOURCE

        return CurrencyState(
            currency = currency,
            eurToRonRate = if (savedRate > 0.0) savedRate else DEFAULT_BNR_EUR_RATE,
            rateDate = savedDate,
            rateSource = savedSource
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.name).apply()
        _currencyState.value = _currencyState.value.copy(currency = currency)
    }

    fun toggleCurrency() {
        val next = if (_currencyState.value.currency == AppCurrency.RON) AppCurrency.EUR else AppCurrency.RON
        setCurrency(next)
    }

    fun updateBnrRate(rate: Double, date: String, source: String) {
        if (rate <= 0.0) return
        prefs.edit()
            .putFloat(KEY_BNR_RATE, rate.toFloat())
            .putString(KEY_BNR_DATE, date)
            .putString(KEY_BNR_SOURCE, source)
            .apply()
        _currencyState.value = _currencyState.value.copy(
            eurToRonRate = rate,
            rateDate = date,
            rateSource = source,
            isRefreshing = false,
            errorMessage = null
        )
    }

    fun setRefreshingRate(refreshing: Boolean, error: String? = null) {
        _currencyState.value = _currencyState.value.copy(
            isRefreshing = refreshing,
            errorMessage = error
        )
    }

    fun isMetricRonSeeded(): Boolean {
        return prefs.getBoolean(KEY_METRIC_RON_SEEDED, false)
    }

    fun markMetricRonSeeded() {
        prefs.edit().putBoolean(KEY_METRIC_RON_SEEDED, true).apply()
    }

    fun isAutoFillSavedEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_FILL_SAVED, true)
    }

    fun setAutoFillSavedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FILL_SAVED, enabled).apply()
        _savedPresetsVersion.value += 1
    }

    /**
     * Automatically saves an expense's title/description and vendor under its category
     * so the user never has to re-type recurring/favorite descriptions, leasing banks,
     * preferred gas stations, or fuel types.
     */
    fun recordSavedExpensePreset(category: ExpenseCategory, title: String, vendor: String) {
        val cleanTitle = title.trim()
        val cleanVendor = vendor.trim()
        val editor = prefs.edit()

        if (cleanTitle.isNotEmpty()) {
            val currentTitles = getSavedTitles(category).toMutableList()
            currentTitles.removeAll { it.equals(cleanTitle, ignoreCase = true) }
            currentTitles.add(0, cleanTitle)
            val capped = currentTitles.take(MAX_SAVED_PER_CATEGORY)
            editor.putString(titleKey(category), JSONArray(capped).toString())
        }

        if (cleanVendor.isNotEmpty()) {
            val currentVendors = getSavedVendors(category).toMutableList()
            currentVendors.removeAll { it.equals(cleanVendor, ignoreCase = true) }
            currentVendors.add(0, cleanVendor)
            val capped = currentVendors.take(MAX_SAVED_PER_CATEGORY)
            editor.putString(vendorKey(category), JSONArray(capped).toString())
        }

        editor.apply()
        _savedPresetsVersion.value += 1
    }

    fun getSavedTitles(category: ExpenseCategory): List<String> {
        val raw = prefs.getString(titleKey(category), null) ?: return emptyList()
        return parseJsonStringList(raw)
    }

    fun getSavedVendors(category: ExpenseCategory): List<String> {
        val raw = prefs.getString(vendorKey(category), null) ?: return emptyList()
        return parseJsonStringList(raw)
    }

    fun removeSavedTitle(category: ExpenseCategory, title: String) {
        val updated = getSavedTitles(category).filterNot { it.equals(title.trim(), ignoreCase = true) }
        prefs.edit().putString(titleKey(category), JSONArray(updated).toString()).apply()
        _savedPresetsVersion.value += 1
    }

    fun removeSavedVendor(category: ExpenseCategory, vendor: String) {
        val updated = getSavedVendors(category).filterNot { it.equals(vendor.trim(), ignoreCase = true) }
        prefs.edit().putString(vendorKey(category), JSONArray(updated).toString()).apply()
        _savedPresetsVersion.value += 1
    }

    fun exportSavedPresetsToJson(): JSONObject {
        val root = JSONObject()
        val titlesObj = JSONObject()
        val vendorsObj = JSONObject()
        ExpenseCategory.values().forEach { cat ->
            val titles = getSavedTitles(cat)
            if (titles.isNotEmpty()) {
                titlesObj.put(cat.name, JSONArray(titles))
            }
            val vendors = getSavedVendors(cat)
            if (vendors.isNotEmpty()) {
                vendorsObj.put(cat.name, JSONArray(vendors))
            }
        }
        root.put("titlesByCategory", titlesObj)
        root.put("vendorsByCategory", vendorsObj)
        root.put("autoFillEnabled", isAutoFillSavedEnabled())
        return root
    }

    fun importSavedPresetsFromJson(json: JSONObject?, merge: Boolean) {
        if (json == null) return
        val editor = prefs.edit()
        if (json.has("autoFillEnabled")) {
            editor.putBoolean(KEY_AUTO_FILL_SAVED, json.optBoolean("autoFillEnabled", true))
        }
        val titlesObj = json.optJSONObject("titlesByCategory")
        val vendorsObj = json.optJSONObject("vendorsByCategory")

        ExpenseCategory.values().forEach { cat ->
            if (titlesObj != null && titlesObj.has(cat.name)) {
                val imported = parseJsonArray(titlesObj.optJSONArray(cat.name))
                val combined = if (merge) {
                    (imported + getSavedTitles(cat)).distinctBy { it.lowercase() }.take(MAX_SAVED_PER_CATEGORY)
                } else {
                    imported.take(MAX_SAVED_PER_CATEGORY)
                }
                editor.putString(titleKey(cat), JSONArray(combined).toString())
            } else if (!merge) {
                editor.remove(titleKey(cat))
            }

            if (vendorsObj != null && vendorsObj.has(cat.name)) {
                val imported = parseJsonArray(vendorsObj.optJSONArray(cat.name))
                val combined = if (merge) {
                    (imported + getSavedVendors(cat)).distinctBy { it.lowercase() }.take(MAX_SAVED_PER_CATEGORY)
                } else {
                    imported.take(MAX_SAVED_PER_CATEGORY)
                }
                editor.putString(vendorKey(cat), JSONArray(combined).toString())
            } else if (!merge) {
                editor.remove(vendorKey(cat))
            }
        }
        editor.apply()
        _savedPresetsVersion.value += 1
    }

    private fun titleKey(category: ExpenseCategory) = "pref_saved_titles_${category.name}"
    private fun vendorKey(category: ExpenseCategory) = "pref_saved_vendors_${category.name}"

    private fun parseJsonStringList(raw: String): List<String> {
        return try {
            parseJsonArray(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i, "").trim()
            if (item.isNotEmpty() && result.none { it.equals(item, ignoreCase = true) }) {
                result.add(item)
            }
        }
        return result
    }

    companion object {
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_CURRENCY = "pref_currency"
        private const val KEY_BNR_RATE = "pref_bnr_eur_rate"
        private const val KEY_BNR_DATE = "pref_bnr_rate_date"
        private const val KEY_BNR_SOURCE = "pref_bnr_rate_source"
        private const val KEY_METRIC_RON_SEEDED = "pref_metric_ron_seeded_v1"
        private const val KEY_AUTO_FILL_SAVED = "pref_auto_fill_saved_v1"
        private const val MAX_SAVED_PER_CATEGORY = 15

        const val DEFAULT_BNR_EUR_RATE = 5.3368
        const val DEFAULT_BNR_DATE = "2026-10-16"
        const val DEFAULT_BNR_SOURCE = "BNR Official (bnr.ro)"
    }
}
