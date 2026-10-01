# AGENTS.md

Guidance for AI coding agents working with the QCC codebase.

## Project Overview

**QCC** is an Android quality-control app for RFID and QR code verification. It compares GTIN/serial from QR scans (barcode via Honeywell CT37) against RFID tags (EPC via Zebra RFD8500), using GS1 standard encoding to detect mismatches.

**Stack:** Kotlin + Jetpack Compose + Material3 · MVVM + Hilt DI · Room DB · Min SDK 28, Target SDK 34

---

## Architecture Map

### Layer Structure (Clean Architecture)
```
app/                  — MainActivity, Hilt @HiltAndroidApp, NavHost setup
feature/{name}/       — UI: Composable screens + ViewModels (@HiltViewModel)
domain/model/         — Shared entities (Models.kt, all @Serializable)
domain/usecase/       — CompareScansUseCase (comparison business logic)
data/local/           — Room: Entities, DAOs, Migrations (schema v8)
data/repository/      — MismatchRepository (Room → CSV export bridge)
device/{vendor}/      — Hardware gateways (Honeywell, Zebra)
core/gs1/             — Gs1ParserService (GS1/EPC parsing, pure Kotlin)
di/                   — Hilt modules (AppModule + GatewayModule)
ui/                   — Shared Compose components
navigation/           — Screen routes (sealed class)
```

### Key Data Models
- **QrScanResult** — GTIN + serial from QR code → SGTIN
- **RfidScanResult** — SGTIN-96 EPC hex (24 chars) → GTIN + serial
- **ComparisonResult** — Match/mismatch enum + message
- **MismatchRecord** — Persisted mismatch row (Room entity)

---

## Critical Architectural Decisions

### ViewModel Scope (Non-Standard)
**ViewModels are scoped at NavHost level, not per-destination.** See `MainActivity.kt`:
```kotlin
val scanVM = hiltViewModel<ScanViewModel>()
val dashboardVM = hiltViewModel<DashboardViewModel>()
```
All three ViewModels (`ScanViewModel`, `DashboardViewModel`, `ExportViewModel`) live for the activity's entire lifetime. **Future use-case screens should switch to per-destination scope** (one ViewModel per composable destination).

### Hardware Gateways Are @Singleton
- **ZebraRfd8500Gateway** — Auto-connects in `ScanViewModel.init{}`, emits tags as `Flow<String>` (EPC hex)
- **HoneywellCt37ScannerGateway** — Lifecycle-bound to `MainActivity.onResume/onPause/onDestroy` (claim/release/close)
- Both emit results via `MutableSharedFlow.tryEmit()` on SDK threads (no blocking)

### Zebra Single-Tag Mode (Intentional)
`ZebraRfd8500Gateway` uses `AtomicBoolean inventoryActive` to emit **exactly one EPC per inventory session**, then stops. This is QCC design (one item at a time). Multi-tag collection does not exist.

### GS1 Parsing Encapsulation
`Gs1ParserService` (pure Kotlin, no Android deps) handles:
- **QR DataMatrix:** `/01/{GTIN-14}/21/{serial}` → `QrScanResult`
- **SGTIN-96 EPC:** 24 hex chars → `RfidScanResult` (GTIN + serial extracted)
- **Checksum:** EAN-13 check digit calculation (GTIN validation)

**SSCC parsing (AI `00`) does not exist yet.**

### Navigation Routes
```kotlin
sealed class Screen(val route: String) {
    Splash, Dashboard, Scan, Export, Reader, Language, About
}
```
Hard-coded string routes (`"reader"`, `"language"`, `"about"`) also exist in `MainActivity`. Next refactor: move all routes to sealed class.

### Room Database
- **Schema version 8** (see `Migrations.kt`)
- **Tables:** `scans` (all results), `mismatches` (mismatch subset), `duplicate_searches`, `duplicate_finds` (one find per TID per search; `previousFoundAt` marks a re-found tag)
- **Migration constraint:** SQLite API < 35 has no `DROP COLUMN` → use recreate-table pattern (see `MIGRATION_5_6`)
- DB file: `qcc.db`

---

### Duplicate Finder & Trigger Ownership
The duplicate finder (`feature/duplicates/`) is destination-scoped. It claims the Zebra trigger via `claimTriggerMode(owner, mode)` / `releaseTriggerMode(owner)` (`TriggerModeArbiter`: only the latest owner may hand it back to QCC) and `ScanViewModel.setSuspended(true)` pauses QCC while that route is open. Full documentation: `docs/confluence/00-index.md`.

---

## Common Workflows

### Add a New Feature Screen
1. **Create composable** in `feature/{name}/{Name}Screen.kt`
2. **Create ViewModel** at `feature/{name}/{Name}ViewModel.kt` with `@HiltViewModel`
3. **Add route** to `navigation/Nav.kt` sealed class
4. **Instantiate ViewModel** in `MainActivity.kt` and pass as state/callbacks (current pattern)
5. **Register Hilt deps** in `di/AppModule.kt` if needed

### Database Schema Change
1. **Increment `AppDatabase.version`**
2. **Create `MIGRATION_x_y.kt`** in `data/local/`
3. **Add migration to `AppModule.kt` Room builder**
4. For `DROP COLUMN`: use recreate-table pattern (copy rows → drop → rename)

### Parse QR or RFID
Always go through `Gs1ParserService`:
```kotlin
val parser = Gs1ParserService()
val qr = parser.parseQr(rawString)     // → QrScanResult
val rfid = parser.parseEpcToSgtin(epc) // → RfidScanResult
```

### Emit Hardware Results
```kotlin
// In gateway: Zebra, Honeywell threads call tryEmit()
tagFlow.tryEmit(epc)  // Non-blocking; drops if buffer full

// In ViewModel: Collect on viewModelScope
rfidReader.observeTagScans().onEach { epc ->
    val result = parser.parseEpcToSgtin(epc)
    // Update UI state
}.launchIn(viewModelScope)
```

---

## Build & Test Commands

**All run from repo root:**
```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew test                   # All unit tests
./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.SomeClass" # Single test
./gradlew lint                   # Lint report
./gradlew clean                  # Clean build
```

**Hardware notes:**
- **No emulator target** — requires physical Honeywell CT37 (barcode) + Zebra RFD8500 (RFID)
- **No instrumented tests** (no emulator/physical device testing suite)
- Vendor SDKs are local AARs: `app/libs/` (Honeywell) + `vendor/zebra/` (Zebra)

---

## Localization

Three locale variants maintained in parallel:
- `values/` — English (EN)
- `values-de/` — German (DE)
- `values-pl/` — Polish (PL)

**All user-visible strings must be added to all three files.** No exceptions.

---

## Project-Specific Patterns

### Hilt Dependency Injection
- **Service bindings:** `@Binds @Singleton` in `GatewayModule` (e.g., `BarcodeScannerGateway`, `RfidReaderGateway`)
- **Provider functions:** `@Provides @Singleton` in `AppModule` (Room, Repositories)
- **ViewModels:** `@HiltViewModel @Inject constructor(deps)`

### State Management (Compose)
- `MutableStateFlow<T>` for read/write state (ViewModel)
- `StateFlow<T>` exposed via `.asStateFlow()` (immutable view)
- `MutableSharedFlow<T>` for event streams (hardware results, no initial value)

### Error Handling
Error messages are user-readable English/German/Polish. Parse errors in `Gs1ParserService` throw `IllegalArgumentException` with message keys (translated in callers). Hardware errors are logged and UI shows status.

---

## Planned Changes (Roadmap)

1. **App rename** — "QCC" → "MV-C" (hardcoded "QCC" literals remain in `DashboardScreen` (title) and `AboutScreen`; `MainActivity` already uses `stringResource(R.string.app_name)`)
2. **Modular tile home screen** — `Splash → Dashboard` replaced with `Home` screen containing use-case tiles (Scan, Inbound Read, etc.). **ViewModels will move to per-destination scope.**
3. **Inbound Read use case** — SSCC barcode parsing + multi-tag RFID inventory + EPCIS 2.0 integration (Retrofit + OkHttp). **INTERNET permission not yet in `AndroidManifest.xml`.**

---

## Vendor SDK Notes

**Local AAR files (not Maven Central):**
- `app/libs/DataCollection.aar` — Honeywell AIDC SDK
- `app/libs/hedc-release.aar` — Honeywell barcode engine
- `vendor/zebra/API3_*.aar` (11 modules) — Zebra RFID SDK

**Import path:** `com.zebra.rfid.api3.*`, `com.honeywell.*` — check actual class names in AAR docs.

---

## Conventions to Follow

- **File naming:** `{Feature}{Type}.kt` (e.g., `ScanViewModel.kt`, `ScanScreen.kt`)
- **Package structure:** Mirror folder path exactly
- **Serialization:** All domain models marked `@Serializable` (kotlinx.serialization)
- **Logging:** Use Android `Log.d/e/w` (no third-party logger)
- **Compose:** Material3 design; use `stringResource(R.string.*)` for all text
- **DB migrations:** Always test schema changes manually on API 28+ device

---

## When in Doubt

1. **See CLAUDE.md** for detailed build commands and workflow guidance
2. **Check Gs1ParserService.kt** for GS1/EPC parsing logic (comprehensive)
3. **Review ScanViewModel.kt** for hardware polling and comparison flow
4. **Study MainActivity.kt** for Hilt setup, lifecycle, drawer navigation
5. **Examine data/local/Migrations.kt** for DB schema patterns

