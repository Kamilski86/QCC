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
| `release` | ✅ baut (seit 2026-10-01), ⏳ unsigniert | R8 mit `proguard-rules.pro` (Vendor-SDKs bleiben unobfuskiert); Ausgabe `app/build/outputs/apk/release/app-release-unsigned.apk` (ca. 12 MB). Start auf dem CT37 geprüft (Dashboard rendert, kein Absturz); **Funktionstest mit Reader/Scan/Export steht aus** |

**Release-Build-Konfiguration (erledigt 2026-10-01):** Ursache des früheren Fehlers war ein von Zebras SDK referenziertes, nicht öffentliches Android-Klassenobjekt (`android.os.ServiceManager`). `app/proguard-rules.pro` enthält nun
1. `-dontwarn android.os.ServiceManager`,
2. `-keep class com.zebra.** { *; }` und `-keep class com.honeywell.** { *; }` – die Zebra-AARs liefern leere Consumer-Rules, Honeywell bündelt JNI-Bibliotheken (`libhedc.so`), die an Klassennamen gebunden sind; ohne Keep benennt R8 z. B. `RFIDReader` in `a6.u` um,
3. `-dontwarn` für nicht genutzte Zebra-Transporte (`com.jcraft.jsch`, `fi.iki.elonen`, `org.apache.xerces`, `org.bouncycastle`, `org.java_websocket`).

**Noch erforderlich vor Auslieferung:**
1. **Signierung** einrichten (Keystore außerhalb des Repositories; Release-Signaturen sind an den Paketnamen gebunden, eine Debug-Signatur eignet sich nicht für die Verteilung).
2. **Funktionstest des signierten Release-Builds** auf dem CT37 (Reader verbinden, QCC-Scan, Duplicate Finder, CSV-Export), da Minifizierung Hilt/Room/SDK-Aufrufe beeinflussen kann.
3. Verteilungsweg festlegen (Seite 13, O-04).

> **Parallel-Test des Release-Builds:** Eine Release-Variante mit Debug-Signatur und `applicationIdSuffix ".rel"` lässt sich lokal parallel zur Debug-App installieren (z. B. per Gradle-Init-Skript, ohne das Repository zu ändern). Dabei Reader und Scanner nur von **einer** App gleichzeitig nutzen.

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
- **Release-Build** funktioniert (R8-Regeln für die Vendor-SDKs).
- Manuelle GTIN-Eingabe (ohne UI) sowie 24 ungenutzte Strings, die Permissions `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` und die Routen `scan`/`export` entfernt.
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
- Release-Build ist unsigniert; Funktionstest des signierten Release-Builds steht aus (siehe Abschnitt 2).
- Test mit **zwei gleichzeitig gehaltenen Duplikaten** steht aus.
- Sendeleistung im Reader-Screen nur angezeigt („dBm“-Beschriftung irreführend); Polnisch nicht in der Sprachauswahl (Seite 13).

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
