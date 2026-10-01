# Business Analyst Sicht - Requirements

> **Archiv (Stand April 2026).** Dieses Dokument wird nicht mehr gepflegt und ist teilweise überholt (z. B. Schema v6, Scan-Buttons im Dashboard, Sendeleistungs-Einstellung). Maßgeblich ist die aktuelle Dokumentation unter [`docs/confluence/`](confluence/00-index.md).

## Dokumentsteuerung

- Version: 1.1 (Rueckwirkende Konsolidierung)
- Geltungsbereich: Aktueller QCC-Use-Case im Projektstand April 2026
- Quellen: `AGENTS.md`, `CLAUDE.md`, `app/src/main/java/de/ca/qcc/`

## 1) Zielbild und Scope (As-Is)

QCC ist eine Android-Anwendung zur Identitaetspruefung einzelner Artikel.
Pro Pruefzyklus werden QR-Daten (GS1 DataMatrix oder manuelle GTIN) und RFID-Daten (EPC SGTIN-96) zusammengefuehrt und verglichen.

Ergebnis des Zyklus:
- `MATCH`: QR-SGTIN entspricht RFID-SGTIN
- `MISMATCH`: Abweichung oder fehlende/ungueltige Vergleichsbasis

Im Scope:
- Scannen und Parsen von QR
- Scannen und Parsen von RFID
- Vergleichsentscheidung
- Persistenz von Scans und Mismatches
- CSV Export der Scanhistorie

Nicht im Scope:
- SSCC Parsing (AI 00)
- Multi-Tag Inventory
- EPCIS 2.0 Online-Kommunikation

## 2) Stakeholder und Verantwortung

- Operator (Shopfloor): Fuehrt Scanzyklen aus, bewertet Rueckmeldung
- Teamleitung Qualitaet: Ueberwacht Mismatch-Rate und Tagesleistung
- IT/Engineering: Betreibt App, behebt Fehler, entwickelt Features
- Prozessmanagement: Definiert Zielwerte und Qualitaetskriterien

## 3) Glossar

- GTIN: Global Trade Item Number (14-stellig)
- EPC: Electronic Product Code (hier SGTIN-96 als 24 Hex-Zeichen)
- SGTIN: Serialised GTIN (Pure Identity)
- Zyklus: Logischer Vergleichsvorgang aus QR + RFID + Ergebnis
- Matchrate: Anteil `MATCH` an allen abgeschlossenen Zyklen

## 4) Annahmen und Randbedingungen

- Hardware-Zielumgebung: Honeywell CT37 + Zebra RFD8500
- Zebra ist im Ist-Zustand auf Single-Tag-Session ausgelegt
- App wird primar offline genutzt; Persistenz ist lokal in Room
- ViewModels sind derzeit auf NavHost-Ebene in `MainActivity` gescoped

## 5) Funktionale Anforderungen (FR)

### FR-01 QR Parsing (Must)
Zweck: QR-Daten fuer Vergleich aufbereiten.

- Trigger: Eingang eines Barcode-Events
- Eingabe: Rohstring mit GS1-Muster `/01/{GTIN14}/21/{SERIAL}`
- Regeln:
  - GTIN muss 14-stellig numerisch sein
  - Seriennummer darf nicht leer sein
  - Aus GTIN + Serial wird QR-SGTIN aufgebaut
- Fehlerfall:
  - Bei ungueltiger Struktur ist eine nutzerverstaendliche Fehlermeldung zu setzen
- Ergebnis:
  - `QrScanResult(rawValue, gtin, serial, sgtin)`

Trace:
- `Gs1ParserService.parseQr()`
- `ScanViewModel.handleQr()`

### FR-02 Manuelle GTIN (Must)
Zweck: Fallback bei nicht verfuegbarer QR-Seriennummer.

- Trigger: Manueller Submit im UI
- Eingabe: 14-stellige GTIN
- Regeln:
  - Nur exakt 14 Ziffern zulassen
  - Serial bleibt `null`
- Fehlerfall:
  - Ungueltige Eingabe erzeugt klare Fehlermeldung

Trace:
- `Gs1ParserService.parseManualQrGtin()`
- `ScanViewModel.setManualQrGtin()`

### FR-03 RFID Parsing (Must)
Zweck: EPC in fachliche Vergleichsstruktur ueberfuehren.

- Trigger: Eingang eines EPC-Reads
- Eingabe: Hex-String
- Regeln:
  - Nur Hex-Zeichen erlaubt
  - Gerade Anzahl Zeichen
  - Exakt 24 Zeichen (SGTIN-96)
  - Header muss SGTIN-96 sein
  - Partition 0..6
  - GTIN/Serial muessen inhaltlich valide sein
- Ergebnis:
  - `RfidScanResult(epc, sgtin, gtin, readerMeta)`

Trace:
- `Gs1ParserService.parseEpcToSgtin()`
- `ScanViewModel.handleRfid()`

### FR-04 Vergleichslogik (Must)
Zweck: Eindeutige Match/Mismatch-Entscheidung.

- Vorbedingung: `qr` und `rfid` liegen vor
- Regeln:
  - Vergleich auf normalisierten SGTIN-Werten (trim + lowercase)
  - QR ohne Seriennummer fuehrt zu Mismatch
  - Fehlender QR-SGTIN-Aufbau fuehrt zu Mismatch
- Nebenwirkung:
  - Jeder Vergleich wird in `scans` persistiert
  - Bei Mismatch zusaetzlich Eintrag in `mismatches`

Trace:
- `CompareScansUseCase.invoke()`
- `MismatchRepository.addScan()`
- `MismatchRepository.add()`

### FR-05 Reihenfolgefreiheit (Must)
Zweck: Bedienfluss an reale Shopfloor-Situation anpassen.

- Regel:
  - QR zuerst oder RFID zuerst muss gleichwertig funktionieren
  - Vergleich startet automatisch bei Vollstaendigkeit beider Werte

Trace:
- `ScanViewModel.compareIfReady()`

### FR-06 Neuer Scanzyklus / Clearing (Should)
Zweck: Verwechslung alter und neuer Werte verhindern.

- Regel:
  - Vor Start eines neuen abgeschlossenen Zyklus werden `qr`, `rfid`, altes Ergebnis und Fehlerstatus geleert
  - Laufender, unvollstaendiger Zyklus wird nicht ungefragt verworfen

Trace:
- `ScanViewModel.startNewCycleIfNeeded()`
- `ScanViewModel.triggerQrScan()`
- `ScanViewModel.triggerRfidScan()`

### FR-07 Tagesmetriken (Should)
Zweck: Operative Transparenz pro Tag.

- Ausgabe:
  - Anzahl aller heutigen Scans
  - Anzahl heutiger Mismatches

Trace:
- `MismatchRepository.observeTodayScanCount()`
- `MismatchRepository.observeTodayCount()`

### FR-08 CSV Export (Should)
Zweck: Externe Auswertung/Reporting.

- Inhalt:
  - Timestamp, SGTIN, SGTIN Tag, QR Raw
- Ausgabeort:
  - Android Documents-Verzeichnis

Trace:
- `MismatchRepository.exportPendingCsv()`

### FR-09 Reader Konfiguration (Should)
Zweck: Anpassung der Leseleistung und transparente Reader-States.

- Funktionen:
  - Reader Discovery
  - Connect/Reconnect
  - Power-Level setzen
  - Statusmeldung bereitstellen

Trace:
- `ZebraRfd8500Gateway.discoverReaders()`
- `ZebraRfd8500Gateway.connect()`
- `ZebraRfd8500Gateway.setPower()`
- `RfidReaderGateway.observeStatus()`

## 6) Nicht-funktionale Anforderungen (NFR)

### NFR-01 Plattformkompatibilitaet
- Muss auf Android Min SDK 28 laufen, Zielplattform SDK 34.
- Nachweis: Build-Konfiguration + Installationscheck auf Referenzgeraet.

### NFR-02 Architekturkonsistenz
- Muss MVVM, Hilt, Room, Navigation Compose folgen.
- Nachweis: Strukturreview der Packages/Klassen.

### NFR-03 Reaktionszeit (Usability)
- Soll akustisches/haptisches Feedback unmittelbar nach Scanereignis ausgeben.
- Nachweis: Manueller Referenztest auf CT37/RFD8500.

### NFR-04 Fehlertoleranz
- Muss Parse-/Verbindungsfehler als benutzerlesbare Meldung bereitstellen.
- Nachweis: Negativtests mit ungueltigen QR/EPC Eingaben und Reader-Disconnect.

### NFR-05 Lokalisierung
- Muss User-visible Strings in `values`, `values-de`, `values-pl` pflegen.
- Nachweis: Resource-Review.

### NFR-06 Persistenzstabilitaet
- Muss Vergleichsdaten robust lokal speichern (Room Schema v6 + Migrationen).
- Nachweis: DB-Integritaetstest nach mehreren Zyklen.

## 7) Business Rules

- BR-01 Vergleich erfolgt nur bei Vorliegen von `qr` und `rfid`.
- BR-02 QR ohne Seriennummer ist nicht matchbar und fuehrt zu Mismatch.
- BR-03 EPC muss SGTIN-96-konform sein.
- BR-04 Neuer abgeschlossener Zyklus startet mit bereinigten Vorwerten.
- BR-05 Jeder Vergleich erzeugt genau einen Scan-Datensatz.

## 8) Use Case Spezifikation (Hauptfall)

### UC-01 Artikelidentitaet pruefen

Akteure:
- Primaer: Operator
- Sekundaer: Zebra Reader, Honeywell Scanner

Vorbedingungen:
- App geoeffnet, Dashboard sichtbar
- Reader verbunden oder verbindbar

Normalfluss:
1. Operator startet Scan (QR oder RFID).
2. System verarbeitet eingehenden Wert und aktualisiert UI-Status.
3. Zweiter Wert wird erfasst (umgekehrte Reihenfolge zulaessig).
4. System fuehrt Vergleich aus.
5. System zeigt Ergebnis (MATCH/MISMATCH) mit Signal.
6. System persistiert Ergebnisdaten.

Alternativfluss A1 (manuelle GTIN):
- Operator erfasst GTIN manuell, RFID folgt danach.

Alternativfluss A2 (neuer Zyklus):
- Bei erneutem Start nach abgeschlossenem Vergleich werden Vorwerte geloescht.

Ausnahmefluss E1 (ungueltiger QR):
- System setzt Fehlermeldung, Zyklus bleibt offen.

Ausnahmefluss E2 (ungueltiger EPC):
- System setzt Fehlermeldung, Zyklus bleibt offen.

## 9) Akzeptanzkriterien (Gherkin)

### AC-01 Reihenfolgefreiheit
Given Dashboard ist aktiv
When zuerst RFID und danach QR gescannt wird
Then wird ein Vergleichsergebnis angezeigt
And es erfolgt kein Reihenfolge-Blocker

### AC-02 Zyklus-Clearing
Given ein abgeschlossener Zyklus mit sichtbaren Werten
When der naechste Scan gestartet wird
Then werden alte QR-, RFID- und Ergebniswerte vor dem neuen Zyklus entfernt

### AC-03 Persistenz Match
Given ein Vergleich mit Ergebnis MATCH
When der Vergleich abgeschlossen wird
Then wird genau ein Datensatz in `scans` gespeichert
And es wird kein neuer Datensatz in `mismatches` erzeugt

### AC-04 Persistenz Mismatch
Given ein Vergleich mit Ergebnis MISMATCH
When der Vergleich abgeschlossen wird
Then wird genau ein Datensatz in `scans` gespeichert
And es wird genau ein Datensatz in `mismatches` erzeugt

### AC-05 Fehlerbehandlung QR
Given ein ungueltiger QR-Inhalt
When das System parseQr ausfuehrt
Then wird eine lesbare Fehlermeldung im UI gesetzt

## 10) Traceability Matrix (kompakt)

| Business-Ziel | Requirement | Akzeptanzkriterium | Testfokus | Code-Trace |
|---|---|---|---|---|
| Schnelle Pruefung am Arbeitsplatz | FR-01, FR-03, FR-04 | AC-01 | E2E Scanfluss | `ScanViewModel`, `Gs1ParserService`, `CompareScansUseCase` |
| Fehlervermeidung bei Folgezyklen | FR-06 | AC-02 | Regression Zyklusstart | `startNewCycleIfNeeded()` |
| Nachvollziehbarkeit | FR-07, FR-08 | AC-03, AC-04 | Repository/Export Tests | `MismatchRepository` |
| Prozessstabilitaet | NFR-04, NFR-06 | AC-05 | Negativ- und Persistenztests | Parser + Room Layer |

## 11) Risiken und offene Punkte

### Risiken
- R-01 Hardware-Abhaengigkeit erschwert automatisierte End-to-End Tests.
- R-02 Lokale AAR Vendor SDKs koennen CI und Reproduzierbarkeit beeintraechtigen.
- R-03 NavHost-weite ViewModel-Scopes koennen bei Featurewachstum Seiteneffekte erzeugen.

### Offene Punkte
- O-01 Entscheidung zu SSCC-Support (AI 00) fuer Inbound-Use-Case.
- O-02 Entscheidung zu Multi-Tag-Modus fuer Zebra Gateway.
- O-03 Entscheidung ueber dedizierte QA-Gates auf echter Hardware pro Release.

## 12) Out of Scope (derzeit)

- EPCIS 2.0 API und Netzwerkworkflow
- Mehrgeraete-Synchronisierung
- Benutzer-/Rollenverwaltung

