# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is an Android project built with Gradle. All commands run from the repo root.

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.SomeTestClass"

# Lint
./gradlew lint

# Clean
./gradlew clean
```

There is no emulator target — the app requires physical Honeywell CT37 (barcode) and Zebra RFD8500 (RFID) hardware. There are no instrumented tests currently. Unit tests use Robolectric + in-memory Room (see `app/src/test/.../testutil`). `./gradlew lintDebug` currently crashes in this environment with "Exception occurred when reading Kotlin metadata" (also on the pre-feature baseline commit 499466a) — known issue, not caused by the duplicate finder.

## Documentation

Full application documentation (background, requirements, architecture, data model, operations, security, glossary) lives in `docs/confluence/` (start at `00-index.md`). Update the affected pages with every behaviour, schema or interface change (checklist: `docs/confluence/15-anhang.md`, section B).

## Architecture Overview

**Stack:** Kotlin + Jetpack Compose + Material3, MVVM, Hilt DI, Room, Jetpack Navigation Compose. Min SDK 28, Target SDK 34.

### Layer structure

```
app/                     — Activity, Hilt entry point, NavHost, drawer
navigation/              — Screen sealed class (route definitions)
feature/{name}/          — Screen composable + ViewModel per use case
feature/duplicates/      — Duplicate finder: screen + ViewModel + proximity feedback (tone/vibration, gauge)
domain/model/            — Shared data classes (Models.kt, all @Serializable)
domain/usecase/          — Business logic (UseCases.kt)
data/local/              — Room: Entities, Daos, Migrations, AppDatabase
data/repository/         — MismatchRepository (bridges Room + CSV export), DuplicateSearchRepository
data/export/             — CsvDocumentWriter (shared CSV file writer)
device/honeywell/        — HoneywellCt37ScannerGateway (barcode, via AIDC SDK AAR)
device/zebra/            — ZebraRfd8500Gateway + RfidReaderGateway interface (RFID, via Zebra SDK AAR)
core/gs1/                — Gs1ParserService (GS1/EPC parsing, pure Kotlin, no Android deps)
di/                      — AppModule + GatewayModule (Hilt @Singleton bindings)
```

Vendor SDKs (Honeywell AIDC, Zebra RFID) are local AARs in `app/libs/` and `vendor/zebra/` — not on Maven.

### Key architectural decisions

**ViewModels are scoped at NavHost level in MainActivity**, not per-destination. `ScanViewModel`, `DashboardViewModel`, and `ExportViewModel` are all created via `hiltViewModel()` at the top-level composable and passed down as state/callbacks. This means they live for the full activity lifetime. Any future use-case screens should scope their own ViewModels to their composable destination instead.

**Hardware gateways are @Singleton.** `ZebraRfd8500Gateway` auto-connects to the first discovered Bluetooth reader in `ScanViewModel.init{}`. `HoneywellCt37ScannerGateway` is lifecycle-managed directly in `MainActivity` (`onResume`/`onPause`/`onDestroy` → `claim`/`release`/`close`). Both gateways emit results as `Flow` — hardware events arrive on SDK threads and are emitted via `MutableSharedFlow.tryEmit()`.

**Zebra gateway is single-tag mode by design.** `ZebraRfd8500Gateway` uses an `AtomicBoolean inventoryActive` to emit exactly one EPC per inventory session, then stops immediately. This is intentional for the QCC use case (one item at a time). A multi-tag collection mode does not exist yet. The gateway also has trigger modes (`TriggerMode`: QCC / CAPTURE / LOCATE); only QCC runs the single-tag inventory and emits `observeTriggerEvents()`, so `ScanViewModel` is unchanged. CAPTURE/LOCATE (duplicate finder) use `observeHandheldTrigger()`, `TagLocationing`, and nearby reads (`readEpcsNearby`, `readTidsNearby`) at 5 % of the power table (verified on hardware); the TID is read with 96 bit. Hardware finding: the RFD8500 accepts only locate masks that are contiguous from the MSB, so locating is always exact (`LocateTarget.FULL_MASK`); typed SGTINs use a selectable filter value (default 1). `setTriggerMode(QCC)` stops an active locate. Non-QCC screens must claim the trigger via `claimTriggerMode(owner, mode)` / `releaseTriggerMode(owner)` (`TriggerModeArbiter`): only the latest owner can hand it back to QCC, so a late-torn-down screen cannot reset a newer one. While the duplicate finder is on screen, `ScanViewModel.setSuspended(true)` makes QCC ignore scanner events.

**GS1 parsing is fully in `Gs1ParserService`.** It handles:
- GS1 DataMatrix QR codes (`/01/{GTIN-14}/21/{serial}`) → `QrScanResult`
- SGTIN-96 EPC hex (24 hex chars) → `RfidScanResult`
- SGTIN pure identity string construction from GTIN + serial

SSCC (Application Identifier `00`, 18 digits) parsing does **not** exist yet.

### Navigation

Routes are defined in `navigation/Nav.kt` as a sealed class. Hard-coded string routes (`"reader"`, `"language"`, `"about"`) are also used directly in `MainActivity`. The Dashboard has Reset, Export and a "Find duplicates" button (no manual QR/RFID scan buttons — scanning is always via the gun trigger). The app currently goes `Splash → Dashboard` with a `ModalNavigationDrawer` providing access to settings screens and the duplicate finder (route `"duplicates"`). Its `DuplicateSearchViewModel` is destination-scoped (the model for future use-case screens); `onScreenStopped()` on ON_STOP/dispose resets the trigger mode to QCC. The keyboard mode is switched to ADJUST_RESIZE only while this screen is active (otherwise ADJUST_NOTHING).

### Database

Room DB (`qcc.db`) is currently at schema version 8. Four tables: `scans` (every comparison result), `mismatches` (mismatch-only subset), `duplicate_searches` and `duplicate_finds` (duplicate finder; one find per TID *per search* — a tag found again in a later search counts again and stores `previousFoundAt`, shown in a dialog and in the CSV column "Previously Found"). Migrations live in `data/local/Migrations.kt`. SQLite on API < 35 has no `DROP COLUMN` — recreate-table pattern is used (see `MIGRATION_5_6`).

### String localization

Two locale variants: `values/` (EN), `values-de/` (DE) (the Polish translation was removed on 2026-10-01). All user-visible strings must be added to both files. The app default language is English (`values/`). The duplicate finder adds `dup_*` keys in EN/DE; its ViewModel returns typed messages (`DuplicateMessage`) that the UI localizes.

## Workflow Orchestration

### 1. Plan Node Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One tack per subagent for focused execution

### 3. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

## Task Management

1. **Plan First**: Write plan to `tasks/todo.md` with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to `tasks/todo.md`
6. **Capture Lessons**: Update `tasks/lessons.md` after corrections

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.

## Planned Changes (context for future work)

1. **App rename** — Display name changing from "QCC" to "MV-C". Hardcoded `"QCC"` literals remain in `DashboardScreen.kt` (title) and `AboutScreen.kt`, pending replacement with `stringResource(R.string.app_name)` (`MainActivity` already uses it).

2. **Modular tile home screen** — A new `Home` screen with use-case tiles will replace the current direct `Splash → Dashboard` flow. QCC becomes one tile; additional use cases will be added as tiles. ViewModels will need to move to per-destination scope.

3. **Inbound Read use case** — New feature requiring: SSCC barcode parsing, multi-tag RFID inventory, EPCIS 2.0 `AggregationEvent` construction, and HTTP POST to an EPCIS endpoint (Retrofit + OkHttp — not yet in the project). The `INTERNET` permission is not yet declared in `AndroidManifest.xml`.
