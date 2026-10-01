# Honeywell IH45 als zweiter RFID-Reader (Branch feature/honeywell-ih45)

Entscheidungen (User, 2026-10-01): IH45 bevorzugt per USB, Bluetooth als Backup. QR-Erfassung wie bisher
über den Trigger des RFID-Readers (Pistole). Beide Reader unterstützt, aber genau einer gleichzeitig
verbunden: Zebra RFD8500 per Bluetooth, IH45 per USB (Backup BT). Honeywell-AAR darf ins Repo.

Bereits erledigt (uncommittet): AAR in `vendor/honeywell/`, fileTree in `app/build.gradle.kts`,
`API3_LLRP*` ausgeschlossen (Klassenkonflikt `a.a`). Debug+Release bauen.

## Phase 0 – Hardware-Klärung (blockiert Phase 2/3)
- [ ] Wie meldet der IH45 per USB? `getAttachedUsbDevice()` / `onUsbDeviceAttached`, USB-Permission nötig?
- [ ] Wie QR über den IH45-Trigger? `TriggerMode.BARCODE` vs `RFID`: QCC braucht RFID-Read UND QR-Scan pro Druck
- [ ] BT-Gerätename des IH45 (Filter für gekoppelte Geräte)
- [ ] Minimale Sendeleistung (dBm) für Nahlesung, brauchbare Leistungsskala für Level 1..N
- [ ] Locate: eingebautes `setLocateTagEPC` vs. RSSI aus Reads

## Phase 1 – Reader-Auswahl (Architektur)
- [ ] `RfidReaderGateway` unverändert lassen; `SwitchingRfidReaderGateway` (@Singleton, an Hilt gebunden) delegiert an Zebra oder IH45
- [ ] Genau ein aktiver Reader: Wechsel trennt zuerst den alten; Status/Trigger/Proximity-Flows werden durchgereicht
- [ ] Einstellung "Reader-Typ" (Auto | Zebra | IH45) in den Reader-Einstellungen, DataStore/SharedPrefs; EN+DE Strings
- [ ] Auto: IH45 per USB angeschlossen -> IH45; sonst gekoppelter Zebra; sonst IH45 per BT
- [ ] Tests mit Fakes (Auswahl, Wechsel, Trigger-Arbiter bleibt gültig)

## Phase 2 – `HoneywellIh45Gateway` (device/honeywell/)
- [ ] `RfidManager.create` (async Service), `createReader`, Lifecycle/Release
- [ ] Verbindung: USB zuerst; bei Fehler/Abzug automatisch BT-Fallback (gekoppeltes Gerät); Status zeigt Transport
- [ ] QCC: Trigger -> `read()` -> erster EPC -> `stopRead()` (Single-Tag wie Zebra, `AtomicBoolean`)
- [ ] `setPower(level)` -> `AntennaPower` (dBm-Mapping aus Phase 0)
- [ ] Trigger-Modi QCC/CAPTURE/LOCATE über `onRfidTriggered`; `claimTriggerMode`-Semantik wie Zebra
- [ ] `readEpcsNearby`, `readTidsNearby` (TID_BANK) bei Minimalleistung; Locate -> `observeProximity` 0..100

## Phase 3 – Release & Betrieb
- [ ] Proguard: Keep-Regeln `com.uhf.**`, `com.silionmodule.**`, obfuskierte Top-Level-Pakete des SDK; Release auf Gerät prüfen
- [ ] Manifest: `uses-feature usb.host` (required=false); USB-Permission-Flow
- [ ] RFD8500-Regressionstest auf Gerät (LLRP ausgeschlossen!)
- [ ] Doku: 05, 07, 08, 10, 11, 12 (ADR-20), 13, 14; CLAUDE.md Architektur; `ScanViewModel`-Meldung "Kein Zebra-Reader" neutral

## Review (2026-10-01)
- [x] Phase 1: `SwitchingRfidReaderGateway` (IH45 per USB hat Vorrang, sonst Zebra; genau ein Reader; Hot-Plug)
- [x] Phase 2: `HoneywellIh45Gateway` (USB, QCC Einzel-Tag per stärkstem RSSI, Tag übernehmen/Bestätigen, Ortung mit Leistungsstufen)
- [x] Phase 3 (teilweise): ProGuard-Regeln + Manifest + LLRP-Ausschluss; Doku 00-15, ADR-20/21, CLAUDE.md
- [x] Duplikatsuche: Bestätigen durch Loslassen am Tag (>= 95 %), Trigger = OK, Bestätigungs-/Warnton; Reader-Icon + Toast
- [x] 118 Unit-Tests grün; auf CT37 + IH45 abgenommen: Auto-Connect, QCC, Tag übernehmen, Ortung, Bestätigen
- [ ] Offen: Release-Build mit IH45 auf dem Gerät, RFD8500-Regressionstest (LLRP ausgeschlossen), Bluetooth-Backup des IH45,
      USB-Abbrüche des IH45 klären, Diagnose-Ansicht (`feature/diagnostics`) entfernen
- Lektion: Sendeleistung taugt beim IH45 nicht zur Reichweitenbegrenzung; RSSI ändert sich nicht mit der Leistung und sättigt im
  Nahfeld -> Auswahl per stärkstem RSSI, Nähe im Nahbereich über Leistungsstufen. Timeouts mit `withTimeoutOrNull` (withTimeout wird als Abbruch verschluckt).

---
# (Archiv) Duplicate finder UX

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
