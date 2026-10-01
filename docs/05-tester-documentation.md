# Tester Sicht - Testdokumentation

> **Archiv (Stand April 2026).** Dieses Dokument wird nicht mehr gepflegt und ist teilweise überholt (z. B. Schema v6, Scan-Buttons im Dashboard, Sendeleistungs-Einstellung). Maßgeblich ist die aktuelle Dokumentation unter [`docs/confluence/`](confluence/00-index.md).

## Dokumentsteuerung

- Version: 1.1
- Testbasis: Produktstand April 2026
- Quellen: `docs/03-business-analyst-requirements.md`, `AGENTS.md`, `CLAUDE.md`

## 1) Testziele

- Verifikation der Kernfunktion: korrekter QR/RFID-Vergleich
- Validierung der Bedienrobustheit (Reihenfolgefreiheit, Zyklus-Clearing)
- Sicherstellung der Datenintegritaet (Scans, Mismatches, Export)
- Absicherung der Betriebsstabilitaet auf Zielhardware

## 2) Testumfang (In Scope)

- Parser-Verhalten fuer gueltige/ungueltige QR- und EPC-Daten
- Vergleichslogik in `CompareScansUseCase`
- State-Verhalten im `ScanViewModel`
- Reader-Zustandswechsel und Reconnect-Szenarien
- Persistenz und CSV-Export

Nicht im Scope:
- Vollstaendige instrumentierte UI-Automation auf Device-Farm
- Netzwerk-/EPCIS Integrationsszenarien

## 3) Teststrategie (Pyramide)

- Unit (hoch priorisiert): Parser, UseCase, ViewModel-Logik
- Integration (mittel): Repository + Room + Export
- Manuelle Systemtests (kritisch): CT37 + RFD8500 End-to-End

## 4) Entry- und Exit-Kriterien

### Entry-Kriterien
- Implementierung abgeschlossen und buildbar
- Anforderungen und AC in `docs/03-business-analyst-requirements.md` aktuell
- Testdaten und Hardware verfuegbar

### Exit-Kriterien
- Keine offenen Critical/Blocker Defects
- Alle P1 Regressionstests erfolgreich
- Dokumentierte Restrisiken akzeptiert

## 5) Priorisierte Testobjekte

### P1 Kritisch
- `Gs1ParserService.parseQr()`
- `Gs1ParserService.parseEpcToSgtin()`
- `CompareScansUseCase.invoke()`
- `ScanViewModel.compareIfReady()`
- `ScanViewModel.startNewCycleIfNeeded()`

### P2 Hoch
- `MismatchRepository.addScan()` / `add()`
- `MismatchRepository.exportPendingCsv()`
- `ZebraRfd8500Gateway` Trigger/Connect-Reconnect

### P3 Mittel
- Navigation und Einstellungsseiten

## 6) Regression Suite (funktional)

### RS-01 Reihenfolgefreiheit
1. QR zuerst, dann RFID -> Ergebnis
2. RFID zuerst, dann QR -> Ergebnis

### RS-02 Zyklus-Clearing
1. Zyklus abschliessen
2. Neuen Scan starten
3. Verifizieren, dass Vorwerte vor neuem Ergebnis entfernt sind

### RS-03 Fehlerpfade Parser
1. QR ohne erwartetes Muster -> Fehler sichtbar
2. EPC mit falscher Laenge/Header -> Fehler sichtbar

### RS-04 Persistenz
1. MATCH -> Eintrag in `scans`, kein Eintrag in `mismatches`
2. MISMATCH -> Eintrag in beiden Tabellen

### RS-05 Export
1. Export starten
2. Datei erstellt
3. Header und Datenspalten korrekt

## 7) Nicht-funktionale Tests

- NFT-01 Stabilitaet bei 100+ aufeinanderfolgenden Zyklen
- NFT-02 Reaktionszeit von Scanereignis bis Ergebnisfeedback
- NFT-03 Recovery nach Bluetooth Disconnect waehrend Betrieb
- NFT-04 Konsistenz von Tageszaehlern ueber laengere Laufzeit

## 8) Hardware- und Umfeldmatrix

- Referenz: Honeywell CT37 + Zebra RFD8500
- Android-Versionen: Mindestens ein Geraet mit Android 12+
- Zustandsfaelle:
  - Reader nicht verbunden
  - Reader verbunden
  - Reader waehrend Scan getrennt
  - Reconnect nach Verbindungsabbruch

## 9) Testdatenstrategie

- Gueltige GS1 QR-Beispiele (`/01/.../21/...`)
- Manuelle GTIN Positiv/Negativfaelle
- EPC SGTIN-96 gueltig/ungueltig (Laenge, Header, Zeichen)
- Datensaetze fuer MATCH und MISMATCH gezielt vorbereiten

## 10) Defect Management

### Schweregrade
- Blocker: Kernprozess nicht nutzbar
- Critical: Falsche Vergleichsentscheidung oder Datenverlust
- Major: Wichtige Funktion beeintraechtigt, Workaround moeglich
- Minor: Geringe Auswirkung, kosmetisch oder Randfall

### Bug-Report Mindestinhalt
- Titel, Build, Geraet, Android-Version
- Repro-Schritte (nummeriert)
- Erwartet vs. Ist
- Severity/Prioritaet
- Evidenz (Screenshots, Logcat, Exportdatei falls relevant)

## 11) Release-Gates (QA)

Releasefreigabe nur wenn:
- P1 Tests vollstaendig gruen
- Keine offenen Blocker/Critical Defects
- Export und Persistenz verifiziert
- Hardware-Smoke-Test auf Referenzsetup bestanden

## 12) Traceability zu BA-Anforderungen

- FR-01/FR-03/FR-04 -> RS-01, RS-03, RS-04
- FR-05 -> RS-01
- FR-06 -> RS-02
- FR-08 -> RS-05
- NFR-04/NFR-06 -> RS-03, RS-04, NFT-01

## 13) Testdurchfuehrung (Basisbefehle)

```bash
./gradlew test
./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.SomeTestClass"
./gradlew lint
```

## 14) Empfohlene QA-Verbesserungen

- Automatisierte Unit-Tests explizit fuer Zyklus-Clearing erweitern
- Kleines standardisiertes Hardware-Smoke-Protokoll je Release
- Defect-Leakage Tracking zwischen Releases einführen
- Mittelfristig instrumentierte Kernpfade fuer Navigation/State pruefen
