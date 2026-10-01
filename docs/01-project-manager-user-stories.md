# Project Manager Sicht - User Stories

> **Archiv (Stand April 2026).** Dieses Dokument wird nicht mehr gepflegt und ist teilweise überholt (z. B. Schema v6, Scan-Buttons im Dashboard, Sendeleistungs-Einstellung). Maßgeblich ist die aktuelle Dokumentation unter [`docs/confluence/`](confluence/00-index.md).

## Dokumentsteuerung

- Version: 1.1
- Status: Working Baseline (rueckwirkend)
- Zeithorizont: Produktstand bis April 2026
- Quellen: `AGENTS.md`, `CLAUDE.md`, `docs/03-business-analyst-requirements.md`

## 1) Produktauftrag und Vision

QCC reduziert Fehlzuordnungen zwischen physischem RFID-Tag und gedruckter QR-Kennzeichnung.
Das Produkt soll am Shopfloor in Sekunden eine verlaessliche Ja/Nein-Entscheidung liefern und gleichzeitig alle Entscheidungen nachvollziehbar dokumentieren.

Leitprinzipien:
- Geschwindigkeit vor Komplexitaet
- Eindeutige Bedienung auch unter Produktionsdruck
- Revisionsfaehige Nachvollziehbarkeit durch Datenpersistenz

## 2) Zielgruppen und Personas

### Persona P1 - Operator (primaer)
- Kontext: Taktgebundene Pruefung im laufenden Betrieb
- Erfolgskriterium: Kein Medienbruch, sofortiges Ergebnis, klare Fehlermeldung
- Hauptrisiko: Verwechslung alter und neuer Scanwerte

### Persona P2 - Schichtleiter (sekundaer)
- Kontext: Tagessteuerung und Qualitaetsueberwachung
- Erfolgskriterium: Metriken zu Scans/Mismatches pro Tag

### Persona P3 - Prozessverantwortlicher (sekundaer)
- Kontext: Ursachenanalyse und KVP
- Erfolgskriterium: Exportierbare Daten fuer externe Auswertung

## 3) Business Outcomes und KPI-Ziele

### Outcome O1 - Pruefprozess beschleunigen
- KPI-1: Median Zeit pro vollstaendigem Scanzyklus
- KPI-2: Anteil Zyklen ohne manuellen Reset

### Outcome O2 - Fehlzuordnungen frueh erkennen
- KPI-3: Mismatch Rate pro Schicht
- KPI-4: First Pass Match Rate

### Outcome O3 - Auditierbarkeit sicherstellen
- KPI-5: Exportquote je Woche
- KPI-6: Vollstaendigkeit persistierter Scanzyklen

## 4) Produktumfang (As-Is)

Im aktuellen Umfang:
- QR Scan, RFID Scan, Vergleich, Ergebnisfeedback
- Reihenfolgefreiheit (QR zuerst oder RFID zuerst)
- Zyklus-Clearing vor neuem abgeschlossenen Scan
- Tageszaehler und CSV Export

Nicht im Umfang:
- SSCC Parsing
- Multi-Tag Inventory
- EPCIS 2.0 Integration

## 5) Priorisierte Epics

### Epic A - Verlaesslicher Scanvergleich
Wertbeitrag: Kernnutzen fuer Operator, direkte Qualitaetsentscheidung.

### Epic B - Transparenz und Reporting
Wertbeitrag: Teamsteuerung, Ursachenanalyse, kontinuierliche Verbesserung.

### Epic C - Betriebsfaehigkeit auf Zielhardware
Wertbeitrag: Stabile Nutzung mit CT37 + RFD8500 unter Realbedingungen.

## 6) Detaillierte User Stories

### US-A1 (Must) - Vergleich nach vollstaendigem Datensatz
Als Operator moechte ich QR- und RFID-Daten scannen, damit ich sofort erkenne, ob Artikel und Tag zusammenpassen.

Akzeptanz:
- Sobald `qr` und `rfid` vorliegen, startet der Vergleich automatisch.
- Ergebnis ist eindeutig `MATCH` oder `MISMATCH`.
- Ergebnis wird visuell und per Signal rueckgemeldet.

Messbarkeit:
- >= 99% abgeschlossene Zyklen mit eindeutigem Ergebnisstatus.

### US-A2 (Must) - Reihenfolgefreiheit
Als Operator moechte ich ohne feste Reihenfolge scannen, damit der Prozess zur realen Arbeitslage passt.

Akzeptanz:
- QR zuerst -> Ergebnis nach RFID.
- RFID zuerst -> Ergebnis nach QR.
- Kein Blocker-Text wegen Reihenfolge.

Messbarkeit:
- Keine Reihenfolge-bedingten Supporttickets.

### US-A3 (Should) - Klare Trennung von Scanzyklen
Als Operator moechte ich bei neuem Zyklus keine Altwerte sehen, damit ich keine Mischinterpretation vornehme.

Akzeptanz:
- Vor dem naechsten abgeschlossenen Zyklus werden alte QR/RFID/Ergebniswerte bereinigt.
- Unvollstaendige laufende Zyklen bleiben erhalten.

Messbarkeit:
- Reduktion von Bedienfehlern im Bereich "falsches Referenzergebnis".

### US-B1 (Must) - Tagesmetriken
Als Schichtleiter moechte ich sehen, wie viele Scans heute erfolgt sind, damit ich Prozessleistung bewerten kann.

Akzeptanz:
- Anzeige "Scans heute" wird kontinuierlich aktualisiert.
- Anzeige "Mismatches heute" ist verfuegbar.

### US-B2 (Should) - CSV Export
Als Prozessverantwortlicher moechte ich Scandaten exportieren, damit ich externe Analysen erstellen kann.

Akzeptanz:
- Exportdatei wird mit Zeitstempel erstellt.
- Export enthaelt die definierten Spalten fuer Nachvollziehbarkeit.

### US-C1 (Must) - Reader Betriebsfaehigkeit
Als Operator moechte ich Reader-Verbindungsstatus und Leistung einstellen, damit ich stabil scannen kann.

Akzeptanz:
- Reader kann gefunden, verbunden und bei Bedarf neu verbunden werden.
- Sendeleistung kann angepasst werden.

## 7) Priorisierungsmodell (MoSCoW)

- Must: US-A1, US-A2, US-B1, US-C1
- Should: US-A3, US-B2
- Could: Erweiterte Dashboards, Trendkurven, Schichtvergleich
- Won't (aktuell): Inbound-Read mit SSCC/EPCIS

## 8) Release-Sicht (rueckwirkend + naechste Wellen)

### Release-Welle R1 (bestehend)
- Kernvergleich, Persistenz, Reader-Handling, Grundnavigation

### Release-Welle R2 (bestehend/aktualisiert)
- Reihenfolgefreiheit stabilisiert
- Zyklus-Clearing fuer bessere Bedienklarheit

### Release-Welle R3 (geplant)
- App-Rename QCC -> MV-C
- Home mit modularen Tiles
- ViewModel-Scope auf Feature-Ebene

### Release-Welle R4 (geplant)
- Inbound Read
- SSCC + Multi-Tag + EPCIS 2.0

## 9) Risiken aus PM-Sicht

- R-01 Hardwareabhaengigkeit begrenzt Testautomatisierung.
- R-02 Lokale Vendor AARs erschweren Build-Reproduzierbarkeit.
- R-03 Prozessaenderungen ohne Training koennen KPI-Effekte verfaelschen.

## 10) Governance und Entscheidungsmodell

- Product Owner / PM: Scope, Priorisierung, KPI-Ziele
- Architect: Zielarchitektur, technische Leitplanken
- BA: Anforderungen, Traceability, AC-Pflege
- Dev: Implementierung gemaess AC
- Test: Freigabeempfehlung auf Basis Release-Gates

## 11) Definition of Done (Produktsicht)

Ein Feature gilt als PM-seitig abgeschlossen, wenn:
- User Story und AC fachlich erfuellt sind
- KPI-Zielbeitrag benannt ist
- Risiken dokumentiert sind
- Auswirkungen auf Schulung/Kommunikation geklaert sind
- Relevante Doku in `docs/` aktualisiert wurde
