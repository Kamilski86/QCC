# Design: Duplikatsuche („Find duplicates“)

- Datum: 2026-09-29
- Status: Freigegeben, angepasst nach Hardware-Spike (2026-09-30)
- Scope: Neuer Use Case in der QCC-App (Honeywell CT37 + Zebra RFD8500)

## 1) Problem und Ziel

Ein Tag-Produzent hat dieselbe SGTIN / denselben EPC auf ca. 480 RFID-Tags codiert. Ein EPC muss eindeutig sein. Die betroffenen Artikel müssen in den Filialen einzeln gefunden, aus dem Verkauf genommen und für die Reklamation dokumentiert werden.

Ziel: Der Anwender gibt eine SGTIN oder einen EPC vor und wird per Nähe-Anzeige, Ton und Vibration mit der RFD8500 zu jedem Duplikat geführt. Jeder Fund wird über die eindeutige Chip-ID (TID) erfasst und am Ende als CSV exportiert.

## 2) Fachliche Entscheidungen

| # | Entscheidung |
|---|---|
| E1 | Gefundene Artikel werden **physisch entfernt** (z. B. Abschirmtasche). Die App „stummschaltet“ keine Tags. |
| E2 | Jeder Fund wird über die **TID** erfasst. Die TID ist global eindeutig; doppelte Bestätigung wird erkannt und nicht gezählt. |
| E3 | Eingabe des Ziels: **Anlesen mit der Pistole** (bevorzugt), **Hex-EPC** (24 Zeichen) oder **SGTIN** ohne URN-Präfix (`4065546.092768.12884901894`) mit wählbarem Filterwert (Standard 1). **Kein QR-Scan**, weil QR- und Tag-Daten abweichen können. |
| E4 | Ton und Vibration über den **CT37**, nicht über den Beeper der RFD8500. |
| E5 | Nähe-Anzeige als **Tacho** (Halbkreis, Verlauf rot → gelb → grün, große Prozentzahl) im bestehenden App-Stil. |
| E6 | Nach „Confirm find“ wird **ohne Rückfrage weitergesucht**. Beendet wird über „End search“. |
| E7 | Pro Bestätigung wird **genau ein** Tag akzeptiert. Bei mehreren Treffern muss der Artikel isoliert werden. |
| E8 | Einstieg über einen neuen Eintrag **„Find duplicates“ im Burger-Menü**. |
| E9 | **Filiale** ist ein optionales Freitextfeld, vorbelegt mit dem zuletzt verwendeten Wert. |
| E10 | Funde werden sofort in Room gespeichert. **CSV-Export automatisch bei „End search“**. |
| E11 | Schalter **„Continuous search“**: Ortung läuft ohne gedrückten Trigger. Standard: aus. |
| E12 | **Englisch ist Standardsprache.** Alle sichtbaren Texte kommen aus String-Ressourcen (`values/` EN, `values-de/`, `values-pl/`). |

## 3) Ablauf und Bildschirm

Route `Screen.Duplicates` (`"duplicates"`), Drawer-Eintrag „Find duplicates“. `DuplicateSearchViewModel` wird per `hiltViewModel()` **in der Destination** erzeugt, nicht auf NavHost-Ebene.

### Phase 1: Ziel festlegen

- Eingabefeld „EPC or SGTIN“. Automatische Erkennung:
  - 24 Hex-Zeichen → EPC (muss SGTIN-96 sein, Validierung über `Gs1ParserService.parseEpcToSgtin`)
  - `Ziffern.Ziffern.Ziffern` → SGTIN; darunter erscheint die Auswahl des Filterwerts 0–7 (Standard 1), siehe „SGTIN-Eingabe ohne Filterwert“
  - sonst → Fehlermeldung, kein Absturz
- In Phase 1 steht der Trigger dauerhaft im Modus `CAPTURE`: Trigger-Druck **oder** Button „Read with reader“ liest sofort genau einen Tag mit Nahbereichs-Leistung (5 % der Maximalleistung) und übernimmt dessen EPC. Werden mehrere Tags gelesen, erscheint ein Hinweis, den Artikel zu isolieren.
- Optionales Feld „Store“ (Filiale).
- Nach gültiger Eingabe: Anzeige von SGTIN und EPC zur Kontrolle, dann Button „Start search“.

### Phase 2: Suche

Layout gemäß freigegebenem Mockup (lokal: `.superpowers/brainstorm/27246-1790681572/content/proximity-visual-v4.html`, nicht versioniert):

1. Weiße TopBar mit Menü-Icon, Titel „Find duplicates“
2. Karte: gesuchte SGTIN, EPC, Filiale
3. Karte: Tacho mit Prozentzahl und Hinweiszeile („Hold trigger and sweep the reader“ bzw. „Continuous search active – sweep the reader“)
4. Karte: Schalter „Continuous search“
5. Karte: Anzahl gefundener Duplikate (eindeutige TIDs) und zuletzt erfasste TID (gekürzt)
6. Schwarze Pill-Buttons mit weißer Schrift (wie `DashboardScreen`): „Confirm find“, „End search“

Die Hintergründe entsprechen dem bestehenden Stil: weiße Karten, schwarze Texte. Der Farbverlauf im Tacho ist das einzige zusätzliche Farbelement.

### „Confirm find“ (TID-Prüfung)

Der Anwender nimmt den vermuteten Artikel heraus, hält ihn direkt an die Pistole und tippt „Confirm find“. Eine laufende Ortung pausiert, das Gateway liest mit Nahbereichs-Leistung (5 % der Maximalleistung) die TIDs (96 Bit) aller Tags mit exakt passendem EPC:

| Ergebnis | Reaktion |
|---|---|
| genau 1 Tag, TID neu | Fund speichern, Zähler +1, Meldung „Find #n saved – remove the item“ |
| 0 Tags | „No duplicate at the reader – wrong item?“ |
| mehr als 1 Tag | „Several duplicates detected – isolate the item“ |
| 1 Tag, TID bereits erfasst | „Already found (#n)“, keine Zählung |

Danach wird die Ortung automatisch fortgesetzt, wenn „Continuous search“ aktiv ist.

### „End search“

Ortung stoppen, Suche als beendet markieren, CSV exportieren, Dateinamen anzeigen, zurück zu Phase 1.

### SGTIN-Eingabe ohne Filterwert

Aus einer SGTIN ist der Filterwert (3 Bit) nicht ableitbar. Die App erzeugt den EPC mit dem **Filterwert 1** (Verkaufsartikel, „point of sale trade item“) als Standard; der Anwender kann unter dem Eingabefeld einen anderen Wert **0–7** wählen. Ein Wechsel des Filterwerts verwirft ein bereits übernommenes Ziel. Ortung und TID-Prüfung suchen **immer exakt** (volle Maske), egal ob EPC angelesen, als Hex eingegeben oder aus einer SGTIN erzeugt wurde. Ein Hex-EPC bringt seinen Filterwert selbst mit; die Auswahl gilt dort nicht. Die Partition ergibt sich aus der Stellenzahl des Company Prefix (6–12 Stellen).

Ursprünglich war geplant, die Filter-Bits bei der Ortung auszumaskieren. Das ist auf der RFD8500 nicht möglich: `TagLocationing` akzeptiert nur Masken, die **vom MSB an lückenlos 1** sind (z. B. `F×12 + 0×12`); eine Maske mit Lücke (`FF1F…`, `FF00…`) wird mit `Invalid EPC Mask` abgelehnt (Hardware-Spike 2026-09-30). Die echten Duplikate tragen Filterwert 1 (EPC `3034F8242800C10300000011`).

## 4) Reader-Anbindung

Erweiterung von `RfidReaderGateway` / `ZebraRfd8500Gateway`:

| Funktion | Zweck |
|---|---|
| `setTriggerMode(mode: TriggerMode)` mit `QCC`, `CAPTURE`, `LOCATE` | bestimmt die Wirkung des Pistolen-Triggers |
| `observeProximity(): Flow<Int>` | relative Nähe 0–100 während der Ortung |
| `startLocate(epc, mask)` / `stopLocate()` | Ortung per Zebra `Actions.TagLocationing` |
| `readTidsNearby(epc, mask): Result<List<String>>` | kurzer Lesevorgang mit 5 % der Maximalleistung, liefert die TIDs (TID-Bank, 6 Wörter = 96 Bit) der Tags mit passendem EPC |
| `readEpcsNearby(): Result<List<String>>` | kurzer Lesevorgang mit 5 % der Maximalleistung, liefert die EPCs aller gelesenen Tags (Anlesen in Phase 1) |

Die App übergibt als `mask` immer die volle Maske (`LocateTarget.FULL_MASK`, 24×`F`).

**Nahbereichs-Leistung (hardware-getestet 2026-09-30):** 5 % der Maximalleistung (Index 15 von 300) liest zuverlässig direkt an der Pistole und nichts in ca. 30 cm Abstand. Leistungsindex 0 war unzuverlässig. Die TID wird mit genau 6 Wörtern gelesen; `count = 0` lieferte die 96-Bit-TID mit angehängten Nullen.

Trigger-Verhalten je Modus:

- `QCC` (Standard): unverändertes Verhalten. Die Trigger-Weiterleitung passiert **im Gateway**: nur in diesem Modus werden der Single-Tag-Scan ausgeführt und `observeTriggerEvents()` ausgelöst. `ScanViewModel` bleibt unverändert.
- `CAPTURE`: Trigger-Druck liest sofort genau einen Tag (gleicher Ablauf wie Button „Read with reader“).
- `LOCATE`: Drücken startet, Loslassen stoppt die Ortung. Bei aktiver „Continuous search“ wird der Trigger ignoriert.

Der Duplikat-Bildschirm setzt den Modus beim Öffnen und setzt ihn **beim Verlassen und beim Wechsel in den Hintergrund zurück auf `QCC`**. Dabei wird eine laufende Ortung gestoppt. Nach Nahbereichs-Lesevorgängen stellt das Gateway die vom Anwender eingestellte Leistung wieder her.

## 5) Daten und Export

Room-Migration **6 → 7**, zwei neue Tabellen; bestehende Tabellen bleiben unverändert.

`duplicate_searches`

| Spalte | Typ | Hinweis |
|---|---|---|
| id | Long PK | autoGenerate |
| startedAt | Long | |
| endedAt | Long? | `null` = laufende Suche |
| sgtin | String | Kurzformat `gcp.itemRef.serial` |
| epc | String | 24 Hex-Zeichen, enthält den verwendeten Filterwert; gesucht wird exakt |
| store | String? | optional |
| exportFileName | String? | nach Export gesetzt |

`duplicate_finds`

| Spalte | Typ | Hinweis |
|---|---|---|
| id | Long PK | autoGenerate |
| searchId | Long | FK → `duplicate_searches.id` |
| foundAt | Long | |
| tid | String | **Unique Index** (global) |
| findNo | Int | fortlaufend pro Suche |

- **Fortsetzen:** Existiert beim Öffnen des Bildschirms eine Suche mit `endedAt = null`, startet der Bildschirm direkt in Phase 2 dieser Suche.
- **Filiale:** letzter Wert in `SharedPreferences`.
- **CSV:** Dateiname `duplicates_yyyyMMdd_HHmmss.csv`, Ablage im Documents-Verzeichnis (gleicher Mechanismus wie `MismatchRepository.exportPendingCsv`). Spaltenköpfe immer Englisch: `Timestamp,SGTIN,EPC,TID,Find No,Store`. Eine Zeile pro Fund der beendeten Suche. Bei 0 Funden wird keine Datei erzeugt, Meldung „No duplicates found – nothing exported“.

## 6) Rückmeldung (Ton und Vibration)

- Nähe 0 %: still.
- Nähe 1–100 %: kurzer Beep über `ToneGenerator`, Pause zwischen den Beeps sinkt linear von ca. 1000 ms auf ca. 80 ms. Kurze Vibration im selben Takt.
- Die Tacho-Animation „atmet“ im Beep-Takt.

## 7) Fehlerbehandlung

- Reader nicht verbunden: Hinweis, „Start search“ und „Read with reader“ deaktiviert.
- Verbindungsabbruch während der Suche: Ortung stoppt, Hinweis. Funde sind bereits gespeichert.
- Ungültige Eingabe oder Lesefehler: lesbare Meldung, kein Absturz.
- Das ViewModel liefert **typisierte Ergebnisse** (z. B. `ConfirmResult.AlreadyFound(findNo)`), die UI übersetzt per `stringResource`. Keine fest codierten Texte im ViewModel.

## 8) Komponenten

| Einheit | Verantwortung |
|---|---|
| `core/gs1/Gs1ParserService` | + SGTIN-Kurzformat → EPC-Hex mit Filterwert (Standard 1, wählbar 0–7); Erkennung EPC vs. SGTIN |
| `device/zebra/RfidReaderGateway`, `ZebraRfd8500Gateway` | Trigger-Modi inkl. Trigger-Weiterleitung, Ortung, TID-Lesen, Anlesen |
| `data/local` | Entities, DAO, `MIGRATION_6_7` |
| `data/repository/DuplicateSearchRepository` | Suchen und Funde speichern, offene Suche laden, CSV-Export |
| `feature/duplicates/DuplicateSearchViewModel` | Zustand beider Phasen, Bestätigungslogik, Modussteuerung |
| `feature/duplicates/ProximityFeedback` | Ton und Vibration in Abhängigkeit der Nähe |
| `feature/duplicates/DuplicateSearchScreen` | UI beider Phasen |
| `app/MainActivity`, `navigation/Nav.kt` | Drawer-Eintrag und Route |

## 9) Tests

- Unit: SGTIN → EPC mit Filterwert (Standard 1 und explizit 0–7, alle Partitionen, Rundlauf mit `parseEpcToSgtin`), Eingabeerkennung, ungültige Eingaben.
- Robolectric (Fake-Gateway, In-Memory-Room, wie `ScanViewModelTest`):
  - Bestätigung: 0 / 1 / mehrere / bereits gefunden (auch aus früherer Suche)
  - Trigger-Modus wird beim Verlassen auf `QCC` zurückgesetzt, Ortung gestoppt
  - Filterwert-Auswahl: Standard 1, Wechsel verwirft das Ziel
  - Fortsetzen einer offenen Suche
  - Export mit Funden (Inhalt) und ohne Funde (keine Datei)
- Migration 6 → 7.
- Manuelle Hardware-Checkliste (RFD8500 + CT37): Ortung mit gehaltenem Trigger, „Continuous search“, TID-Prüfung aller vier Fälle (inkl. zwei Duplikate gleichzeitig), Anlesen, getippte SGTIN mit Filterwert 1 findet den echten Tag, QCC-Trigger-Verhalten unverändert.

## 10) Risiken

- **R1 (hoch):** Verhalten von `TagLocationing` mit Maske und das Lesen der TID-Speicherbank auf der RFD8500 sind nicht verifiziert. **Erster Umsetzungsschritt ist ein Hardware-Spike** auf der echten Pistole, bevor UI und Datenbank darauf aufbauen.
  - **Spike-Ergebnis 2026-09-30:** Exakte Ortung (volle Maske) funktioniert (4–5 Werte/s, 38–99 je nach Abstand). Masken werden nur akzeptiert, wenn sie vom MSB an lückenlos 1 sind; die Filterbit-Maske (`FF1F…`) liefert `Invalid EPC Mask` → SGTIN-Eingabe mit Filterwert (Standard 1, wählbar 0–7) und exakter Suche. TID-Lesen funktioniert; mit `count = 0` kommen 12 angehängte Nullen → 6 Wörter (96 Bit) lesen. Anlesen mit Leistungsindex 0 unzuverlässig, mit 5 % der Maximalleistung zuverlässig, bei ca. 30 cm nichts gelesen. Trigger-Events kommen außerhalb von `QCC` zuverlässig an; QCC-Ablauf danach unverändert. Nicht getestet: zwei Duplikate gleichzeitig (nur ein Duplikat verfügbar) → in der Hardware-Abnahme nachholen. Echter Duplikat-EPC `3034F8242800C10300000011` (Filterwert 1, Partition 5).
- R2: Die Nahbereichs-Leistung (5 %) ist am Einzelfall kalibriert; bei anderen Tag-Typen oder Artikeln (z. B. Metall, Flüssigkeit) kann Nachjustieren nötig sein.
- R3: `ScanViewModel` ist Activity-weit gescoped und lauscht dauerhaft auf den Trigger; weil das Gateway `observeTriggerEvents()` nur im Modus `QCC` auslöst, entstehen keine Seiteneffekte.

## 11) Nicht im Scope

- Software-Stummschaltung gefundener Tags (Pre-Filter, Kill, Neucodierung)
- Zielwert / Fortschritt „n von 480“
- Online-Übertragung (EPCIS)
- Suche nach mehreren SGTINs gleichzeitig
- Übersetzung der bestehenden, fest codierten deutschen Meldungen im QCC-Ablauf
