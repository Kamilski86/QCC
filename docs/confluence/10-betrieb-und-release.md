# 10 Betrieb, Deployment und Release

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Quellen** | `SETUP.md`, `app/build.gradle.kts`, Manifest, Build-Versuch `assembleRelease` (2026-10-01), Git-Historie |
| **Hinweis** | Ein formaler Release-/Vertriebsprozess (Signierung, Store/MDM, CI) ist im Repository **nicht dokumentiert**. Abschnitte, die dies betreffen, sind als *Empfehlung* gekennzeichnet. |

## 1 Umgebungen

| Umgebung | Beschreibung |
|---|---|
| Entwicklung | Android Studio / Gradle, Debug-Build, Installation per USB auf das CT37 |
| Test | dasselbe CT37 + RFD8500; keine separate Testumgebung, kein CI **[zu bestätigen]** |
| Produktion | Auslieferungsweg (MDM, Store, manuelle APK-Installation) **[zu bestätigen]** |

Es gibt keine Backend-Systeme; die App ist offline.

## 2 Build-Varianten

| Variante | Status | Hinweis |
|---|---|---|
| `debug` | ✅ baut, läuft auf CT37 | `applicationId de.ca.qcc`, Schemata als Assets (für Tests) |
| `release` | ⛔ **Build scheitert** | R8: `Missing class android.os.ServiceManager (referenced from com.zebra.rfid.RfidServiceMgr.<init>())`; `isMinifyEnabled = true`, `proguard-rules.pro` leer, keine Signierung |

**Vor erstem Release erforderlich:**
1. R8-Regeln für die Vendor-SDKs ergänzen (z. B. `-dontwarn android.os.ServiceManager` und die in `app/build/outputs/mapping/release/missing_rules.txt` vorgeschlagenen Regeln); Keep-Regeln für Zebra-/Honeywell-Reflexion prüfen.
2. Release-Build auf dem CT37 vollständig durchspielen (Scan, RFID, Duplicate Finder, Export) – Minifizierung kann Hilt/Room/kotlinx-serialization/SDK-Aufrufe beeinflussen.
3. Signierung einrichten (Keystore außerhalb des Repositories).

## 3 Installation und Update

### Entwicklungsinstallation
```bash
./gradlew installDebug                                  # ein verbundenes Gerät
ANDROID_SERIAL=<seriennr> ./gradlew installDebug        # mehrere Geräte
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n de.ca.qcc/.app.MainActivity       # App starten
```
Eine bestehende Installation wird ersetzt, **Daten bleiben erhalten**; die Datenbank migriert beim ersten Start automatisch (aktuell bis Schema 8).

### Voraussetzungen am Gerät
- Android 9+ (API 28) – getestet auf **CT37 mit Android 14**.
- Bluetooth an; RFD8500 gekoppelt; Berechtigung „Nearby devices“ (Android 12+) beim ersten Start erteilen.
- Für den CSV-Export: Ordner *Documents* verfügbar (Android 10+ über MediaStore ohne Zusatzberechtigung; **API 28** bräuchte `WRITE_EXTERNAL_STORAGE`, im Manifest nicht deklariert).

### Deinstallation / Datenverlust
Deinstallation oder „Daten löschen“ entfernt `qcc.db` und die Einstellungen. Exportierte CSV-Dateien in *Documents* bleiben bestehen.

## 4 Versionierung

| Aspekt | Ist |
|---|---|
| `versionName` | `0.1.0` (manuell in `app/build.gradle.kts`; zusätzlich **hart codiert** im About-Screen) |
| `versionCode` | `1` (nicht erhöht) |
| Git-Tags | keine (nur ein Sicherungs-Tag `backup-pre-restore-20260420-152031`) |
| DB-Schema | 8 (unabhängig von der App-Version) |

**Empfehlung:** Semantische Versionen; `versionCode` bei jedem ausgelieferten Build erhöhen; Git-Tag `vX.Y.Z` pro Release; About-Screen aus `BuildConfig.VERSION_NAME` speisen (`buildConfig = true` ist bereits aktiviert).

## 5 Release-Prozess (Empfehlung)

1. Branch `main` stabil, alle Tests grün (`./gradlew test`).
2. Version erhöhen, Änderungshistorie (Seite 15) und Release Notes ergänzen.
3. Release-Build mit R8 erzeugen, signieren, auf CT37 prüfen (Hardware-Abnahme Seite 09, H-01…H-10).
4. Git-Tag setzen, Artefakt archivieren (APK + `mapping.txt`).
5. Verteilung (MDM/Store/manuell) **[zu bestätigen]**; Anwender informieren (Schulung bei Verhaltensänderung, z. B. Wegfall der Scan-Buttons).

## 6 Release Notes

### Version 0.1.0 (Stand 2026-10-01, Branch `fix/duplicate-finder-ux`, noch nicht in `main` gemergt)

**Neu**
- **Duplikatsuche („Find duplicates“)**: Ziel per Anlesen, EPC oder SGTIN (Filterwert 0–7 wählbar); Ortung mit Tacho, Beep und Vibration; TID-Erfassung per „Confirm find“; Dauersuche; CSV-Export bei „End search“; offene Suche wird fortgesetzt.
- **Wiederfunde**: Tags, die in einer früheren Suche gefunden wurden und erneut auftauchen, zählen erneut und tragen das Datum des Vorfunds (Dialog und CSV-Spalte „Previously Found“).
- **Dashboard**: Button „Find duplicates“.
- Popups (Dialog mit OK) für Warnungen und Fehler im Duplicate Finder.

**Geändert**
- Dashboard: Buttons „QR Scan“ und „RFID Scan“ entfallen (Scan nur per Pistolen-Trigger). „Reset“ und „Export Mismatches (CSV)“ bleiben.
- Duplicate Finder, Phase 1: „Apply“ und „Read with reader“ entfallen (Eingabe wird direkt ausgewertet; Trigger liest an).
- Duplicate Finder, Phase 2: eine Bildschirmseite ohne Scrollen; neue TID-Zeile; Kachel „Last“ entfällt.
- **Datenbank Schema 8** (Migration von 7 automatisch, Daten bleiben).
- Trigger-Modus mit Besitzer; QCC pausiert, solange der Duplicate Finder offen ist.

**Behoben**
- Nach Wechsel Duplicates → Dashboard funktionierte der QCC-Scan nicht zuverlässig; Ortung/Ton konnten im Hintergrund weiterlaufen.
- „Already found“-Meldung ging verloren (stand oben in einer scrollbaren Liste).
- Zuletzt gelesene TID ging beim Wiederöffnen einer offenen Suche verloren.

**Bekannte Probleme**
- Release-Build scheitert an R8 (siehe Abschnitt 2).
- Test mit **zwei gleichzeitig gehaltenen Duplikaten** steht aus.
- Sendeleistung im Reader-Screen nur angezeigt; manuelle GTIN ohne UI; Polnisch nicht in der Sprachauswahl (Seite 13).

## 7 Betrieb und Support

| Thema | Ist / Empfehlung |
|---|---|
| Monitoring / Crash-Reporting | keines; Logging nur per Logcat (`QccZebra`) |
| Support-Information bei Störungen | Build/Commit, Gerät, Android-Version, Repro-Schritte, `adb logcat -s QccZebra`, ggf. Exportdateien (Seite 09, Abschnitt 7) |
| Datensicherung | lokale DB (`/data/data/de.ca.qcc/databases/qcc.db`) per Android-Backup möglich (siehe Seite 11); **CSV-Exporte** sind die vorgesehene Auswertungsquelle |
| Datenabzug | Dateimanager → *Documents* → `scans_*.csv`, `duplicates_*.csv`; per USB/MTP oder E-Mail/MDM |
| Zeit/Datum | Zeitstempel und „heute“ folgen Gerätezeit/-zeitzone; falsche Gerätezeit verfälscht Tageszähler und CSV |
| Rollback | Downgrade der App-Version nicht möglich, wenn die DB bereits migriert wurde (Room-Downgrade-Fehler); Rückkehr nur über Deinstallation (Datenverlust) |
| Zuständigkeit/Support-Kontakt | **[zu bestätigen]** |

### Runbook – häufige Störungen
Siehe Benutzerhandbuch Abschnitt 7 (Anwender-Sicht) und Entwicklerhandbuch Abschnitt 7 (Analyse).
