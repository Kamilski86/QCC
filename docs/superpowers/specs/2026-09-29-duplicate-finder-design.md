# Design: Duplikatsuche („Find duplicates“)

- Datum: 2026-09-29
- Status: Entwurf zur Freigabe
- Scope: Neuer Use Case in der QCC-App (Honeywell CT37 + Zebra RFD8500)

## 1) Problem und Ziel

Ein Tag-Produzent hat dieselbe SGTIN / denselben EPC auf ca. 480 RFID-Tags codiert. Ein EPC muss eindeutig sein. Die betroffenen Artikel müssen in den Filialen einzeln gefunden, aus dem Verkauf genommen und für die Reklamation dokumentiert werden.

Ziel: Der Anwender gibt eine SGTIN oder einen EPC vor und wird per Nähe-Anzeige, Ton und Vibration mit der RFD8500 zu jedem Duplikat geführt. Jeder Fund wird über die eindeutige Chip-ID (TID) erfasst und am Ende als CSV exportiert.

## 2) Fachliche Entscheidungen

| # | Entscheidung |
|---|---|
| E1 | Gefundene Artikel werden **physisch entfernt** (z. B. Abschirmtasche). Die App „stummschaltet“ keine Tags. |
| E2 | Jeder Fund wird über die **TID** erfasst. Die TID ist global eindeutig; doppelte Bestätigung wird erkannt und nicht gezählt. |
| E3 | Eingabe des Ziels: **Anlesen mit der Pistole** (bevorzugt), **Hex-EPC** (24 Zeichen) oder **SGTIN** ohne URN-Präfix (`4065546.092768.12884901894`). **Kein QR-Scan**, weil QR- und Tag-Daten abweichen können. |
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
  - `Ziffern.Ziffern.Ziffern` → SGTIN
  - sonst → Fehlermeldung, kein Absturz
- Button „Read with reader“: Gateway wechselt in Modus `CAPTURE`; der nächste Trigger-Druck liest genau einen Tag mit minimaler Leistung und übernimmt dessen EPC ins Feld. Werden mehrere Tags gelesen, erscheint ein Hinweis, den Artikel zu isolieren.
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

Der Anwender nimmt den vermuteten Artikel heraus, hält ihn direkt an die Pistole und tippt „Confirm find“. Eine laufende Ortung pausiert, das Gateway liest mit minimaler Leistung die TIDs aller Tags mit passendem EPC:

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

Aus einer SGTIN ist der Filterwert (3 Bit) nicht ableitbar. Die App erzeugt den EPC mit Filter 0 und **maskiert die Filter-Bits** bei Ortung und TID-Prüfung. Bei eingegebenem oder angelesenem EPC wird exakt gesucht (volle Maske). Die Partition ergibt sich aus der Stellenzahl des Company Prefix (6–12 Stellen).

## 4) Reader-Anbindung

Erweiterung von `RfidReaderGateway` / `ZebraRfd8500Gateway`:

| Funktion | Zweck |
|---|---|
| `setTriggerMode(mode: TriggerMode)` mit `QCC`, `CAPTURE`, `LOCATE` | bestimmt die Wirkung des Pistolen-Triggers |
| `observeProximity(): Flow<Int>` | relative Nähe 0–100 während der Ortung |
| `startLocate(epc, mask)` / `stopLocate()` | Ortung per Zebra `Actions.TagLocationing` |
| `readTidsNearby(epc, mask): Result<List<String>>` | kurzer Lesevorgang mit minimaler Leistung, liefert TIDs der Tags mit passendem EPC |
| `captureSingleEpc(): Result<String>` | ein Tag mit minimaler Leistung lesen |

Trigger-Verhalten je Modus:

- `QCC` (Standard): unverändertes Verhalten (Single-Tag-Inventory + Trigger-Event). `ScanViewModel` reagiert **nur in diesem Modus** auf `observeTriggerEvents()`.
- `CAPTURE`: Trigger-Druck liest genau einen Tag.
- `LOCATE`: Drücken startet, Loslassen stoppt die Ortung. Bei aktiver „Continuous search“ wird der Trigger ignoriert.

Der Duplikat-Bildschirm setzt den Modus beim Öffnen und setzt ihn **beim Verlassen und beim Wechsel in den Hintergrund zurück auf `QCC`**. Dabei wird eine laufende Ortung gestoppt. Nach Lesevorgängen mit minimaler Leistung stellt das Gateway die vom Anwender eingestellte Leistung wieder her.

## 5) Daten und Export

Room-Migration **6 → 7**, zwei neue Tabellen; bestehende Tabellen bleiben unverändert.

`duplicate_searches`

| Spalte | Typ | Hinweis |
|---|---|---|
| id | Long PK | autoGenerate |
| startedAt | Long | |
| endedAt | Long? | `null` = laufende Suche |
| sgtin | String | Kurzformat `gcp.itemRef.serial` |
| epc | String | 24 Hex-Zeichen |
| filterMasked | Boolean | `true` bei SGTIN-Eingabe |
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
| `core/gs1/Gs1ParserService` | + SGTIN-Kurzformat → EPC-Hex (Filter 0) und Maske; Erkennung EPC vs. SGTIN |
| `device/zebra/RfidReaderGateway`, `ZebraRfd8500Gateway` | Trigger-Modi, Ortung, TID-Lesen, Einzel-Tag-Anlesen |
| `data/local` | Entities, DAO, `MIGRATION_6_7` |
| `data/repository/DuplicateSearchRepository` | Suchen und Funde speichern, offene Suche laden, CSV-Export |
| `feature/duplicates/DuplicateSearchViewModel` | Zustand beider Phasen, Bestätigungslogik, Modussteuerung |
| `feature/duplicates/ProximityFeedback` | Ton und Vibration in Abhängigkeit der Nähe |
| `feature/duplicates/DuplicateSearchScreen` | UI beider Phasen |
| `app/MainActivity`, `navigation/Nav.kt` | Drawer-Eintrag und Route |
| `feature/scan/ScanViewModel` | Trigger nur im Modus `QCC` auswerten |

## 9) Tests

- Unit: SGTIN → EPC + Maske (alle Partitionen, Rundlauf mit `parseEpcToSgtin`), Eingabeerkennung, ungültige Eingaben.
- Robolectric (Fake-Gateway, In-Memory-Room, wie `ScanViewModelTest`):
  - Bestätigung: 0 / 1 / mehrere / bereits gefunden (auch aus früherer Suche)
  - Trigger-Modus wird beim Verlassen auf `QCC` zurückgesetzt, Ortung gestoppt
  - `ScanViewModel` ignoriert Trigger außerhalb von `QCC`
  - Fortsetzen einer offenen Suche
  - Export mit Funden (Inhalt) und ohne Funde (keine Datei)
- Migration 6 → 7.
- Manuelle Hardware-Checkliste (RFD8500 + CT37): Ortung mit gehaltenem Trigger, „Continuous search“, TID-Prüfung aller vier Fälle, Anlesen, QCC-Ablauf unverändert.

## 10) Risiken

- **R1 (hoch):** Verhalten von `TagLocationing` mit Maske und das Lesen der TID-Speicherbank auf der RFD8500 sind nicht verifiziert. **Erster Umsetzungsschritt ist ein Hardware-Spike** auf der echten Pistole, bevor UI und Datenbank darauf aufbauen.
- R2: Die minimale Leistung für die TID-Prüfung muss in der Praxis kalibriert werden (Reichweite wenige Zentimeter).
- R3: `ScanViewModel` ist Activity-weit gescoped und lauscht dauerhaft auf den Trigger; die Modus-Prüfung verhindert Seiteneffekte.

## 11) Nicht im Scope

- Software-Stummschaltung gefundener Tags (Pre-Filter, Kill, Neucodierung)
- Zielwert / Fortschritt „n von 480“
- Online-Übertragung (EPCIS)
- Suche nach mehreren SGTINs gleichzeitig
- Übersetzung der bestehenden, fest codierten deutschen Meldungen im QCC-Ablauf
