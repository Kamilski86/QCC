# Duplicate finder UX + QCC isolation (branch fix/duplicate-finder-ux)

## Phase 0/1 – Stabilität (QCC darf nie durch Duplicates blockiert werden)
- [x] Logging (Tag `QccZebra`): Modus-Wechsel, Inventory.perform-/Locate-Stop-Fehler
- [x] Trigger-Modus mit Besitzer (`TriggerModeArbiter`, claim/release) statt last-writer-wins
- [x] `startLocating` nur bei `screenActive`
- [x] `ScanViewModel.setSuspended` – QCC ignoriert Scanner-Events solange Duplicates offen ist
- [ ] Gerätetest: Dashboard -> Duplicates -> Dashboard mehrfach, schnell und langsam; Logcat `QccZebra` prüfen

## Phase 2 – Funde & Meldungen
Entscheidung (User): Confirm find zählt auch bei bereits bekannter TID und vermerkt "already found at: DATUM"
- [x] Entscheidung (Option A): je Suche zählt eine TID 1x; aus früherer Suche bekannt -> zählt + Vermerk mit Datum
- [x] Datenmodell: Funde je Suche; Verweis auf frühesten früheren Fund (Datum) -> Migration 7->8, CSV-Spalte
- [x] `lastTid` aus DB wiederherstellen
- [x] Dialog mit OK für "Already found" und "Found again" (Trigger/Locate pausieren bis OK). Andere Meldungen bleiben Karten – Platz in Phase 4 klären
- [x] Strings EN/DE/PL

## Phase 3 – Dashboard
- [x] QR-/RFID-Scan-Button entfernen; Reset + Export bleiben; "Find duplicates"-Button darunter

## Phase 4 – Duplicates-Masken
- [x] Maske 1: Apply + "Read with reader" weg (Hinweistext bleibt), Live-Parse, Start parst nochmal
- [x] Maske 2: ohne Scrollen, TID-Zeile zwischen EPC und Store, "Last" in Zähler-Karte entfällt
- [x] Robolectric-Layouttest (kleines Display)

## Phase 5 – Verifikation
- [x] Unit-Tests, assembleDebug (Rest offen:) Gerätetest inkl. 2 Duplikate, danach Merge nach main

## Offen (Gerät)
- [ ] Layout auf echtem CT37 prüfen (Gauge im Worst Case mit Store ca. 82 dp im Test bei 360x600 dp)
- [ ] Dashboard -> Duplicates -> Dashboard mehrfach; Test mit 2 Duplikaten + Wiederfund (Datum im Popup)
- [ ] Merge nach main
