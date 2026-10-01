# QCC – Applikationsdokumentation (Startseite)

| | |
|---|---|
| **Dokumentversion** | 2.0 (vollständige Neufassung, ersetzt die Rollen-Dokumente `docs/01`–`05` vom April 2026) |
| **Stand** | 2026-10-01 |
| **Code-Stand** | Branch `fix/duplicate-finder-ux`, Commit `88d0b55` · App-Version `0.1.0` (versionCode 1) · DB-Schema 8 |
| **Status** | Freigabe-Entwurf – offene, zu bestätigende Punkte stehen in [13 Roadmap, Risiken, offene Punkte](13-roadmap-risiken-offene-punkte.md) |
| **Sprache** | Deutsch (Code, Bezeichner und UI-Standardsprache: Englisch) |

## Worum es geht

**QCC** ist eine Android-App für das Handheld **Honeywell CT37** in Kombination mit dem Bluetooth-RFID-Reader **Zebra RFD8500**. Sie unterstützt zwei Anwendungsfälle:

1. **QCC-Vergleich (Kernfunktion):** Prüft per Pistolen-Trigger, ob der RFID-Tag eines Artikels (SGTIN-96) zum aufgedruckten 2D-Code (GS1 DataMatrix / QR) passt – Ergebnis `MATCH` oder `MISMATCH`, lokal protokolliert, als CSV exportierbar.
2. **Duplikatsuche (Find duplicates):** Führt den Anwender per Nähe-Anzeige, Ton und Vibration zu RFID-Tags mit **identischer SGTIN/EPC** und erfasst jeden Fund eindeutig über die **TID** des Chips; Abschluss mit CSV-Export.

## Seitenbaum (Confluence-Struktur)

```
QCC – Applikationsdokumentation              ← diese Seite (00)
├── 01 Hintergrund und Nutzen
├── 02 Produktübersicht und Funktionen
├── 03 Anforderungen
├── 04 Benutzerhandbuch
├── 05 Architektur
│   └── 12 Architekturentscheidungen (ADR)
├── 06 Datenmodell
├── 07 Schnittstellen, Hardware und GS1
├── 08 Entwicklerhandbuch
├── 09 Test und Qualität
├── 10 Betrieb, Deployment und Release
├── 11 Sicherheit und Datenschutz
├── 13 Roadmap, Risiken, offene Punkte
├── 14 Glossar
└── 15 Anhang (Änderungshistorie, Dokumentationspflege, Quellen)
```

## Lesepfade nach Zielgruppe

| Zielgruppe | Empfohlene Seiten |
|---|---|
| Management / Fachbereich | 01, 02, 13 |
| Anwender (Operator, Filiale) | 04, 14 |
| Business Analyse / Produktmanagement | 01, 02, 03, 09, 13 |
| Architektur | 05, 12, 06, 07, 11 |
| Entwicklung | 08, 05, 06, 07, 12 |
| Test / QS | 09, 03, 04 |
| Betrieb / Support | 10, 04, 11, 13 |

## Import-Hinweise für Confluence

- Jede `.md`-Datei ist **eine Confluence-Seite**. Die Reihenfolge der Nummern entspricht dem Seitenbaum. Import z. B. über *Seite erstellen → Inhalt einfügen → Markdown* oder einen Markdown-Importer. Alternativ erzeugt `build-combined.sh` eine Gesamtdatei.
- Interne Verweise sind relative Markdown-Links (`03-anforderungen.md`). Nach dem Import auf Confluence-Seitenlinks umstellen.
- **Diagramme** sind als ` ```mermaid `-Blöcke eingebettet. Confluence rendert Mermaid nur mit einem Marketplace-Makro (z. B. „Mermaid Diagrams“). Ohne Makro: Diagramme mit `mmdc` (mermaid-cli) als PNG/SVG rendern und als Bild einfügen; zu jedem Diagramm steht der Sachverhalt auch als Text/Tabelle da.
- Vorgeschlagene **Labels**: `qcc`, `android`, `rfid`, `gs1`, `architektur`, `anforderungen`, `betrieb`.
- Jede Seite beginnt mit einer Kopftabelle (Stand, Quellen). Beim Import als *Seiteneigenschaften* (Page Properties) übernehmen, wenn eine Übersichtsseite gewünscht ist.

## Zentrale Fakten auf einen Blick

| Thema | Wert |
|---|---|
| Paket / applicationId | `de.ca.qcc` |
| Anzeigename | „QCC“ (geplante Umbenennung in „MV-C“, siehe Seite 13) |
| Plattform | Android, minSdk 28, targetSdk/compileSdk 34, Zielgerät CT37 mit Android 14 |
| Sprache / Stack | Kotlin 2.0.21, Jetpack Compose (BOM 2024.06.00) + Material 3, MVVM, Hilt 2.51, Room 2.6.1, Coroutines/Flow |
| Hardware | Honeywell CT37 (Barcode, AIDC-SDK) + Zebra RFD8500 (RFID, Zebra RFID API3 SDK, Bluetooth) |
| Datenbank | Room/SQLite `qcc.db`, Schema **8**, 4 Tabellen |
| Sprachen der UI | Englisch (Standard) und Deutsch (Polnisch wurde am 2026-10-01 entfernt) |
| Netzwerk | keiner – die App ist offline (kein `INTERNET`-Permission) |
| Tests | 99 Unit-/Robolectric-Tests, keine instrumentierten Tests |
| Auslieferung | Release-Build baut (R8-Regeln für die Vendor-SDKs), **Signierung und Verteilungsweg offen**, siehe Seite 10 |
