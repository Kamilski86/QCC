# 11 Sicherheit und Datenschutz

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Art der Seite** | Bestandsaufnahme aus Code und Manifest (keine formale Sicherheitsprüfung, kein Pen-Test) |
| **Quellen** | `AndroidManifest.xml`, `res/xml/*`, Code, `build.gradle.kts`, `proguard-rules.pro`, Rollenleitfaden `agents/8_Senior-Security-Specialist.md` |
| **Kennzeichnung** | **[Beobachtung]** = aus dem Code belegt · **[Empfehlung]** = Vorschlag · **[zu bestätigen]** = Entscheidung/Information außerhalb des Repositories |

## 1 Schutzobjekte und Datenklassifikation

| Daten | Inhalt | Speicherort | Einordnung |
|---|---|---|---|
| Scan-Historie | GTIN/SGTIN (QR und Tag), EPC, QR-Rohstring, Zeitstempel, Match-Status | `qcc.db` (`scans`, `mismatches`) | Betriebsdaten, **keine** personenbezogenen Felder erkennbar |
| Duplikatsuchen | Ziel-SGTIN/EPC, Filiale (Freitext), TIDs, Zeitstempel | `qcc.db`, `SharedPreferences`, CSV | Betriebsdaten; „Store“ ist Freitext (Anwender könnte Personenbezug eintragen) |
| CSV-Exporte | wie oben | öffentlicher Ordner *Documents* | **für andere Apps/Nutzer mit Speicherzugriff lesbar** |
| Gerätezustand | Reader-Name/Status | Speicher, Logcat | gering |

**Personenbezug:** Die App hat **keine Anmeldung, kein Benutzerkonto und erfasst keine Mitarbeiterkennung**; es werden keine Personendaten gezielt verarbeitet. Eine datenschutzrechtliche Bewertung durch die zuständige Stelle steht aus **[zu bestätigen]**. Ein Login-Feature existiert nicht (die früher vorhandenen, ungenutzten Strings `user_id`, `password`, `login` wurden am 2026-10-01 entfernt).

Vertraulichkeit/Integrität/Verfügbarkeit: Schutzbedarf **[zu bestätigen]**. Annahme für die Bewertung unten: normaler Betriebsdatenschutz, keine Geheimhaltungsstufe.

## 2 Berechtigungen

| Permission | Zweck | Genutzt? | Bewertung |
|---|---|---|---|
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | Zebra-Reader finden/verbinden (Laufzeit-Anfrage ab Android 12) | ja | erforderlich (im App-Manifest deklariert); der IH45 per USB braucht sie nicht |
| `ACCESS_FINE_LOCATION` (`maxSdkVersion="30"`) | Bluetooth-Suche der Diagnose-Ansicht auf Android 10/11 | nur in der temporären Diagnose | **seit 2026-10-01 im App-Manifest deklariert**; entfällt mit der Diagnose-Ansicht **[zu bestätigen]** (die Permission steht ohnehin aus den SDK-Manifesten im APK, siehe nächste Zeile) |
| `VIBRATE` | Haptik | ja | erforderlich |
| ~~`POST_NOTIFICATIONS`~~ | – | – | **entfernt am 2026-10-01** (war ungenutzt) |
| ~~`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`~~ | – | – | **entfernt am 2026-10-01** (war ungenutzt) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `READ_EXTERNAL_STORAGE`, `com.honeywell.decode.permission.DECODE` | **nicht im App-Manifest**, sondern vom Manifest-Merge aus den **Vendor-SDKs** (Zebra/Honeywell) übernommen; das **Honeywell-RFID-SDK (IH45)** bringt `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` sowie die Services `com.honeywell.UpdateService.UpdateService` und `com.honeywell.rfidservice.RfidService` mit (Details der Manifest-Merge-Ausgabe prüfen **[zu bestätigen]**); sichtbar im fertigen APK (`aapt2 dump permissions`) | teils | **[Beobachtung]** Das APK fordert damit auch **Standort** und **Lesezugriff auf Speicher** an, obwohl die App beides selbst nicht benötigt. Die App fragt Standort/Speicher zur Laufzeit **nicht** ab (Laufzeit-Anfrage nur für Bluetooth ab Android 12). **[Empfehlung]** Per `tools:node="remove"` im App-Manifest entfernen, nachdem getestet wurde, dass die SDKs ohne sie funktionieren (auf Android 9–11 sind Standort-Rechte für die Bluetooth-Suche üblicherweise nötig) |
| `INTERNET` | – | nicht deklariert | App ist offline (gewollt); wird für EPCIS (geplant) nötig |
| Speicher (`WRITE_EXTERNAL_STORAGE`) | CSV auf API 28 | nicht deklariert | MediaStore ab API 29 ohne Berechtigung; API 28 würde scheitern |

## 3 Speicherung und Backup

- **[Beobachtung]** Die Datenbank liegt unverschlüsselt im privaten App-Speicher (Standard-Android-Sandbox; Schutz durch Gerätesperre/Dateiverschlüsselung des OS).
- **[Beobachtung]** `android:allowBackup="true"` mit `tools:replace`; die vorhandenen Dateien `backup_rules.xml`/`data_extraction_rules.xml` sind **leer** und im Manifest **nicht referenziert**. Folge: Android-Backup (z. B. Google-Backup) kann die Datenbank mitsichern. **[Empfehlung]** Backup bewusst entscheiden (deaktivieren oder per Regeln begrenzen), sobald klar ist, ob Scan-Daten das Gerät verlassen dürfen.
- **[Beobachtung]** CSV-Dateien liegen im gemeinsamen Ordner *Documents*; keine Verschlüsselung, keine automatische Löschung.
- **[Empfehlung]** Aufbewahrungs- und Löschkonzept festlegen (Seite 06, Abschnitt 6: derzeit unbegrenzt).

## 4 Kommunikation

- **Bluetooth** zum RFD8500: Kopplung und Verschlüsselung liegen bei Android bzw. dem Zebra-SDK; die App implementiert keine eigene Absicherung.
- **USB** zum IH45: serielle Verbindung (`/dev/ttyACM0`, 115200 Baud) über das Honeywell-SDK, ohne Authentifizierung/Verschlüsselung auf Anwendungsebene; Zugriff hat jede App mit Rechten auf das USB-Gerät bzw. das Honeywell-SDK **[zu bestätigen]**. Die vorinstallierte Honeywell-System-App `com.honeywell.datacollection.rfid` kann den Reader parallel öffnen.
- **Kein Netzwerkverkehr.** Für die geplante EPCIS-Anbindung wären TLS, Authentifizierung und Secret-Handling zu spezifizieren (siehe Rollenleitfaden Security/API).

## 5 Angriffsfläche

| Aspekt | Befund |
|---|---|
| Exportierte Komponenten | im App-Manifest nur `MainActivity` (`exported=true`) mit `MAIN/LAUNCHER`; keine Provider/Receiver/Services; keine Deep-Links. Das Honeywell-RFID-SDK ergänzt per Manifest-Merge die Services `com.honeywell.UpdateService.UpdateService` und `com.honeywell.rfidservice.RfidService`; deren `exported`-Status ist zu prüfen **[zu bestätigen]** |
| Eingaben | QR-/EPC-/SGTIN-Eingaben werden strikt validiert (Regex, Längen, Header, Wertebereiche); Fehler → `IllegalArgumentException` mit lesbarem Text |
| SQL | Room mit parametrisierten Queries; keine String-Konkatenation von Eingaben |
| CSV | Felder werden RFC-4180-konform escaped (`csvEscape`); **[Beobachtung]** keine Neutralisierung von Formelzeichen (`=`, `+`, `-`, `@`) beim Öffnen in Tabellenkalkulation (CSV-Injection) – relevant nur, wenn QR-Rohdaten von Dritten stammen |
| Logging | Logcat enthält technische Ereignisse (Moduswechsel, SDK-Fehler), **keine** Scan-Inhalte; Reader-Fehlertexte können Gerätedetails enthalten |
| Release-Härtung | R8-Minifizierung aktiv und baubar (Seite 10); die Vendor-SDK-Pakete (Zebra, Honeywell inkl. IH45-SDK-Pakete und Ein-Buchstaben-Pakete `a.**`–`u.**`) sind bewusst **von der Obfuskation ausgenommen**; Release-Build wird signiert, wenn `keystore.properties` vorhanden ist (Seite 10); kein Debuggable-Flag explizit gesetzt (Standard: Release nicht debuggable) |

## 6 Lieferkette

- **Vendor-SDKs** (Honeywell, Zebra) liegen als **lokale AARs** im Repository; keine Prüfsummen/Herkunftsnachweise im Repo. Das Honeywell-RFID-SDK (`vendor/honeywell/honeywell_rfid_sdk_v01.01.03.04.11.aar`) ist von Honeywell signiert; die **Lizenz zur Weitergabe wurde vom Product Owner freigegeben**. Für das Zebra-SDK und die Honeywell-AIDC-AARs sind keine Lizenzhinweise dokumentiert **[zu bestätigen]**.
- Maven-Abhängigkeiten sind versioniert festgelegt (Google, Maven Central); Dependency-Scanning ist nicht eingerichtet **[Empfehlung]**.
- Zebra-`vendor`-Verzeichnis enthält Beispiel-Apps (per `.gitignore` ausgeschlossen).

## 7 Risiken und Maßnahmen

| ID | Risiko | Wahrscheinlichkeit | Auswirkung | Maßnahme |
|---|---|---|---|---|
| S-01 | Scan-/Fund-Daten gelangen über Backup oder öffentlichen Ordner an Dritte | mittel | mittel | Backup-Regeln festlegen; Export-Ablage bewerten (z. B. app-spezifischer Ordner/Share-Intent) |
| S-02 | ~~Überflüssige Berechtigungen~~ | – | – | **erledigt** (2026-10-01): `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` entfernt |
| S-03 | CSV-Injection beim Öffnen exportierter Dateien | niedrig | niedrig–mittel | Formelzeichen am Feldanfang neutralisieren, falls Dritte QR-Inhalte beeinflussen können |
| S-04 | Manipulierte/ersetzte Vendor-AARs | niedrig | hoch | Prüfsummen dokumentieren, Bezug aus offiziellen Quellen belegen (das Honeywell-RFID-AAR trägt eine Honeywell-Signatur) |
| S-08 | Zusätzliche Services/Berechtigungen aus dem Honeywell-RFID-SDK (Manifest-Merge) vergrößern die Angriffsfläche | niedrig | mittel | `exported`-Status prüfen, ggf. per `tools:node` einschränken, wenn funktional möglich |
| S-05 | Unbegrenzte Aufbewahrung von Betriebsdaten | mittel | niedrig–mittel | Aufbewahrungs-/Löschkonzept |
| S-06 | Betrieb auf Android 9–11 ungeprüft: die Legacy-/Standort-Berechtigungen stehen zwar im APK (aus den SDKs), die App fragt sie dort aber nicht zur Laufzeit ab | niedrig | mittel | Zielgeräte-Matrix festlegen; sonst `minSdk` anheben oder Laufzeit-Abfrage ergänzen |
| S-07 | Release-Keystore geht verloren oder wird offengelegt | niedrig | hoch | Backup von Keystore **und** Passwort getrennt vom Repository (Passwortmanager); bei Verlust können installierte Apps nicht mehr aktualisiert werden, bei Offenlegung kann Dritten ein gültig signiertes APK gelingen (siehe Seite 10, Abschnitt 4) |

## 8 Offene Fragen für Security/Datenschutz

1. Schutzbedarf der Scan- und Funddaten? Interne Einstufung?
2. Datenschutzrechtliche Bewertung (Freitextfeld „Store“, Gerätezuordnung)?
3. Zulässigkeit von Android-Backup und öffentlichem Export-Ordner?
4. Lizenz- und Nutzungsbedingungen der Vendor-SDKs für den Betrieb und die Weitergabe?
5. Anforderungen an Signierung, MDM und App-Distribution?
6. Sind die per Manifest-Merge ergänzten Honeywell-Services (`RfidService`, `UpdateService`) unbedenklich, und genügt die USB-Anbindung ohne Anwendungsschutz?
