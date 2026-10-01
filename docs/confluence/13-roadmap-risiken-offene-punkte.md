# 13 Roadmap, Risiken, offene Punkte

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Quellen** | `CLAUDE.md` („Planned Changes“), bisherige PM-/BA-Dokumente, Befunde der Dokumentationsprüfung (2026-10-01) |

## 1 Roadmap

### Geplant (aus `CLAUDE.md`, fachlich noch nicht freigegeben)

| # | Vorhaben | Inhalt / Abhängigkeiten |
|---|---|---|
| RM-1 | **App-Umbenennung „QCC“ → „MV-C“** | Anzeigename; im Code stehen noch Literale „QCC“ in `DashboardScreen` (Titel) und `AboutScreen`; `app_name` in `strings.xml` ersetzen; Launcher-Beschriftung, Doku, Paketname/DB-Dateiname bleiben (Entscheidung offen) |
| RM-2 | **Home-Screen mit Use-Case-Kacheln** | ersetzt `Splash → Dashboard`; QCC wird eine Kachel; **ViewModels auf Destination-Scope** (ADR-04), `ScanViewModel`-Pausierung entfällt dann |
| RM-3 | **Inbound Read** | SSCC-Parsing (AI `00`, 18 Ziffern), **Mehr-Tag-Inventur** im Zebra-Gateway, **EPCIS-2.0-`AggregationEvent`**, HTTP-POST an einen EPCIS-Endpunkt (Retrofit + OkHttp, `INTERNET`-Permission, TLS/Authentifizierung/Retry) |

### Releasewellen (aus der früheren PM-Sicht, aktualisiert)

| Welle | Inhalt | Status |
|---|---|---|
| R1 | Kernvergleich, Persistenz, Reader-Handling, Grundnavigation | umgesetzt |
| R2 | Reihenfolgefreiheit, Zyklus-Clearing | umgesetzt |
| **R2.5** | **Duplicate Finder**, Wiederfunde, Isolation von QCC, Dashboard-Anpassung | umgesetzt (Branch, Merge nach `main` offen) |
| R3 | Umbenennung MV-C, Home mit Kacheln, ViewModel-Scope pro Destination | geplant |
| R4 | Inbound Read (SSCC, Mehr-Tag, EPCIS 2.0) | geplant |

### Vorschläge aus der Dokumentationsprüfung (nicht beauftragt)

| # | Vorschlag | Nutzen |
|---|---|---|
| V-1 | ~~R8-Regeln, Release-Build, Signierung~~ **erledigt (2026-10-01)**; offen: Funktionstest des signierten Release-Builds mit Reader, Scan und Export | Auslieferbarkeit |
| V-2 | ~~Bedienelement oder Anzeige der Sendeleistung~~ **entschieden (2026-10-01): Anzeige bleibt, Label korrigiert („Level n of 30“ + Hinweistext), kein Bedienelement** | erledigt (FR-09) |
| V-3 | ~~Entscheidung zur manuellen GTIN~~ **entschieden: entfernt (2026-10-01)** | FR-02 |
| V-4 | ~~Polnisch in die Sprachauswahl~~ **Polnisch entfernt (2026-10-01)**; offen: QCC-Klartexte lokalisieren | NFR-05 |
| V-5 | Button „Export Mismatches (CSV)“ umbenennen oder nur Mismatches exportieren | Konsistenz (FR-08) |
| V-6 | Version aus `BuildConfig` im About-Screen; Git-Tags | Release-Hygiene |
| V-7 | ~~Unbenutzte Permissions/Strings/Routen entfernen~~ **erledigt (2026-10-01)** | Aufräumen (Seite 11) |
| V-8 | Backup-/Aufbewahrungskonzept | Datenschutz |
| V-9 | CI mit Unit-Tests (ohne Hardware) | Qualität |
| V-11 | Berechtigungen aus den SDKs (Standort, Speicher) per `tools:node="remove"` entfernen und testen | Angriffsfläche (Seite 11) |
| V-10 | Test mit zwei gleichzeitigen Duplikaten (H-04) | Absicherung FR-16 |

## 2 Technische Schulden

| ID | Schuld | Ort |
|---|---|---|
| TD-01 | Deutsche Klartexte in QCC-ViewModel (`Bereit`, `Starte neuen Scan`, `QR gelesen`, …), UseCase (`SGTIN stimmt überein`, …), Gateway-Status (`Nicht verbunden`, `Verbunden`, …) | `ScanViewModel`, `CompareScansUseCase`, `ZebraRfd8500Gateway`, `ReaderStatus` |
| TD-02 | NavHost-weite ViewModels | `MainActivity` |
| TD-03 | ~~Leere R8-Regeln, Release-Build nicht baubar~~ **behoben (2026-10-01)**; Release unsigniert | `proguard-rules.pro` |
| TD-04 | ~~24 ungenutzte String-Ressourcen~~ **entfernt (2026-10-01)**; verbleibend ungenutzt: `apply_power_hint` (abhängig von der Entscheidung zur Leistungsanzeige) | `strings.xml` |
| TD-05 | ~~Ungenutzte Routen `Screen.Scan`, `Screen.Export`~~ **entfernt (2026-10-01)**; die Routen `reader`/`language`/`about` sind teils noch als String-Literale in `MainActivity` | `Nav.kt`, `MainActivity` |
| TD-06 | ~~Ungenutzte Permissions~~ **entfernt (2026-10-01)** | Manifest |
| TD-07 | ~~Reader-Screen: Beschriftung „dBm“~~ **behoben**; `onPowerLevelChange` im Screen-Parameter weiterhin ungenutzt | `ReaderConfigScreen` |
| TD-08 | ~~Sprachauswahl ohne Polnisch; fehlende DE-Strings~~ **behoben (2026-10-01)**: Polnisch entfernt, `match`/`mismatch`/`ok` in DE ergänzt | – |
| TD-09 | App-Version zweimal gepflegt (Gradle + About-Literal) | `AboutScreen`, `build.gradle.kts` |
| TD-10 | Alte Schema-Exporte unter `schemas/de.ca.rfidchecker…` | `app/schemas` |
| TD-11 | Leere `backup_rules.xml`/`data_extraction_rules.xml` ohne Referenz | `res/xml` |
| TD-12 | `ComparisonStatus.ERROR`, `ReaderStatus.batteryPercent` nie befüllt | Domain |
| TD-13 | Feste Company-Prefix-Länge 7 im QR→SGTIN-**Aufbau** (nur noch Anzeige/Persistenz; Vergleich unabhängig seit 2026-10-01) | `Gs1ParserService.buildQrSgtin` |
| TD-14 | `lintDebug` bricht in der Umgebung ab | Tooling |
| TD-15 | Keine instrumentierten Tests, Gateways ungetestet | Tests |
| TD-16 | Titel „QCC“ als Literal | `DashboardScreen` |

## 3 Risikoregister

| ID | Risiko | W | A | Maßnahme / Status |
|---|---|---|---|---|
| R-01 | Hardwareabhängigkeit begrenzt Testautomatisierung | hoch | mittel | Fakes; Hardware-Smoke-Protokoll |
| R-02 | Lokale Vendor-AARs: Reproduzierbarkeit, Lizenz, Größe | mittel | mittel | Prüfsummen, Lizenzklärung (Seite 11) |
| R-03 | NavHost-weite ViewModels erzeugen Seiteneffekte bei neuen Features | mittel | mittel | `setSuspended` als Übergang; Umbau (RM-2) |
| R-04 | ~~Release-Build nicht baubar~~ **behoben (2026-10-01)**, signiert; Restrisiko: Funktionstest des signierten Release-Builds (Minifizierung) steht aus | – | – | V-1 |
| R-05 | Nahbereichs-Leistung (5 %) am Einzelfall kalibriert; Metall/Flüssigkeit/andere Tags | mittel | mittel | Hardware-Tests mit weiteren Artikeln; Leistung ggf. konfigurierbar machen |
| R-06 | Mehrere gleichzeitige Duplikate nicht an Hardware verifiziert | mittel | mittel | H-04 durchführen |
| R-07 | Falsche Gerätezeit verfälscht Tageszähler/CSV | niedrig | mittel | Zeitsynchronisation per MDM |
| R-08 | Android 9–11 ungeprüft (Standort-/Legacy-Bluetooth-Rechte stammen aus den SDKs und werden nicht zur Laufzeit abgefragt; Export auf API 28) | niedrig | mittel | Zielgeräte-Matrix, ggf. `minSdk` anheben |
| R-09 | ~~QR→SGTIN geht von 7-stelligem Company Prefix aus~~ **entschärft (2026-10-01, ADR-19)**: Vergleich über GTIN + Seriennummer; Restrisiko nur in der Anzeige „QR SGTIN“ | – | – | erledigt |
| R-10 | Unbegrenzte Datenhaltung, Backup nicht gesteuert | mittel | niedrig–mittel | V-8 |
| R-11 | Prozessänderung ohne Schulung (Wegfall der Scan-Buttons, Dialoge bei Warnungen) | mittel | niedrig | Kurzanleitung (Seite 04), Einweisung |
| R-12 | Dokumentation driftet vom Code | mittel | mittel | Pflegeprozess (Seite 15) |
| R-13 | Spät abgebauter Screen/Trigger-Besitz: Ursache auf Hardwareebene nicht abschließend belegt | niedrig | hoch | Logging `QccZebra`; bei Wiederauftreten Log auswerten |
| R-14 | Vergleich nutzt String-Gleichheit der Seriennummer; QR-Serials mit führenden Nullen oder Zusatzzeichen würden nicht matchen | niedrig | mittel | mit Fachbereich Serialformat klären |

W = Wahrscheinlichkeit, A = Auswirkung.

## 4 Offene Punkte und zu bestätigende Informationen

| ID | Frage | Adressat |
|---|---|---|
| O-01 | Wer ist Product Owner/Auftraggeber/Fachbereich? Gibt es Stakeholder-Liste und Support-Kontakt? | Management |
| O-02 | Bedeutung der Abkürzung **QCC**; Hintergrund der Umbenennung **MV-C** und Zeitpunkt | PO |
| O-03 | Zielwerte für KPI-1…KPI-8 (Seite 01) | PO / Prozessmanagement |
| O-04 | Auslieferungsweg (MDM, Store, manuell), Signierung, Release-Rhythmus | IT/Betrieb |
| O-05 | Entscheidung SSCC/Mehr-Tag/EPCIS (Inbound Read), Endpunkt, Authentifizierung | PO / Architektur |
| O-06 | QA-Gates auf echter Hardware je Release verbindlich? Formale Abnahmeprotokolle? | QS |
| O-07 | Schutzbedarf, Datenschutzbewertung, Backup-/Export-/Aufbewahrungsregeln | Security / DSB |
| O-08 | Lizenz-/Nutzungsbedingungen Zebra- und Honeywell-SDK | Rechts-/Einkaufsbereich |
| O-09 | Welche Android-Versionen/Geräte sind in Betrieb (nur CT37 mit Android 14)? | IT/Betrieb |
| O-10 | ~~Company-Prefix-Längen~~ erledigt: aktuell 7 Stellen, Vergleich unabhängig davon | – |
| O-11 | ~~Manuelle GTIN~~ **entschieden: entfernt** | PO |
| O-12 | ~~Sendeleistung einstellbar?~~ **entschieden: nur Anzeige** | – |
| O-13 | Soll die Duplikatsuche eine Sollmenge/Fortschritt („n von 480“) kennen? (bewusst out of scope) | PO |
| O-14 | Wo werden die CSV der Duplikatsuche weiterverarbeitet (Reklamationsprozess)? | Fachbereich |
| O-15 | Merge von `fix/duplicate-finder-ux` (inkl. `feature/duplicate-finder`) nach `main` und Push nach `origin` | Entwickler / PO |
