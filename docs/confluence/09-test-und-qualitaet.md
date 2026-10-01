# 09 Test und Qualität

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Testbestand** | **99 Tests**, alle grün (`./gradlew :app:testDebugUnitTest`) |
| **Quellen** | `app/src/test/`, Anforderungen (Seite 03) |

## 1 Teststrategie

| Ebene | Umfang | Werkzeug | Status |
|---|---|---|---|
| Unit (pure Kotlin) | Parser, Beep-Intervall | JUnit | ✅ |
| ViewModel/Integration | ViewModels mit Fake-Gateways, In-Memory-Room | Robolectric, coroutines-test | ✅ |
| Datenbank/Migration | Migrationen, Constraints | Robolectric + `MigrationTestHelper` | ✅ |
| UI/Layout | Suchmaske ohne Scrollen, Gauge-Größe | Robolectric + Compose `ui-test-junit4` | ✅ |
| Hardware-System | CT37 + RFD8500 | manuell | 🧪 teilweise |
| Instrumentiert (Gerät/Emulator) | – | – | ⛔ nicht vorhanden |

Nicht automatisiert getestet: `ZebraRfd8500Gateway`, `HoneywellCt37ScannerGateway` (SDK-Abhängigkeit), `MismatchRepository.exportPendingCsv`/`MediaStoreCsvDocumentWriter`, `ToneProximityFeedback` (Audio), Navigation und `MainActivity`-Verdrahtung.

## 2 Testbestand im Detail

| Testklasse | Tests | Schwerpunkt |
|---|---|---|
| `core/gs1/Gs1ParserServiceTest` | 1 | Parser-Grundfall |
| `core/gs1/Gs1ParserServiceTest2` | 4 | EPC/QR-Parsing, Negativfälle |
| `core/gs1/Gs1LocateTargetTest` | 17 | SGTIN→EPC (Filter 0–7, Partitionen, Rundlauf), Eingabeerkennung, Validierung |
| `feature/scan/ScanViewModelTest` | 8 | Reihenfolgefreiheit, Zyklus-Clearing (Hardware-Pfad), Persistenz Match/Mismatch, ungültiger EPC, **Suspendierung**, **Vergleich unabhängig von der Company-Prefix-Länge** (Match bei 8-stelligem Präfix, Mismatch bei anderer Serial/GTIN) |
| `feature/duplicates/DuplicateSearchViewModelTest` | 43 | Phasen, Live-Auswertung, Start ohne Apply, Trigger-Modi, Ortung, Continuous, Confirm (0/1/n/bekannt/Wiederfund), Dialog-Logik, Trigger-Besitz, Fortsetzen offener Suche, Export, Fehlerpfade |
| `feature/duplicates/BeepIntervalTest` | 5 | Beep-Kennlinie 1000→80 ms |
| `feature/duplicates/DuplicateSearchScreenLayoutTest` | 3 | alle Elemente sichtbar ohne Scrollen (360×600 dp), Gauge ≥ 80 dp, TID-Platzhalter „-“ |
| `data/repository/DuplicateSearchRepositoryTest` | 15 | Nummerierung, Wiederfund + Vorfund-Datum, doppelte TID, offene Suche, `lastTid`, CSV (Inhalt, Escape, „Previously Found“), idempotenter Abschluss, Retry nach Exportfehler |
| `data/local/MigrationTest` | 3 | 6→7 (Daten bleiben), 7→8 (Constraints, `previousFoundAt`), Cascade |
| **Summe** | **99** | |

Test-Fakes: `FakeDuplicateReader` (inkl. Arbiter-Semantik), `FakeCsvWriter`, `FakeProximityFeedback`, `inMemoryDb`.

## 3 Traceability Anforderung → Test

| Anforderung | Tests / Nachweis |
|---|---|
| FR-01, FR-03 | `Gs1ParserServiceTest*`, `Gs1LocateTargetTest` |
| FR-02 | entfallen (manuelle GTIN entfernt) |
| FR-04, FR-05, FR-06 | `ScanViewModelTest` (inkl. AC-11) |
| FR-07, FR-08 | keine Automatisierung (manuell: Dashboard-Zähler, CSV) |
| FR-09 | manuell |
| FR-10, FR-11 | `DuplicateSearchViewModelTest`, `Gs1LocateTargetTest` |
| FR-12, FR-13 | `DuplicateSearchViewModelTest` (Store, Start, ohne Reader) |
| FR-14, FR-15 | `DuplicateSearchViewModelTest`, `BeepIntervalTest`; Hardware 🧪 |
| FR-16, FR-17 | `DuplicateSearchViewModelTest`, `DuplicateSearchRepositoryTest`, `MigrationTest` |
| FR-18 | `DuplicateSearchViewModelTest` (Dialog-Klassifikation, Trigger-Sperre, Continuous) |
| FR-19, FR-20 | `DuplicateSearchRepositoryTest`, `DuplicateSearchViewModelTest` |
| FR-21 | `staleViewModelStopping_…`, `screenStoppedTwice_…`, `continuousRequestedAfterScreenStopped_…`, `ScanViewModelTest.scansWhileSuspended_…`; Hardware 🧪 |
| NFR-06 | `MigrationTest`, Repository-Tests |
| NFR-07 | `DuplicateSearchScreenLayoutTest` |

## 4 Hardware-Abnahme (manuelle Checkliste, RFD8500 + CT37)

| # | Prüfung | Ergebnis (Stand) |
|---|---|---|
| H-01 | Ortung mit gehaltenem Trigger; Tacho/Beep/Vibration folgen der Nähe | ✅ pauschal bestätigt (2026-09-30) |
| H-02 | „Continuous search“ ohne Trigger | ✅ pauschal bestätigt (2026-09-30) |
| H-03 | Confirm find: 1 Tag / 0 Tags / bereits gefunden | ✅ pauschal bestätigt (2026-09-30) |
| H-04 | Confirm find: **zwei Duplikate gleichzeitig** → „Several duplicates detected“ | ⏳ **offen** (nur ein Duplikat verfügbar) |
| H-05 | Anlesen (Phase 1) | ✅ pauschal bestätigt (2026-09-30) |
| H-06 | getippte SGTIN (Filter 1) findet den echten Tag | ✅ pauschal bestätigt (2026-09-30) |
| H-07 | QCC-Trigger-Verhalten unverändert nach Duplicates | ✅ (Anwender-Rückmeldung 2026-10-01 „passt“) |
| H-08 | Wiederfund aus früherer Suche mit Datum im Dialog | ⏳ Prüfung beim Anwender angefragt; Ergebnis nicht protokolliert |
| H-09 | Suchmaske ohne Scrollen auf dem CT37 | ✅ (Anwender-Rückmeldung 2026-10-01) |
| H-10 | Dashboard → Duplicates → Dashboard mehrfach (schnell/langsam), danach QR+RFID | ✅ (Anwender-Rückmeldung 2026-10-01) |

> **Belegtiefe:** Am 2026-09-30 bestätigte der Anwender auf CT37 + RFD8500 pauschal „es funktioniert“; am 2026-10-01 meldete er nach Installation „es passt“. Welche Checklistenpunkte dabei einzeln durchgespielt wurden, wurde **nicht protokolliert** – die ✅ in dieser Tabelle bedeuten daher „pauschal bestätigt“, nicht „einzeln nachgewiesen“. Nachgewiesen durch Hardware-Spike sind H-01/H-03/H-05/H-06 in Teilen (Seite 07, Abschnitt 4). Für ein formales Protokoll bitte beim nächsten Release mit Datum, Build und Tester festhalten.

## 5 Nicht-funktionale Tests (Plan)

| ID | Test | Status |
|---|---|---|
| NFT-01 | Stabilität bei 100+ Zyklen | offen |
| NFT-02 | Reaktionszeit Scan → Rückmeldung | offen |
| NFT-03 | Recovery nach Bluetooth-Disconnect im Betrieb | teilweise (Logik per Test, Hardware offen) |
| NFT-04 | Konsistenz der Tageszähler über längere Laufzeit (Datumswechsel) | offen |
| NFT-05 | Release-Build auf Gerät (R8) | teilweise: Build ✅, Start auf CT37 ✅ (Dashboard rendert, kein Absturz, 2026-10-01); **Funktionstest mit Reader/Scan/Export offen** (signierter Release-Build nötig) |
| NFT-06 | Layout bei größerer Systemschrift / anderen Displays | offen (Gauge skaliert, nicht getestet) |

## 6 Testdaten

| Zweck | Wert |
|---|---|
| gültiger QR | `http://qr.c-a.com/01/04065546927682/21/12884901894` |
| passender EPC (Filter 0) | `3014F824285A980300000006` |
| passender EPC (Filter 1) | `3034F824285A980300000006` ↔ SGTIN `4065546.092768.12884901894` |
| kein SGTIN-96-Header | `E28068940000500000000001` |
| echter Duplikat-EPC | `3034F8242800C10300000011` ↔ SGTIN `4065546.000772.12884901905` |
| Beispiel-TID | `E2801191A503006B7A41A41F` |

## 7 Defect-Management

| Schweregrad | Bedeutung |
|---|---|
| Blocker | Kernprozess nicht nutzbar |
| Critical | falsche Vergleichsentscheidung, Datenverlust |
| Major | wichtige Funktion beeinträchtigt, Workaround möglich |
| Minor | geringe Auswirkung |

Bug-Report-Mindestinhalt: Titel, Build/Commit, Gerät, Android-Version, nummerierte Repro-Schritte, erwartet/ist, Schweregrad, Evidenz (Screenshot, **Logcat `adb logcat -s QccZebra`**, Exportdatei). Ein Issue-Tracker ist im Repository nicht konfiguriert **[zu bestätigen]**.

## 8 Release-Gates

Freigabe nur, wenn:
1. `./gradlew test` grün,
2. keine offenen Blocker/Critical Defects,
3. Persistenz und Export auf Gerät verifiziert,
4. Hardware-Smoke-Test auf CT37 + RFD8500 bestanden (Checkliste Abschnitt 4: H-01…H-10),
5. **Release-Build** (R8) baut und läuft auf dem Gerät (Build und Start erfüllt; vollständiger Funktionstest offen).

## 9 Empfohlene Verbesserungen

- Hardware-Smoke-Protokoll je Release (Datum, Build, Tester, Ergebnis je H-xx).
- Instrumentierte Kerntests für Navigation/Zustand; Gateway-Tests mit einem dünnen SDK-Adapter.
- Tests für `exportPendingCsv` und die CSV-Formate (Spaltenköpfe sind Vertrag!).
- Zeitabhängige Tageszähler testbar machen (Clock injizieren).
- Defect-Leakage-Tracking zwischen Releases.
