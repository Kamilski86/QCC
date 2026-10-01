# 15 Anhang

| | |
|---|---|
| **Stand** | 2026-10-01 |

## A Änderungshistorie der Applikation (aus Git)

| Datum | Meilenstein | Commits (Auszug) |
|---|---|---|
| 2026-04-20 | **Erstversion „CaRfidChecker“**: Scan-Vergleich QR/RFID mit Reihenfolgefreiheit, Zyklus-Clearing, Room (Schema 6) + CSV, Zebra RFD8500 und Honeywell CT37, GS1/EPC-Parser, Projektdokumentation; `SETUP.md`; Status-Spalte im CSV-Export | `bc98759`, `f5f3da2`, `0946aa7` |
| 2026-05-06 | **Umbenennung** CaRfidChecker → QCC (Paket `de.ca.qcc`, DB `qcc.db`, Theme, Doku) | `f94bcf2` |
| 2026-05-11 | Sammelcommit des Arbeitsstands | `499466a` |
| 2026-09-29 | Scan-Zyklus an QCC-Anforderungen angeglichen; **Design-Spec und Plan** des Duplicate Finders | `ac7f440`, `90976fc`, `0e2b1cc` |
| 2026-09-30 | **Duplicate Finder** (Branch `feature/duplicate-finder`): GS1-Locate-Ziele und SGTIN-96-Encoding, Trigger-Modi/Ortung/Nahbereichs-Lesen im Zebra-Gateway (Hardware-Spike, Leistung/TID-Länge), DB Schema 7, Repository + CSV-Writer, Proximity-Feedback, ViewModel, Screen + Gauge, Drawer-Eintrag, EN/DE/PL-Strings, Härtungen (Locate-Serialisierung, ToneGenerator, Idempotenz), Dokumentation | `bd0ea0d` … `ce8daa4` |
| 2026-10-01 | **UX-/Stabilitätsfolgearbeit** (Branch `fix/duplicate-finder-ux`): Trigger-Besitz und QCC-Isolation (`1e9a613`); Wiederfunde mit Datum, Dialoge, Schema 8 (`fcfc3ca`); Dashboard-Button, Ein-Bildschirm-Suchmaske, Maske 1 ohne Apply/Capture, Dialoge für alle Warnungen (`6858e3c`); `CLAUDE.md` aktualisiert (`88d0b55`); **Dokumentation neu** (`docs/confluence/`) | `1e9a613`, `fcfc3ca`, `6858e3c`, `88d0b55`, `38dd09e` |
| 2026-10-01 | **Aufräumen und Release-Build**: manuelle GTIN, 24 ungenutzte Strings, drei ungenutzte Permissions und zwei ungenutzte Routen entfernt (`f36854b`); R8-Regeln für die Vendor-SDKs, Release-Build läuft (`af94d09`) | `f36854b`, `af94d09` |
| 2026-10-01 | **Release-Signierung** (Keystore außerhalb des Repos, `keystore.properties`, signierter Build); Korrektur Seite 11 (Berechtigungen aus den SDKs) | folgt |
| 2026-10-01 | **Vergleich über GTIN + Seriennummer** (Company-Prefix-unabhängig), **Leistungsanzeige** „Level n of 30“, **Polnisch entfernt** (siehe Seiten 03, 10, 12, 13) | folgt |

Stand der Branches: `main` enthält noch **nicht** die Duplicate-Finder-Arbeit; `fix/duplicate-finder-ux` ist auf `feature/duplicate-finder` aufgebaut (rund 30 Commits vor `main`). Merge und Push stehen aus (Seite 13, O-15).

## B Pflege der Dokumentation

### B.1 Grundsätze
- **Single Source:** Diese Seitenfamilie (`docs/confluence/`) ist die maßgebliche Dokumentation. Die Rollen-Dokumente `docs/01`–`05` sind **archiviert** und verweisen hierher. `CLAUDE.md`/`AGENTS.md` bleiben knappe Leitplanken für KI-Agenten; `SETUP.md` die kurze Einstiegsanleitung.
- **Doku mit dem Code ändern:** Jede Verhaltens-, Schema- oder Schnittstellenänderung aktualisiert im selben Pull Request die betroffenen Seiten.
- **Belegen statt behaupten:** Nicht belegbare Aussagen werden mit **[zu bestätigen]** markiert und auf Seite 13 gesammelt.

### B.2 Welche Änderung betrifft welche Seite

| Änderung | Seiten |
|---|---|
| Neue/geänderte Funktion, Regel, Meldung | 02, 03 (FR/AC/BR), 04, 09 |
| Neuer Bildschirm / Navigation | 02, 04, 05, 08 |
| Datenbank / CSV | 06, 03 (NFR-06), 09, 10 (Release Notes) |
| Hardware / Gateway / GS1 | 07, 05, 12 (ADR) |
| Architekturentscheidung | 12, 05 |
| Build / Tooling / Umgebung | 08, 10 |
| Berechtigungen / Datenhaltung | 11 |
| Risiko / Schuld erkannt oder behoben | 13 |
| Jede Auslieferung | 10 (Release Notes), 15 (Änderungshistorie), `versionName`/`versionCode` |

### B.3 Review-Checkliste vor dem Merge
- [ ] Anforderungs-IDs und Akzeptanzkriterien aktuell, Traceability-Matrix (Seite 03) angepasst
- [ ] Testbestand und Zahl der Tests (Seite 09) aktualisiert
- [ ] Schemaversion/Migrationen (Seiten 00, 06) aktualisiert
- [ ] Neue Strings in EN/DE/PL, Benutzerhandbuch (Seite 04) angepasst
- [ ] Offene Punkte/Risiken (Seite 13) und Glossar (Seite 14) geprüft
- [ ] Änderungshistorie (Anhang A) ergänzt

### B.4 Verantwortlichkeiten
Fachliche Inhalte: Product Owner **[zu bestätigen]**; technische Inhalte: Entwicklung; Test: QS; Review der Seite 11: Security/Datenschutz **[zu bestätigen]**.

## C Abdeckung: Was gehört zu einer vollständigen Applikationsdokumentation?

| Thema | Seite | Status |
|---|---|---|
| Hintergrund, Problem, Nutzen, Stakeholder, KPIs | 01 | ✅ (KPI-Zielwerte offen) |
| Produktbeschreibung, Funktionskatalog, Screens | 02 | ✅ |
| Anforderungen (funktional, nicht-funktional, Regeln, Use Cases, AC, Traceability) | 03 | ✅ |
| Benutzerhandbuch, Fehlerbehebung | 04 | ✅ |
| Architektur (Kontext, Bausteine, Laufzeit, Querschnitt, Qualität) | 05 | ✅ |
| Architekturentscheidungen | 12 | ✅ |
| Datenmodell, Migrationen, Dateiformate, Datenlebenszyklus | 06 | ✅ |
| Schnittstellen, Hardware, GS1-Spezifikation | 07 | ✅ |
| Entwicklerhandbuch, Setup, Konventionen | 08 | ✅ |
| Test, Abnahme, Traceability | 09 | ✅ (Belegtiefe der Hardware-Abnahme begrenzt) |
| Betrieb, Deployment, Release Notes, Versionierung | 10 | 🟡 (kein formaler Release-/Distributionsprozess im Repo) |
| Sicherheit und Datenschutz | 11 | 🟡 (keine formale Bewertung) |
| Roadmap, Risiken, Schulden, offene Punkte | 13 | ✅ |
| Glossar | 14 | ✅ |
| Barrierefreiheit | – | nicht dokumentiert; keine Anforderung im Repo **[zu bestätigen]** |
| Schulungsunterlagen / Kurzanleitung zum Ausdrucken | – | nicht vorhanden (Seite 04 als Basis) |
| API-Referenz (KDoc/Dokka) | – | nicht vorhanden (Kommentare im Code sparsam); optional via Dokka |
| Betriebshandbuch mit SLAs/Support-Prozess | – | nicht vorhanden, siehe Seite 13 (O-01, O-04) |

## D Abbildung der bisherigen Dokumente

| Bisherige Datei | Aufgegangen in |
|---|---|
| `docs/01-project-manager-user-stories.md` | 01 (Vision, Nutzen, KPIs, Scope), 03 (User-Story-Inhalte als FR/AC), 13 (Releasewellen) |
| `docs/02-business-architect-architecture.md` | 05, 12 |
| `docs/03-business-analyst-requirements.md` | 03, 07 (Parser-Regeln) |
| `docs/04-developer-documentation.md` | 08 |
| `docs/05-tester-documentation.md` | 09 |
| `docs/superpowers/specs/2026-09-29-duplicate-finder-design.md` | 03 (FR-10…FR-22), 05, 06, 07, 12; **die Spec selbst bleibt als Entwurfsdokument erhalten** und ist in Teilen überholt (globale TID-Eindeutigkeit, „Apply“/„Read with reader“, Meldungs-Karte, Spalte „Last“) |
| `SETUP.md`, `CLAUDE.md`, `AGENTS.md` | bleiben; Fakten aktualisiert |

## E Quellen

- Code: `app/src/main/java/de/ca/qcc/`, `app/src/test/java/de/ca/qcc/`, `app/build.gradle.kts`, `AndroidManifest.xml`, `app/schemas/`
- Repository-Dokumente: `README.md`, `SETUP.md`, `CLAUDE.md`, `AGENTS.md`, `docs/`, `tasks/todo.md`, `agents/`
- Hardware-Spike und Abnahme: Design-Spec (Abschnitt 10) und Rückmeldungen des Anwenders (2026-09-30, 2026-10-01)
- Git-Historie (`git log`), Build-/Testläufe vom 2026-10-01 (`assembleDebug`, `testDebugUnitTest`, `assembleRelease`)
- Externe Standards: GS1 General Specifications, GS1 EPC Tag Data Standard (SGTIN-96), GS1 EPCIS 2.0
