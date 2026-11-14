# AutoLog — Car Expense, Maintenance & Receipt Tracker

A clean, modern Android application built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Room SQLite (v2)** to track multi-vehicle expenses, attached receipts and PDF invoices, maintenance reminders, and dual-currency (`RON` / `EUR`) spending analytics.

---

## Key Features

### 1. Multi-Vehicle Management
- Track multiple vehicles with **Name, Make, Model, Year, License Plate, VIN, Fuel Type**, and **Current Odometer (`km`)**.
- Quick odometer updates directly from the Dashboard plus automatic vehicle mileage synchronization whenever a higher odometer reading is logged in a service or expense entry.

### 2. Comprehensive Expense & Saved Presets Tracking
- Categorize spending across **10 automotive categories**:
  - **Service & Repairs**, **Monthly Payment**, **Inspection / ITP**, **Insurance (RCA / CASCO)**, **Fuel & Charging**, **Rovinietă & Taxes**, **Tolls & Parking**, **Wash & Detailing**, **Parts & Accessories**, and **Other / Misc**.
- Smart **Saved Presets** automatically remember per-category descriptions and vendors locally in `SharedPreferences` for one-tap reuse.
- Interactive search across title, vendor, and notes combined with category and date-range filters (*All Time*, *This Month*, *Last 3 Mos*, *This Year*).

### 3. Receipt & Invoice Attachments (Images & Multi-Page PDFs)
- Capture receipts directly with the camera, import images from the gallery, or attach multi-page **PDF invoices** via Android's document picker.
- Rendered natively in-process using Android's `PdfRenderer` and `BitmapFactory` inside an isolated private `receipts/` sandbox.

### 4. Dual Currency (`RON` / `EUR`) with On-Demand BNR Exchange Rate Sync
- Canonical database storage in **RON** with instant switching between **RON (Leu Românesc)** and **EUR (€)** across dashboards, cards, dialogs, and reports.
- Optional **manual on-demand** exchange rate refresh fetching the official BNR daily series (`bnr.ro`) with automatic fallback to the ECB/Frankfurter reference API (`api.frankfurter.dev`).

### 5. Date & Mileage Maintenance Reminders
- Configure upcoming alerts by **due date**, **target odometer (`km`)**, or both.
- Color-coded urgency indicators (**Overdue!**, **Due Soon** within 14 days or 1,000 km, **Upcoming**, and **Completed**), with one-tap completion archiving.

### 6. Financial Analytics & Shareable Text Reports
- Real-time metrics for **Total Spend**, **This Month**, **This Year**, and **Cost Per km** (calculated from tracked odometer deltas or current mileage).
- Category progress bars and itemized totals, plus one-tap plain-text report sharing via Android's system share sheet.

### 7. Portable `.zip` Backup & Restore
- Full self-contained `.zip` archive export containing vehicles, expenses, reminders, theme/currency preferences, saved presets (`autolog_backup.json`), and all attached receipt/PDF files (`receipts/`).
- Export via the system Storage Access Framework (including Google Drive or local folders) or one-tap save directly into `Downloads`, with **Replace All** or **Merge** import modes.

### 8. 7 Automotive Theme Modes
- Cycle or select across **System Default**, **Daylight Clean (Light)**, **Midnight Slate (Dark)**, **AMOLED Pitch Black**, **Carbon & Sport Red**, **Racing Green**, and **Cockpit Amber**.

---

## Architecture & Project Structure

```text
AutoLog/
├── .github/workflows/unit-tests.yml                   # GitHub Actions CI workflow (JVM unit tests)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/autolog/carcare/
│   │   │   │   ├── MainActivity.kt                    # Compose host, edge-to-edge UI & demo seeding
│   │   │   │   ├── data/
│   │   │   │   │   ├── BackupManager.kt               # .zip / .json export & restore with Zip-Slip/bomb guards
│   │   │   │   │   ├── BnrExchangeRateService.kt      # Manual HTTPS BNR & ECB/Frankfurter rate fetcher
│   │   │   │   │   ├── CarRepository.kt               # Unified repository & sample Romanian vehicle/invoice seed
│   │   │   │   │   ├── ReceiptStorageManager.kt       # Private receipt sandbox, filename sanitization & PDF renderer
│   │   │   │   │   └── local/                         # AutoLogDatabase (v2), DAOs, Entities, Converters, ThemePreferences
│   │   │   │   └── ui/
│   │   │   │       ├── CarViewModel.kt                # StateFlows, filtering, stats & backup orchestration
│   │   │   │       ├── components/                    # ExpenseCard, ReminderCard & Formatters
│   │   │   │       ├── screens/                       # Dashboard, Expenses, Reminders, Analytics & Dialogs
│   │   │       │       └── theme/                         # Color palettes, 7 ThemeMode presets & Typography
│   │   │   └── res/                                   # Adaptive icons, strings, themes & network_security_config.xml
│   │   └── test/java/com/autolog/carcare/
│   │       ├── data/
│   │       │   ├── BnrExchangeRateServiceTest.kt      # BNR/Frankfurter JSON parsing & rate validation tests
│   │       │   ├── ReceiptStorageManagerTest.kt       # Path traversal sanitization & PDF detection tests
│   │       │   └── local/VehicleAndCategoryTest.kt    # Vehicle naming, Room Converters & category metadata tests
│   │       └── ui/
│   │           ├── CarAnalyticsAndFilterTest.kt       # Search/date/category filters, Cost-per-km & urgent reminders
│   │           ├── components/CurrencyAndFormattersTest.kt # RON/EUR conversions & odometer/currency formatting
│   │           └── theme/ThemeModeTest.kt             # 7-preset ThemeMode cycle & dark preset flags
│   └── build.gradle.kts
├── gradle/
│   ├── gradle-daemon-jvm.properties                   # Toolchain JVM 25 configuration
│   ├── libs.versions.toml                             # Gradle Version Catalog
│   └── wrapper/                                       # Gradle 9.6.0 wrapper
├── local.properties.example                           # Template for local Android SDK path
├── LICENSE
└── README.md
```

---

## How Currency Sync, Receipts & Backups Work

1. **On-Demand Rate Sync (`BnrExchangeRateService`)**:
   - Network requests are never executed automatically in the background; `fetchLatestEurRate()` runs only when the user explicitly taps the **Sync BNR** action.
   - Enforces `HttpsURLConnection` with redirects disabled (`instanceFollowRedirects = false`), caps responses at `32,768` characters, validates rate bounds (`1.0 .. 25.0`), and requires strict `YYYY-MM-DD` date strings before updating `ThemePreferences`.
2. **Private Receipt Sandbox (`ReceiptStorageManager`)**:
   - Imported photos and PDFs are stored strictly inside the app's private `files/receipts/` directory without requesting external storage permissions.
   - All filenames are sanitized (`..` stripped, unsafe characters normalized, canonical path verified inside `receipts/`), and incoming streams are bounded to a **15 MB** per-attachment cap.
3. **Hardened `.zip` Backups (`BackupManager`)**:
   - Exports use Android's Storage Access Framework (`CreateDocument`) or scoped `MediaStore.Downloads` APIs with **zero cloud SDKs**.
   - Imports block Zip-Slip traversal (`..` entries rejected) and enforce strict decompression size limits (`10 MB` JSON metadata cap, `15 MB` single receipt cap, `150 MB` total archive cap).

---

## Privacy, Security & Permissions

- **Local-First & Zero Tracking**: Vehicles, expenses, reminders, receipts, and preferences stay strictly on-device in Room SQLite (`AutoLogDatabase`), `SharedPreferences`, and internal files. Zero analytics, telemetry, user accounts, or ad SDKs exist.
- **No Automatic Cloud Upload**: `AndroidManifest.xml` configures `android:allowBackup="false"` and `android:fullBackupOnly="false"`, ensuring vehicle financial data and receipts are never uploaded silently to cloud backup servers.
- **Strict TLS Transport Policy**: `AndroidManifest.xml` disables cleartext HTTP (`android:usesCleartextTraffic="false"`), and `network_security_config.xml` explicitly restricts system CA HTTPS connections to `bnr.ro` and `api.frankfurter.dev`.

| Permission (`AndroidManifest.xml`) | Why It Is Needed |
| :--- | :--- |
| `INTERNET` | Strictly for manual, user-initiated EUR/RON exchange rate sync against `bnr.ro` / `api.frankfurter.dev`. Transmits zero vehicle or user data. |

---

## Getting Started

### Prerequisites
- **Android Studio** or **JDK 25** (configured via `gradle/gradle-daemon-jvm.properties`) with the **Android SDK** (`compileSdk = 35`, `minSdk = 26`).

### Setup & Build
1. Clone the repository:
   ```bash
   git clone https://github.com/claudiuroata/AutoLog.git
   cd AutoLog
   ```
2. Configure your local Android SDK path (or open the project in Android Studio to generate `local.properties` automatically):
   ```bash
   cp local.properties.example local.properties
   # Edit local.properties and set sdk.dir to your Android SDK location
   ```
3. Build the debug APK using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
   *(On Windows PowerShell: `.\gradlew.bat assembleDebug`)*

   The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Running Unit Tests

Run all local JVM unit tests across every feature using the Gradle wrapper:

```bash
./gradlew testDebugUnitTest
```

Included test suites:
- `VehicleAndCategoryTest` — `Vehicle.fullDisplayName` formatting, Room `Converters` round-trips/unknown fallbacks, and metadata checks for all 10 `ExpenseCategory` entries.
- `CurrencyAndFormattersTest` — RON/EUR conversions, invalid rate fallbacks, currency display formatting, and odometer string formatting.
- `ThemeModeTest` — Dark preset classification and 7-mode theme cycle wrap-around.
- `ReceiptStorageManagerTest` — Path-traversal sanitization (`..`, Windows/Unix separators), 100-character filename caps, blank defaults, and case-insensitive PDF detection.
- `BnrExchangeRateServiceTest` — BNR daily series comma-decimal parsing, chronological latest-date selection, Frankfurter fallback JSON parsing, and out-of-range/malformed rejection.
- `CarAnalyticsAndFilterTest` — Category, search query, and date-window expense filtering, spend totals, odometer-delta Cost Per km calculation, and 14-day / 1,000-km reminder urgency counts.

---

## Dependencies, External Services & Third-Party Licenses

AutoLog links against the following open-source libraries via Gradle (`gradle/libs.versions.toml`, `app/build.gradle.kts`) and includes the Gradle Wrapper (`9.6.0`):

| Dependency / Service | Scope | Purpose | License |
| :--- | :--- | :--- | :--- |
| **AndroidX Core, Lifecycle, Activity & Navigation** (`core-ktx:1.15.0`, `lifecycle-*:2.8.7`, `activity-compose:1.9.3`, `navigation-compose:2.8.4`) | Runtime | Core Android, Compose lifecycle & navigation APIs | Apache License 2.0 |
| **Jetpack Compose & Material 3** (`compose-bom:2024.11.00`, `material3`, `material-icons-extended`) | Runtime | Declarative UI & Material Icons | Apache License 2.0 |
| **AndroidX Room** (`room-runtime`, `room-ktx`, `room-compiler:2.8.5`) | Runtime | Local SQLite database, DAOs & schema migrations | Apache License 2.0 |
| **Gradle Wrapper (`9.6.0`)** (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) | Build Tooling | Cross-platform Gradle build wrapper | Apache License 2.0 |
| **JUnit 4** (`junit:4.13.2`) | Unit Tests Only | Local JVM unit testing framework | Eclipse Public License 1.0 |
| **org.json** (`org.json:json:20240303`) | Unit Tests Only | Pure JVM JSON parsing for unit tests | The JSON License |
| **BNR / ECB Frankfurter Rate Endpoints** (`bnr.ro`, `api.frankfurter.dev`) | External Reference API | Optional manual EUR/RON official exchange rate lookup | Public reference data |

---

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.
