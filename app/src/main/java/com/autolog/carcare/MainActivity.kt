package com.autolog.carcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.autolog.carcare.data.CarRepository
import com.autolog.carcare.data.local.AutoLogDatabase
import com.autolog.carcare.data.local.ThemePreferences
import com.autolog.carcare.ui.CarViewModel
import com.autolog.carcare.ui.CarViewModelFactory
import com.autolog.carcare.ui.components.LocalCurrencyState
import com.autolog.carcare.ui.screens.MainScreen
import com.autolog.carcare.ui.theme.AutoLogTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: CarViewModel by viewModels {
        val database = AutoLogDatabase.getDatabase(applicationContext)
        val repository = CarRepository(database, applicationContext)
        val themePreferences = ThemePreferences(applicationContext)
        CarViewModelFactory(repository, themePreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if database is empty on first app run or needs Romanian metric/RON upgrade
        lifecycleScope.launch {
            val database = AutoLogDatabase.getDatabase(applicationContext)
            val repository = CarRepository(database, applicationContext)
            val themePreferences = ThemePreferences(applicationContext)
            val existing = repository.getAllVehicles().firstOrNull()
            if (existing.isNullOrEmpty() || !themePreferences.isMetricRonSeeded()) {
                repository.preloadSampleData()
                themePreferences.markMetricRonSeeded()
            } else {
                repository.ensureSampleInvoicesAttached()
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val currencyState by viewModel.currencyState.collectAsState()
            AutoLogTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalCurrencyState provides currencyState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
