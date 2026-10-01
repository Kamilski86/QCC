# Developer Sicht - Technische Dokumentation

> **Archiv (Stand April 2026).** Dieses Dokument wird nicht mehr gepflegt und ist teilweise überholt (z. B. Schema v6, Scan-Buttons im Dashboard, Sendeleistungs-Einstellung). Maßgeblich ist die aktuelle Dokumentation unter [`docs/confluence/`](confluence/00-index.md).

## Dokumentsteuerung

- Version: 1.1
- Zielgruppe: Entwickler und technische Reviewer
- Quellen: `AGENTS.md`, `CLAUDE.md`, `docs/02-business-architect-architecture.md`

## 1) Kurzueberblick

QCC ist eine Android-App zur Pruefung von QR/RFID-Paaren im Einzelartikelmodus.
Der Kernfluss liegt in `ScanViewModel`: Daten empfangen -> parsen -> vergleichen -> persistieren -> rueckmelden.

## 2) Tech Stack

- Kotlin
- Jetpack Compose + Material3
- MVVM + Hilt DI
- Coroutines + Flow/StateFlow
- Room (SQLite)
- Vendor SDKs als lokale AARs (Honeywell/Zebra)

## 3) Projektlayout

```text
app/src/main/java/de/ca/qcc/
  app/                 MainActivity, App
  feature/             UI Screens + ViewModels
  domain/model/        Datenmodelle
  domain/usecase/      Fachlogik
  data/local/          Room, DAO, Migrationen
  data/repository/     Persistenz + Export
  device/honeywell/    Barcode Gateway
  device/zebra/        RFID Gateway
  core/gs1/            Parser/Validierung
  navigation/          Routen
  di/                  Hilt Module
```

## 4) Onboarding fuer Entwickler

### Voraussetzungen
- JDK und Android Studio passend zum Gradle-Setup
- Zugriff auf lokale Vendor AARs im Projekt
- Optional: CT37 + RFD8500 fuer E2E Hardwaretests

### Build/Checks

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## 5) Kernmodule und Verantwortlichkeiten

### `feature/scan/ScanViewModel.kt`
- Scan-Zyklussteuerung inklusive `startNewCycleIfNeeded()`
- Sammlung von QR/RFID Events
- Vergleichsausloesung via `compareIfReady()`
- Tone/Vibration Feedback

### `core/gs1/Gs1ParserService.kt`
- Parsing QR (`/01/.../21/...`)
- Parsing manuelle GTIN
- Parsing EPC (SGTIN-96)
- Guard Rails fuer ungueltige Inputs

### `domain/usecase/UseCases.kt`
- `CompareScansUseCase` als fachlicher Entscheidungsbaustein
- Normalisierung, Match/Mismatch Entscheidung
- Persistenzaufrufe in Repository

### `data/repository/Repositories.kt`
- Room Mapping zu Domain-Modellen
- Tageszaehler (Flow)
- CSV Export in Documents

### `device/zebra/ZebraRfd8500Gateway.kt`
- Discovery, Connect/Disconnect
- Eventlistener fuer Trigger/Tag-Reads
- Single-Tag Inventory per Session

## 6) Entwicklungsregeln (projektkonkret)

- Neue Features nur in klaren Feature-Paketen erweitern
- UI-Texte nicht hart kodieren; Ressourcen in drei Sprachen pflegen
- Fehlertexte benutzerlesbar halten
- Keine direkte SDK-Nutzung im UI-Layer; immer Gateway nutzen
- Datenbankschema nur mit Migration aendern

## 7) State- und Event-Modell

- Durable UI State: `MutableStateFlow` / `StateFlow`
- Ereignisse aus Hardware: `MutableSharedFlow` aus Gateways
- Vergleichstrigger nur bei vollstaendigen Eingangsdaten
- Zyklusreset nur bei abgeschlossenem vorherigem Zyklus

## 8) Typische Erweiterungsaufgaben

### A) Neuer Bildschirm
1. `feature/<name>/<Name>Screen.kt` erstellen
2. `feature/<name>/<Name>ViewModel.kt` mit `@HiltViewModel`
3. Route in `navigation/Nav.kt` hinzufuegen
4. Nav-Integration in `MainActivity.kt`

### B) Neue Business-Regel
1. Fachregel in UseCase oder Parser verorten
2. Auswirkungen auf `ScanUiState` und UI pruefen
3. Unit Tests fuer Regel + Negativfall ergaenzen
4. BA/Tester-Dokumente aktualisieren

### C) Datenbankschema aendern
1. Version erhoehen
2. Migration in `data/local/Migrations.kt`
3. Migration im Room Builder registrieren
4. Export/Abfragen gegentesten

## 9) Debugging Leitfaden

### Scanner liefert keine Daten
- Honeywell Lifecycle-Hooks in `MainActivity` pruefen
- Triggerpfad in `ScanViewModel.triggerQrScan()` pruefen

### RFID liefert kein Ergebnis
- Reader Status in `observeStatus()` pruefen
- Verbindung via `reconnectReader()` testen
- Eventfluss in `ZebraRfd8500Gateway` pruefen

### Vergleich startet nicht
- Vorhandensein von `qr` und `rfid` im `ScanUiState` pruefen
- Parserfehler (`error`) im UI State beobachten

## 10) Teststrategie aus Dev-Sicht

- Unit: Parser, UseCase, ViewModel-Entscheidungslogik
- Integration: Repository + Room
- Manuell: Hardware-E2E auf CT37/RFD8500

## 11) Bekannte technische Schulden

- NavHost-weite ViewModel-Scopes
- Teilweise harte Strings im ViewModel
- Keine instrumentierten Hardwaretests
- Vendor AAR Handling ohne zentrales Artefaktmanagement

## 12) Definition of Done (Dev)

Ein Arbeitspaket ist abgeschlossen, wenn:
- Code kompiliert und relevante Tests gruen sind
- Lint ohne neue Probleme bleibt
- Fachregeln mit AC konsistent sind
- Doku in `docs/` aktualisiert wurde (mindestens BA + Tester bei Verhaltensaenderung)
