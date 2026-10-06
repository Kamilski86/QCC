# 09 Test und Qualität

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Testbestand** | **118 Tests**, alle grün (`./gradlew :app:testDebugUnitTest`; Stand 2026-10-01, Testergebnisse in `app/build/test-results`) |
| **Quellen** | `app/src/test/`, Anforderungen (Seite 03) |

## 1 Teststrategie

| Ebene | Umfang | Werkzeug | Status |
|---|---|---|---|
| Unit (pure Kotlin) | Parser, Beep-Intervall | JUnit | ✅ |
| ViewModel/Integration | ViewModels mit Fake-Gateways, In-Memory-Room | Robolectric, coroutines-test | ✅ |
| Datenbank/Migration | Migrationen, Constraints | Robolectric + `MigrationTestHelper` | ✅ |
| UI/Layout | Suchmaske ohne Scrollen, Gauge-Größe | Robolectric + Compose `ui-test-junit4` | ✅ |
| Hardware-System | CT37 + RFD8500, CT37 + IH45 | manuell | 🧪 teilweise |
| Instrumentiert (Gerät/Emulator) | – | – | ⛔ nicht vorhanden |

Nicht automatisiert getestet: `ZebraRfd8500Gateway`, `HoneywellCt37ScannerGateway`, `HoneywellIh45Gateway` (SDK-Abhängigkeit; nur die reinen Hilfsfunktionen sind getestet), Diagnose-Ansicht, `ReaderConnectionIndicator`, `MismatchRepository.exportPendingCsv`/`MediaStoreCsvDocumentWriter`, `ToneProximityFeedback` (Audio), Navigation und `MainActivity`-Verdrahtung.

## 2 Testbestand im Detail

| Testklasse | Tests | Schwerpunkt |
|---|---|---|
| `core/gs1/Gs1ParserServiceTest` | 1 | Parser-Grundfall |
| `core/gs1/Gs1ParserServiceTest2` | 4 | EPC/QR-Parsing, Negativfälle |
| `core/gs1/Gs1LocateTargetTest` | 17 | SGTIN→EPC (Filter 0–7, Partitionen, Rundlauf), Eingabeerkennung, Validierung |
| `feature/scan/ScanViewModelTest` | 8 | Reihenfolgefreiheit, Zyklus-Clearing (Hardware-Pfad), Persistenz Match/Mismatch, ungültiger EPC, **Suspendierung**, **Vergleich unabhängig von der Company-Prefix-Länge** (Match bei 8-stelligem Präfix, Mismatch bei anderer Serial/GTIN) |
| `feature/duplicates/DuplicateSearchViewModelTest` | 47 | Phasen, Live-Auswertung, Start ohne Apply, Trigger-Modi, Ortung, Continuous, Confirm (0/1/n/bekannt/Wiederfund), Dialog-Logik, Trigger-Besitz, Fortsetzen offener Suche, Export, Fehlerpfade; **neu:** Loslassen am Tag bestätigt den Fund, Loslassen weiter weg beendet nur die Ortung, erfolgloses Bestätigen spielt den Warnton, Trigger-Druck schließt Dialog ohne Ortung (+4) |
| `feature/duplicates/BeepIntervalTest` | 5 | Beep-Kennlinie 1000→80 ms |
| `feature/duplicates/DuplicateSearchScreenLayoutTest` | 3 | alle Elemente sichtbar ohne Scrollen (360×600 dp), Gauge ≥ 80 dp, TID-Platzhalter „-“ |
| `data/repository/DuplicateSearchRepositoryTest` | 15 | Nummerierung, Wiederfund + Vorfund-Datum, doppelte TID, offene Suche, `lastTid`, CSV (Inhalt, Escape, „Previously Found“), idempotenter Abschluss, Retry nach Exportfehler |
| `data/local/MigrationTest` | 3 | 6→7 (Daten bleiben), 7→8 (Constraints, `previousFoundAt`), Cascade |
| `device/SwitchingRfidReaderGatewayTest` | 7 | ohne IH45 wird der Zebra verwendet; IH45 wird zuerst gelistet und der Zebra vor dem Verbinden getrennt; Flows und Aufrufe folgen dem aktiven Reader; Einstecken schaltet auf den IH45 um und behält die Leistung; Abstecken fällt auf den Zebra zurück; Trigger-Modus erreicht beide Reader; bereits verbundener Reader wird nicht neu verbunden |
| `device/HoneywellIh45GatewayHelpersTest` | 8 | Maskenvergleich `(tag & mask) == (epc & mask)` (Vollmaske, Nullbits, fehlerhafte Eingabe), RSSI→Nähe, nur Berührung erreicht die Bestätigungsschwelle, stärkstes Tag gewinnt und schwache Nachbarn werden ignoriert, zu schwaches stärkstes Tag wird nicht gewählt, Nahfeld wird über die Leistungsstufe entschieden |
| **Summe** | **118** | |

Test-Fakes: `FakeDuplicateReader` (inkl. Arbiter-Semantik), `FakeCsvWriter`, `FakeProximityFeedback` (neu: `resultTone`), `inMemoryDb`.

## 3 Traceability Anforderung → Test

| Anforderung | Tests / Nachweis |
|---|---|
| FR-01, FR-03 | `Gs1ParserServiceTest*`, `Gs1LocateTargetTest` |
| FR-02 | entfallen (manuelle GTIN entfernt) |
| FR-04, FR-05, FR-06 | `ScanViewModelTest` (inkl. AC-11) |
| FR-07, FR-08 | keine Automatisierung (manuell: Dashboard-Zähler, CSV) |
| FR-09 | `SwitchingRfidReaderGatewayTest`, `HoneywellIh45GatewayHelpersTest`; im Übrigen manuell (Hardware) |
| FR-10, FR-11 | `DuplicateSearchViewModelTest`, `Gs1LocateTargetTest` |
| FR-12, FR-13 | `DuplicateSearchViewModelTest` (Store, Start, ohne Reader) |
| FR-14, FR-15 | `DuplicateSearchViewModelTest`, `BeepIntervalTest`; Hardware 🧪 |
| FR-16, FR-17 | `DuplicateSearchViewModelTest`, `DuplicateSearchRepositoryTest`, `MigrationTest` |
| FR-18 | `DuplicateSearchViewModelTest` (Dialog-Klassifikation, Trigger-Sperre, Continuous) |
| FR-19, FR-20 | `DuplicateSearchRepositoryTest`, `DuplicateSearchViewModelTest` |
| FR-23 | `DuplicateSearchViewModelTest` (Loslassen, Trigger schließt Dialog, Warnton); Hardware 🧪 |
| NFR-09, NFR-12 | Hardware 🧪 (Abschnitt 4a), `HoneywellIh45GatewayHelpersTest` (RSSI-Auswahl) |
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

## 4a Hardware-Abnahme IH45 (CT37 + IH45, 2026-10-01)

Das IH45-Gateway ist nicht unit-testbar (SDK); die Abnahme erfolgte manuell auf der Hardware. Auch hier gilt die Belegtiefe-Einschränkung aus Abschnitt 4 (keine einzelne Protokollierung je Punkt).

| # | Prüfung | Ergebnis (Stand) |
|---|---|---|
| HI-01 | USB-Verbinden/Auto-Connect: nach dem Einstecken ca. 5 s, beim Kaltstart ca. 2 s | ✅ am 2026-10-01 geprüft |
| HI-02 | QCC mit Trigger (QR-Scan per Software + RFID-Lesung, stärkstes Tag; Schwelle −50 dBm) | ✅ am 2026-10-01 geprüft |
| HI-03 | Tag übernehmen (Duplikatsuche Phase 1) | ✅ am 2026-10-01 geprüft |
| HI-04 | Ortung mit Leistungsstufen (0/1/2, Anzeige bis 100 % bei Berührung) | ✅ am 2026-10-01 geprüft; Grenze: Nahfeld-RSSI schwankt um 3–5 dB |
| HI-05 | Bestätigen durch Loslassen des Triggers am Tag; Trigger schließt Dialog; Töne | ✅ am 2026-10-01 geprüft (beide Reader laut Entscheidung des Product Owners; Feinabstimmung der Schwellen **[zu bestätigen]**) |
| HI-06 | Reader-Icon und Toast beim Verbinden/Trennen | ✅ am 2026-10-01 geprüft |
| HI-07 | Umschalten Zebra ↔ IH45 durch An-/Abstecken im laufenden Betrieb | Logik per `SwitchingRfidReaderGatewayTest`; Hardware-Durchlauf mit beiden Readern **[zu bestätigen]** |
| HI-08 | **Regressionstest RFD8500** nach Ausschluss von `API3_LLRP` und Einbindung des Honeywell-SDK | ⏳ **offen** |
| HI-09 | **Release-Build (R8) mit IH45 auf dem Gerät** | ⏳ **offen** (Build ✅) |
| HI-10 | H-04 (zwei Duplikate gleichzeitig) mit IH45 | ⏳ offen |

## 5 Nicht-funktionale Tests (Plan)

| ID | Test | Status |
|---|---|---|
| NFT-01 | Stabilität bei 100+ Zyklen | offen |
| NFT-02 | Reaktionszeit Scan → Rückmeldung | offen |
| NFT-03 | Recovery nach Bluetooth-Disconnect im Betrieb | teilweise (Logik per Test, Hardware offen) |
| NFT-04 | Konsistenz der Tageszähler über längere Laufzeit (Datumswechsel) | offen |
| NFT-05 | Release-Build auf Gerät (R8) | teilweise: Build ✅, Start auf CT37 ✅ (Dashboard rendert, kein Absturz, 2026-10-01, vor der IH45-Einbindung); **Funktionstest mit Reader/Scan/Export offen, insbesondere mit IH45** (neue Keep-Regeln, Seite 10) |
| NFT-07 | Stabilität der USB-Verbindung des IH45 (gelegentliches `USB detached`) | offen (Seite 13, R-15) |
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

Bug-Report-Mindestinhalt: Titel, Build/Commit, Gerät, Android-Version, nummerierte Repro-Schritte, erwartet/ist, Schweregrad, Evidenz (Screenshot, **Logcat `adb logcat -s QccZebra QccIh45 QccCt37`**, Exportdatei). Ein Issue-Tracker ist im Repository nicht konfiguriert **[zu bestätigen]**.

## 8 Release-Gates

Freigabe nur, wenn:
1. `./gradlew test` grün,
2. keine offenen Blocker/Critical Defects,
3. Persistenz und Export auf Gerät verifiziert,
4. Hardware-Smoke-Test auf CT37 + RFD8500 **und** CT37 + IH45 bestanden (Checklisten Abschnitt 4: H-01…H-10 und 4a: HI-01…HI-10),
5. **Release-Build** (R8) baut und läuft auf dem Gerät (Build und Start erfüllt; vollständiger Funktionstest, auch mit IH45, offen).

## 9 Empfohlene Verbesserungen

- Hardware-Smoke-Protokoll je Release (Datum, Build, Tester, Ergebnis je H-xx).
- Instrumentierte Kerntests für Navigation/Zustand; Gateway-Tests mit einem dünnen SDK-Adapter.
- Tests für `exportPendingCsv` und die CSV-Formate (Spaltenköpfe sind Vertrag!).
- Zeitabhängige Tageszähler testbar machen (Clock injizieren).
- Defect-Leakage-Tracking zwischen Releases.
