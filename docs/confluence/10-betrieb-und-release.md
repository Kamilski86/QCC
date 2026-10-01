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
| `release` | ✅ baut, ✅ **signiert** (seit 2026-10-01) | R8 mit `proguard-rules.pro`; Ausgabe `app/build/outputs/apk/release/app-release.apk` (ca. 12 MB, Signatur v2 verifiziert). Ohne `keystore.properties` entsteht `app-release-unsigned.apk`. Start auf dem CT37 geprüft; **Funktionstest mit Reader/Scan/Export steht aus** |

**Release-Build-Konfiguration (erledigt 2026-10-01):** Ursache des früheren Fehlers war ein von Zebras SDK referenziertes, nicht öffentliches Android-Klassenobjekt (`android.os.ServiceManager`). `app/proguard-rules.pro` enthält nun
1. `-dontwarn android.os.ServiceManager`,
2. `-keep class com.zebra.** { *; }` und `-keep class com.honeywell.** { *; }` – die Zebra-AARs liefern leere Consumer-Rules, Honeywell bündelt JNI-Bibliotheken (`libhedc.so`), die an Klassennamen gebunden sind; ohne Keep benennt R8 z. B. `RFIDReader` in `a6.u` um,
3. `-dontwarn` für nicht genutzte Zebra-Transporte (`com.jcraft.jsch`, `fi.iki.elonen`, `org.apache.xerces`, `org.bouncycastle`, `org.java_websocket`).

**Noch erforderlich vor Auslieferung:**
1. ~~Signierung einrichten~~ – **erledigt**, siehe Abschnitt 4.
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

## 4 Signierung des Release-Builds

| Aspekt | Wert |
|---|---|
| Keystore | `~/.qcc-signing/qcc-release.jks` (PKCS12, RSA 4096, Alias `qcc`, gültig ca. 27 Jahre), **außerhalb des Repositories** |
| Zertifikat | `CN=QCC, C=DE`; SHA-256-Fingerabdruck `15:47:C7:96:86:FF:6F:98:0D:27:7B:96:0F:5A:47:84:06:C5:F7:D4:35:33:3E:13:3E:CE:E1:B2:01:E3:A5:28` |
| Zugangsdaten | `keystore.properties` im Repo-Stamm (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); **git-ignoriert** (`.gitignore`: `keystore.properties`, `*.jks`, `*.keystore`) |
| Build-Logik | `app/build.gradle.kts` liest `keystore.properties`; ist die Datei vorhanden, wird `release` signiert, sonst entsteht ein unsigniertes APK (Checkouts ohne Secrets bauen weiter) |
| Signaturschema | APK Signature Scheme v2 (minSdk 28) |

**Befehle**
```bash
./gradlew assembleRelease                                      # → app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
shasum -a 256 app/build/outputs/apk/release/app-release.apk    # Prüfsumme für Confluence/Verteilung
```

**Wichtig – Schlüsselverwaltung**
- Die Signatur ist die **Identität der App**: Updates lassen sich nur installieren, wenn sie mit **demselben** Schlüssel signiert sind. **Geht der Keystore oder das Passwort verloren, kann keine installierte App mehr aktualisiert werden** (nur Deinstallation, dabei gehen die lokalen Daten verloren).
- **Backup:** Keystore-Datei **und** Passwort (steht in `keystore.properties`) sicher und getrennt vom Repository ablegen (Passwortmanager/Tresor des Unternehmens). Nicht in Confluence, Git oder E-Mail.
- Das Passwort wurde zufällig erzeugt; für eine Unternehmensumgebung kann der Schlüssel vom Betrieb/Security neu erzeugt werden (dann einmalig neu installieren, da der Schlüssel wechselt).
- **Debug- und Release-Build haben verschiedene Signaturen:** Ein Release-APK lässt sich **nicht** über eine installierte Debug-App installieren (und umgekehrt) – vorher deinstallieren (Datenverlust) oder auf einem anderen Gerät testen.

### APK exportieren und in Confluence ablegen
1. `./gradlew assembleRelease`, Prüfsumme bilden, Datei mit Version benennen (z. B. `QCC-0.1.0-release.apk` + `QCC-0.1.0-release.apk.sha256`; lokal in `dist/`, git-ignoriert).
2. In Confluence als **Seitenanhang** an einer zugriffsbeschränkten Seite hochladen (Dateimakro/Anhänge); Version, Datum, Commit und SHA-256 auf der Seite vermerken.
3. Erst nach Klärung der SDK-Lizenzen (Seite 13, O-08) breiter verteilen; für den Rollout ist MDM sauberer als der Download aus Confluence.
4. Installation am Gerät: „Unbekannte Apps installieren“ erlauben oder per MDM/`adb install`.

## 5 Versionierung

| Aspekt | Ist |
|---|---|
| `versionName` | `0.1.0` (manuell in `app/build.gradle.kts`; zusätzlich **hart codiert** im About-Screen) |
| `versionCode` | `1` (nicht erhöht) |
| Git-Tags | keine (nur ein Sicherungs-Tag `backup-pre-restore-20260420-152031`) |
| DB-Schema | 8 (unabhängig von der App-Version) |

**Empfehlung:** Semantische Versionen; `versionCode` bei jedem ausgelieferten Build erhöhen; Git-Tag `vX.Y.Z` pro Release; About-Screen aus `BuildConfig.VERSION_NAME` speisen (`buildConfig = true` ist bereits aktiviert).

## 6 Release-Prozess (Empfehlung)

1. Branch `main` stabil, alle Tests grün (`./gradlew test`).
2. Version erhöhen, Änderungshistorie (Seite 15) und Release Notes ergänzen.
3. Release-Build mit R8 erzeugen (signiert über `keystore.properties`), auf CT37 prüfen (Hardware-Abnahme Seite 09, H-01…H-10).
4. Git-Tag setzen, Artefakt archivieren (APK + `mapping.txt`).
5. Verteilung (MDM/Store/manuell) **[zu bestätigen]**; Anwender informieren (Schulung bei Verhaltensänderung, z. B. Wegfall der Scan-Buttons).

## 7 Release Notes

### Version 0.1.0 (Stand 2026-10-01, Branch `fix/duplicate-finder-ux`, noch nicht in `main` gemergt)

**Neu**
- **Duplikatsuche („Find duplicates“)**: Ziel per Anlesen, EPC oder SGTIN (Filterwert 0–7 wählbar); Ortung mit Tacho, Beep und Vibration; TID-Erfassung per „Confirm find“; Dauersuche; CSV-Export bei „End search“; offene Suche wird fortgesetzt.
- **Wiederfunde**: Tags, die in einer früheren Suche gefunden wurden und erneut auftauchen, zählen erneut und tragen das Datum des Vorfunds (Dialog und CSV-Spalte „Previously Found“).
- **Dashboard**: Button „Find duplicates“.
- Popups (Dialog mit OK) für Warnungen und Fehler im Duplicate Finder.

**Geändert**
- **Release-Build** funktioniert (R8-Regeln für die Vendor-SDKs) und wird **signiert** (Keystore außerhalb des Repositories).
- Vergleich über **GTIN-14 + Seriennummer** statt über den Punkt-String (unabhängig von der Company-Prefix-Länge).
- Reader-Screen: Sendeleistung als „Level n of 30“ mit Hinweistext statt irreführend „dBm“.
- **Polnische Übersetzung entfernt** (App-Sprachen: EN, DE).
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
- Funktionstest des signierten Release-Builds (Reader, Scan, Export) steht aus (siehe Abschnitt 2).
- Test mit **zwei gleichzeitig gehaltenen Duplikaten** steht aus.

## 8 Betrieb und Support

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
