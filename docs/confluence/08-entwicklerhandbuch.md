# 08 Entwicklerhandbuch

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Quellen** | `SETUP.md`, `CLAUDE.md`, `AGENTS.md`, `app/build.gradle.kts`, Git-Historie |

## 1 Toolchain und Versionen

| Komponente | Version |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle (Wrapper) | 8.13 (lokal geändert, siehe unten) |
| Kotlin | 2.0.21 (Compose-Compiler-Plugin 2.0.21), KSP `2.0.21-1.0.28` |
| JDK | 17 (`sourceCompatibility`/`jvmTarget` 17) |
| Compose BOM | 2024.06.00 (Material 3, Icons Extended) |
| AndroidX | core-ktx 1.13.1, appcompat 1.7.0, lifecycle 2.8.4, activity-compose 1.9.1, navigation-compose 2.7.7 |
| DI | Hilt 2.51, hilt-navigation-compose 1.2.0 |
| Persistenz | Room 2.6.1 (+ ktx, KSP-Compiler, Testing) |
| Async / Serialisierung | kotlinx-coroutines 1.8.1, kotlinx-serialization-json 1.6.3 |
| Test | JUnit 4.13.2, Robolectric 4.14.1, androidx.test core-ktx 1.6.1, coroutines-test 1.8.1, Compose `ui-test-junit4` |
| Android | minSdk 28, targetSdk/compileSdk 34, `versionName 0.1.0`, `versionCode 1` |

> Im Arbeitsverzeichnis liegen **bewusst nicht committete lokale Änderungen** an `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.13), `settings.gradle.kts` (foojay-Plugin entfernt) und `.claude/settings.local.json`. Nicht versehentlich mitcommitten.

## 2 Einrichtung

1. **Android Studio** in einer **aktuellen stabilen Version** installieren (SETUP.md nennt Hedgehog 2023.1 als Untergrenze; das eingesetzte AGP 8.13.2 setzt eine entsprechend neue Android-Studio-Version voraus) – mit JDK 17.
2. Repository klonen (`origin`: `https://github.com/Kamilski86/KaJak.git`) und den Projektordner öffnen; Gradle-Sync abwarten (bei Problemen *Invalidate Caches / Restart* oder `./gradlew clean`).
3. **Vendor-SDKs** liegen im Repository (`app/libs/*.aar`, `vendor/zebra/*.aar`) und werden per `fileTree` eingebunden – kein Download nötig.
4. Gerät: CT37 per USB, **USB-Debugging** aktivieren (Einstellungen → Über das Telefon → Build-Nummer 7× tippen → Entwickleroptionen). Zebra RFD8500 koppeln.

> Ohne Hardware (CT37 + RFD8500) lässt sich die App nur eingeschränkt testen; es gibt **kein Emulator-Ziel**. Unit-Tests laufen ohne Hardware.

## 3 Build- und Prüfbefehle (im Repo-Stamm)

```bash
./gradlew assembleDebug                 # Debug-APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # Build + Installation auf verbundenem Gerät
./gradlew test                          # alle Unit-/Robolectric-Tests
./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.feature.duplicates.DuplicateSearchViewModelTest"
./gradlew lint                          # siehe bekannte Probleme
./gradlew assembleRelease               # siehe bekannte Probleme (R8)
./gradlew clean
```

Mehrere Geräte: `ANDROID_SERIAL=<seriennummer> ./gradlew installDebug`; Geräteliste `adb devices -l`.

### Bekannte Probleme der Umgebung

| Problem | Details |
|---|---|
| `./gradlew lintDebug` stürzt ab | „Exception occurred when reading Kotlin metadata“ – auch auf dem Baseline-Commit `499466a`; nicht durch Feature-Arbeit verursacht |
| Lint-Befunde | 8 alte `MissingTranslation`-Fehler (z. B. `cancel`, `actions`, …) unbehoben |
| `assembleRelease` | **behoben (2026-10-01)**: `app/proguard-rules.pro` hält `com.zebra.**` und `com.honeywell.**` unobfuskiert (Honeywell bündelt JNI-Bibliotheken) und unterdrückt Warnungen für nicht genutzte Zebra-Transporte (JSch, NanoHTTPD, Xerces, BouncyCastle, WebSocket). Die Datei ist jetzt **Pflichtbestandteil** des Release-Builds. Ergebnis: unsigniertes `app-release-unsigned.apk` (ca. 12 MB) |
| Keine Signaturkonfiguration | `release` hat kein `signingConfig`; das APK ist **unsigniert** und nicht installierbar, bis signiert wird (Seite 10) |

## 4 Projektstruktur

Siehe [05 Architektur](05-architektur.md), Abschnitt „Paketstruktur“. Wichtige weitere Orte:

| Pfad | Inhalt |
|---|---|
| `app/schemas/de.ca.qcc.data.local.AppDatabase/` | exportierte Room-Schemata (6, 7, 8); in `debug` als Assets für `MigrationTest` eingebunden |
| `app/src/test/…/testutil/DuplicateFakes.kt` | Test-Fakes (`FakeDuplicateReader`, `FakeCsvWriter`, `FakeProximityFeedback`, `inMemoryDb`) |
| `docs/confluence/` | diese Dokumentation |
| `docs/superpowers/specs`, `…/plans` | Design-Spec und Umsetzungsplan des Duplicate Finders |
| `tasks/todo.md` | Aufgabenplan der laufenden Arbeit (siehe Workflow) |
| `agents/` | Rollenleitfäden für KI-Agenten (Product Manager, Architekt, Analyst, Entwickler, Tester, Security, DevOps, Technical Writer …) |
| `CLAUDE.md`, `AGENTS.md` | Leitplanken für KI-gestützte Entwicklung |

## 5 Konventionen

- **Architektur:** keine direkte SDK-Nutzung im UI-Layer; immer über Gateways. Neue Hardware-Funktionen erst im Interface (mit Default-Implementierung), dann im Gateway, dann im Fake.
- **Strings:** jede sichtbare Zeichenkette in `values/` (EN), `values-de/`, `values-pl/` anlegen; keine Klartexte in Composables. Der Duplicate Finder liefert **typisierte** Meldungen (`DuplicateMessage`), die UI übersetzt.
- **Datenbank:** Schemaänderung nur mit Versionserhöhung, Migration, Registrierung in `AppModule`, committetem Schema-JSON und `MigrationTest`.
- **Parser:** QR/EPC immer über `Gs1ParserService` (pure Kotlin, ohne Android-Abhängigkeit).
- **Ereignisse:** Hardware-Ergebnisse als `SharedFlow` mit `tryEmit`; Zustand als `StateFlow`.
- **Scopes:** neue Use-Case-Screens bekommen ihr ViewModel **in der Destination** (`hiltViewModel()` im `composable`), nicht auf NavHost-Ebene.
- **Trigger:** Screens, die den Pistolen-Trigger anders nutzen, belegen ihn über `claimTriggerMode(owner, mode)` und geben ihn mit `releaseTriggerMode(owner)` frei; der QCC-Zyklus wird für die Dauer pausiert (`ScanViewModel.setSuspended`).
- **Design:** weiße Karten, schwarze Texte, schwarze Buttons; Layout-Änderungen mit dem Layouttest absichern.
- **Commits:** Conventional Commits (`feat(scope): …`, `fix(scope): …`, `docs: …`, `test: …`); Feature-Branches (`feature/…`, `fix/…`); Merge nach `main` per `--ff-only`. Co-Author-Zeile der KI laut Session-Vorgabe.

## 6 Typische Aufgaben

### 6.1 Neuen Use-Case-Screen anlegen
1. `feature/<name>/<Name>Screen.kt` und `<Name>ViewModel.kt` (`@HiltViewModel`) erstellen.
2. Route in `navigation/Nav.kt` (`Screen`) ergänzen.
3. In `MainActivity.AppNav` ein `composable(Screen.<Name>.route)` ergänzen; ViewModel **dort** per `hiltViewModel()` erzeugen.
4. Falls der Trigger/Reader anders genutzt wird: `claim/releaseTriggerMode`, Lifecycle-Observer wie beim Duplicate Finder, `ScanViewModel.setSuspended` für die Route setzen.
5. Strings in drei Sprachen, Tests (ViewModel mit Fake-Gateway), Doku (Seiten 02–04, 09).

### 6.2 Neue Regel im Vergleich
Regel im `CompareScansUseCase` oder Parser verorten, Negativtest ergänzen, Seite 03 (Anforderung/AC) und 09 aktualisieren.

### 6.3 Datenbankschema ändern
Siehe Seite 06, Abschnitt 4. Beispiel `MIGRATION_7_8` und `MigrationTest.migrate7To8_…`.

### 6.4 Neue Gateway-Funktion
`RfidReaderGateway` mit Default-Implementierung erweitern → `ZebraRfd8500Gateway` → `FakeDuplicateReader` → Test.

## 7 Debugging

| Symptom | Prüfen |
|---|---|
| Scanner liefert keine Daten | Honeywell-Lifecycle in `MainActivity` (`claim/release`); Triggerpfad `ScanViewModel.triggerQrScan`; ist `ScanViewModel` **suspended** (Duplicates-Route offen)? |
| RFID liefert kein Ergebnis | `observeStatus()`; Reconnect; **Trigger-Modus** (Logcat `QccZebra`: `triggerMode X -> Y`); `Inventory.perform failed` |
| Trigger im Dashboard tot nach Duplicates | Log `releaseTriggerMode ignored: … no longer owns the trigger` vs. fehlendes Release; `locateActive`/`inventoryActive` in der Moduswechsel-Zeile |
| Ortung stoppt nicht | `TagLocationing.Stop failed` im Log |
| Vergleich startet nicht | `ScanUiState.qr`/`rfid` gesetzt? `error`-Feld; Parser-Meldung |
| Nahbereichs-Lesen liefert nichts | Leistungsindex (5 %, min. 15), 700-ms-Fenster, `Reader beschäftigt` |

**Logcat:** `adb logcat -s QccZebra` (Moduswechsel mit Zustand, fehlgeschlagene Inventory-/Locate-Aufrufe, Besitzer-Freigabe).

## 8 Tests ausführen

Siehe [09 Test und Qualität](09-test-und-qualitaet.md). Kurz: `./gradlew :app:testDebugUnitTest`. Robolectric-Tests laufen mit `@Config(sdk = [34], application = Application::class)`; In-Memory-Room mit `allowMainThreadQueries` und synchronen Executors; Coroutinen mit `UnconfinedTestDispatcher` als `Dispatchers.Main`.

## 9 Arbeitsweise mit KI-Unterstützung (Projektkontext)

Das Projekt wird mit KI-Agenten (Claude Code) entwickelt. Leitplanken: `CLAUDE.md`/`AGENTS.md`; Planen vor Umsetzen (`tasks/todo.md`), Tests zuerst (TDD), Verifikation vor „fertig“, Lehren nach Korrekturen in `tasks/lessons.md` (Datei existiert noch nicht). Persistenter Projekt-Kontext liegt im Memory des Agenten (lokal, nicht im Repository).

## 10 Definition of Done (Entwicklung)

- Code kompiliert, `./gradlew test` grün, keine neuen Lint-Befunde.
- Fachregeln stimmen mit Anforderungen/AC überein; Hardware-Pfade auf CT37 + RFD8500 geprüft.
- Strings in drei Sprachen; Migration + Schema-JSON bei DB-Änderung.
- Doku (`docs/confluence/`) aktualisiert – mindestens Seiten 03 (Anforderungen), 09 (Test), 15 (Änderungshistorie) und je nach Änderung 04/05/06/07.
