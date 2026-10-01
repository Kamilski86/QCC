# Duplicate finder UX + QCC isolation (branch fix/duplicate-finder-ux)

## Phase 0/1 – Stabilität (QCC darf nie durch Duplicates blockiert werden)
- [x] Logging (Tag `QccZebra`): Modus-Wechsel, Inventory.perform-/Locate-Stop-Fehler
- [x] Trigger-Modus mit Besitzer (`TriggerModeArbiter`, claim/release) statt last-writer-wins
- [x] `startLocating` nur bei `screenActive`
- [x] `ScanViewModel.setSuspended` – QCC ignoriert Scanner-Events solange Duplicates offen ist
- [ ] Gerätetest: Dashboard -> Duplicates -> Dashboard mehrfach, schnell und langsam; Logcat `QccZebra` prüfen

## Phase 2 – Funde & Meldungen
Entscheidung (User): Confirm find zählt auch bei bereits bekannter TID und vermerkt "already found at: DATUM"
- [ ] OFFEN klären: gleiche TID mehrfach in derselben Suche zählen? (Default: nur 1x je Suche; frühere Suche -> zählt + Vermerk)
- [ ] Datenmodell: Funde je Suche; Verweis auf frühesten früheren Fund (Datum) -> Migration 7->8, CSV-Spalte
- [ ] `lastTid` aus DB wiederherstellen
- [ ] Meldungen (außer Saved) als AlertDialog mit OK; Trigger während Dialog ignorieren
- [ ] Strings EN/DE/PL

## Phase 3 – Dashboard
- [ ] QR-/RFID-Scan-Button entfernen; Reset + Export bleiben; "Find duplicates"-Button darunter

## Phase 4 – Duplicates-Masken
- [ ] Maske 1: Apply + "Read with reader" weg (Hinweistext bleibt), Live-Parse, Start parst nochmal
- [ ] Maske 2: ohne Scrollen, TID-Zeile zwischen EPC und Store, "Last" in Zähler-Karte entfällt
- [ ] Robolectric-Layouttest (kleines Display)

## Phase 5 – Verifikation
- [ ] Unit-Tests, assembleDebug, Gerätetest inkl. 2 Duplikate, danach Merge nach main
