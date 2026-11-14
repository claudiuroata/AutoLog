package com.autolog.carcare.data.local

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.autolog.carcare.ui.theme.ColorFuel
import com.autolog.carcare.ui.theme.ColorInspection
import com.autolog.carcare.ui.theme.ColorInsurance
import com.autolog.carcare.ui.theme.ColorOther
import com.autolog.carcare.ui.theme.ColorParts
import com.autolog.carcare.ui.theme.ColorPayment
import com.autolog.carcare.ui.theme.ColorRegistration
import com.autolog.carcare.ui.theme.ColorService
import com.autolog.carcare.ui.theme.ColorTollParking
import com.autolog.carcare.ui.theme.ColorWash

enum class ExpenseCategory(
    val displayName: String,
    val description: String
) {
    SERVICE(
        displayName = "Service & Repairs",
        description = "Oil changes, brakes, tires, maintenance, repairs"
    ),
    PAYMENT(
        displayName = "Monthly Payment",
        description = "Loan installment, lease, financing"
    ),
    INSPECTION(
        displayName = "Inspection / ITP",
        description = "ITP periodic technical inspection, RAR, emissions check"
    ),
    INSURANCE(
        displayName = "Insurance (RCA / CASCO)",
        description = "Mandatory RCA policy, CASCO coverage, roadside assistance"
    ),
    FUEL(
        displayName = "Fuel & Charging",
        description = "Gasoline (Benzină), Diesel (Motorină), GPL, EV charging"
    ),
    REGISTRATION(
        displayName = "Rovinietă & Taxes",
        description = "Rovinietă road vignette, annual auto tax (Impozit), registration"
    ),
    TOLL_PARKING(
        displayName = "Tolls & Parking",
        description = "Bridge tolls (Fetești), city parking, garage, airport parking"
    ),
    WASH_DETAIL(
        displayName = "Wash & Detailing",
        description = "Car wash, interior detailing, cleaning"
    ),
    PARTS_UPGRADES(
        displayName = "Parts & Accessories",
        description = "Wipers, battery, mats, dashcam, upgrades"
    ),
    OTHER(
        displayName = "Other / Misc",
        description = "Miscellaneous auto expenses"
    );

    val icon: ImageVector
        get() = when (this) {
            SERVICE -> Icons.Default.Build
            PAYMENT -> Icons.Default.Payments
            INSPECTION -> Icons.AutoMirrored.Filled.FactCheck
            INSURANCE -> Icons.Default.Security
            FUEL -> Icons.Default.LocalGasStation
            REGISTRATION -> Icons.AutoMirrored.Filled.Assignment
            TOLL_PARKING -> Icons.Default.LocalParking
            WASH_DETAIL -> Icons.Default.LocalCarWash
            PARTS_UPGRADES -> Icons.Default.Handyman
            OTHER -> Icons.Default.MoreHoriz
        }

    val color: Color
        get() = when (this) {
            SERVICE -> ColorService
            PAYMENT -> ColorPayment
            INSPECTION -> ColorInspection
            INSURANCE -> ColorInsurance
            FUEL -> ColorFuel
            REGISTRATION -> ColorRegistration
            TOLL_PARKING -> ColorTollParking
            WASH_DETAIL -> ColorWash
            PARTS_UPGRADES -> ColorParts
            OTHER -> ColorOther
        }
}
