# 12 Architekturentscheidungen (ADR)

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Format** | Kontext · Entscheidung · Konsequenzen · Status (*Aktiv*, *Ersetzt*, *Offen*) |
| **Quellen** | `CLAUDE.md`, `AGENTS.md`, Design-Spec, Git-Historie, Code |

| ID | Titel | Status |
|---|---|---|
| ADR-01 | MVVM mit StateFlow | Aktiv |
| ADR-02 | Hardwarezugriff über Gateway-Interfaces | Aktiv |
| ADR-03 | Zebra im QCC-Modus: Einzel-Tag pro Session | Aktiv |
| ADR-04 | NavHost-weite ViewModels (QCC) vs. Destination-ViewModels (neu) | Aktiv, Schuld |
| ADR-05 | Fachlogik (GS1) als pure-Kotlin-Service | Aktiv |
| ADR-06 | Room mit exportiertem Schema und expliziten Migrationen | Aktiv |
| ADR-07 | Duplicate Finder: Erfassung über TID | Aktiv |
| ADR-08 | Ortung und TID-Prüfung immer exakt; SGTIN mit explizitem Filterwert | Aktiv |
| ADR-09 | Nahbereichs-Lesen mit 5 % Leistung | Aktiv |
| ADR-10 | Trigger-Routing im Gateway nach `TriggerMode` | Aktiv |
| ADR-11 | Trigger-Besitz (Arbiter) und QCC-Pause | Aktiv |
| ADR-12 | TID-Eindeutigkeit je Suche statt global | Aktiv (ersetzt Teil von ADR-07) |
| ADR-13 | Typisierte Meldungen, Dialog für Warnungen/Fehler, Snackbar für Erfolg | Aktiv |
| ADR-14 | Suchmaske als feste Spalte mit flexiblem Gauge | Aktiv |
| ADR-15 | Ton/Vibration über das CT37 | Aktiv |
| ADR-16 | CSV-Export über MediaStore in *Documents* | Aktiv |
| ADR-17 | Vendor-SDKs als lokale AARs | Aktiv, Schuld |
| ADR-18 | Englisch als Standardsprache, zwei Ressourcen-Sets (EN, DE) | Aktiv |
| ADR-19 | QCC-Vergleich über GTIN-14 + Seriennummer | Aktiv |
| ADR-20 | IH45 als zweiter RFID-Reader – USB, Umschaltung, volle Leistung + RSSI-Auswahl, Leistungsstufen in der Ortung | Aktiv |
| ADR-21 | Trigger-gesteuerte Bestätigung in der Duplikatsuche | Aktiv |

---

### ADR-01 MVVM mit StateFlow
- **Kontext:** Compose-UI, Hardware-Ereignisse asynchron.
- **Entscheidung:** ViewModels halten Zustand in `StateFlow`; UI beobachtet und sendet Events zurück.
- **Konsequenzen:** gut testbar (ViewModel-Tests mit Fakes); Zustand überlebt Rekonfiguration.

### ADR-02 Hardwarezugriff über Gateway-Interfaces
- **Kontext:** Vendor-SDKs sind schwer testbar und ändern sich.
- **Entscheidung:** `BarcodeScannerGateway`, `RfidReaderGateway`; Standardimplementierungen im Interface; Hilt bindet `@Singleton`-Implementierungen. Seit 2026-10-01 bindet `GatewayModule` an `RfidReaderGateway` das `SwitchingRfidReaderGateway`, das zwischen `ZebraRfd8500Gateway` und `HoneywellIh45Gateway` umschaltet (ADR-20).
- **Konsequenzen:** Fakes für Tests; kein SDK-Aufruf im UI. Nachteil: Gateways selbst sind ungetestet.

### ADR-03 Zebra im QCC-Modus: Einzel-Tag pro Session
- **Kontext:** QCC prüft einen Artikel nach dem anderen.
- **Entscheidung:** `AtomicBoolean inventoryActive`; genau ein EPC je Inventory-Session, danach sofort stoppen.
- **Konsequenzen:** einfache, eindeutige Zuordnung; Mehr-Tag-Inventur existiert nicht (nötig für „Inbound Read“). Der IH45 liefert im QCC-Modus ebenfalls genau ein Tag je Trigger-Druck, wählt es aber per RSSI aus (ADR-20).

### ADR-04 ViewModel-Scopes
- **Kontext:** Schnelle Einführung des QCC-Dashboards.
- **Entscheidung (historisch):** `ScanViewModel`, `DashboardViewModel`, `ExportViewModel` auf NavHost-Ebene; **neue** Screens (Duplicate Finder) destination-scoped.
- **Konsequenzen:** QCC-ViewModels leben so lange wie die Activity und lauschen dauerhaft auf Scanner-Ereignisse → Pausierung nötig (ADR-11). Ziel: alle Use-Case-Screens destination-scoped (Roadmap „Home mit Tiles“).

### ADR-05 GS1-Parsing als pure-Kotlin-Service
- **Entscheidung:** `Gs1ParserService` ohne Android-Abhängigkeiten.
- **Konsequenzen:** schnell testbar (JVM); der QR→SGTIN-Aufbau nimmt einen 7-stelligen Company Prefix an – nur noch für Anzeige/Persistenz relevant (siehe ADR-19).

### ADR-06 Room mit Schemaexport und Migrationen
- **Kontext:** Daten sind Audit-Grundlage und dürfen bei Updates nicht verloren gehen.
- **Entscheidung:** `exportSchema = true`, jede Änderung mit Migration und `MigrationTest`; Recreate-Table-Muster wegen fehlendem `DROP COLUMN` (API < 35); **keine** destruktive Fallback-Migration.
- **Konsequenzen:** verlustfreie Updates; kein Downgrade.

### ADR-07 Duplicate Finder: Erfassung über TID
- **Kontext:** Duplikate senden denselben EPC; nur die TID unterscheidet Chips.
- **Entscheidung:** Jeder Fund wird über die gelesene TID (96 Bit) erfasst; je Bestätigung genau ein Tag; gefundene Artikel werden physisch entfernt.
- **Konsequenzen:** belegbare Einzelfunde; Anwender muss Artikel isolieren (Fehlerfälle „0/mehrere Tags“).

### ADR-08 Exakte Ortung, SGTIN mit explizitem Filterwert
- **Kontext:** Hardware-Spike: die RFD8500 akzeptiert nur MSB-lückenlose Masken. Ursprünglich geplant war, die Filterbits auszumaskieren.
- **Entscheidung:** immer volle 96-Bit-Maske; getippte SGTIN → EPC mit Filterwert (Standard 1, wählbar 0–7); Hex-EPC bringt den Filterwert mit.
- **Konsequenzen:** Tippfehler beim Filterwert führen zu 0 %; daher Anlesen als empfohlener Weg.

### ADR-09 Nahbereichs-Lesen mit 5 % Leistung
- **Entscheidung:** 5 % des Leistungsindex (min. Index 15), Lesefenster 700 ms; danach Anwender-Leistung wiederherstellen.
- **Konsequenzen:** nur Tags direkt an der Pistole; am Einzelfall kalibriert – bei Metall/Flüssigkeit ggf. Nachjustierung (Risiko R-05). Gilt für den **Zebra**; beim IH45 ist eine niedrige Sendeleistung zur Reichweitenbegrenzung nicht brauchbar (ADR-20).

### ADR-10 Trigger-Routing im Gateway
- **Kontext:** `ScanViewModel` lauscht Activity-weit auf den Trigger.
- **Entscheidung:** `TriggerMode` (`QCC`, `CAPTURE`, `LOCATE`) im Gateway; `observeTriggerEvents()` nur im Modus QCC.
- **Konsequenzen:** `ScanViewModel` blieb unverändert; Modus ist aber globaler Zustand (→ ADR-11).

### ADR-11 Trigger-Besitz und QCC-Pause
- **Kontext:** Nach Wechsel Duplicates → Dashboard funktionierte der Scan „mal so, mal so“: mehrere unkoordinierte Schreiber auf `triggerMode` (spät abgebauter Screen überschrieb den Modus), Locate-Starts nach dem Verlassen, QCC reagierte im Hintergrund auf Scanner-Ereignisse.
- **Entscheidung:** `TriggerModeArbiter` – nur der zuletzt belegende Besitzer kann an `QCC` zurückgeben (`claimTriggerMode/releaseTriggerMode`); `onScreenStopped` idempotent; `startLocating` nur bei `screenActive`; `ScanViewModel.setSuspended` auf der Duplicates-Route; Logging unter `QccZebra`.
- **Konsequenzen:** Isolation zwischen Use Cases (Anforderung FR-21); nachgewiesen durch Tests und Anwender-Rückmeldung. Kein Beweis für die letzte Ursache auf Hardwareebene (Log-Analyse nur bei Wiederauftreten).

### ADR-12 TID-Eindeutigkeit je Suche
- **Kontext:** Ursprünglich war die TID global eindeutig (ein Fund je physischem Tag). Fachlich kann ein Tag nach dem Fund erneut auf die Fläche gelangen.
- **Entscheidung:** `UNIQUE(searchId, tid)`; Wiederfund aus früherer Suche zählt erneut und speichert `previousFoundAt` (Datum des letzten Vorfunds); gleiche TID in derselben Suche zählt nicht erneut.
- **Konsequenzen:** Migration 7 → 8; CSV-Spalte „Previously Found“; Label „Found (unique TIDs)“ gilt je Suche.

### ADR-13 Meldungen: typisiert, Dialog vs. Snackbar
- **Kontext:** Meldungen standen oben in einer scrollbaren Liste und gingen unter; Layoutverschiebungen verhinderten die Ein-Bildschirm-Maske.
- **Entscheidung:** Das ViewModel liefert `DuplicateMessage`; alles außer reinem Erfolg (`Saved`, `Exported`, `NothingExported`) ist ein **modaler Dialog** mit OK (Trigger/Locate pausieren); Erfolg als **Snackbar** (überlagert, verschiebt nichts).
- **Konsequenzen:** Warnungen können nicht übersehen werden; Anwender muss quittieren (Mehraufwand bei Fehlerhäufung).

### ADR-14 Feste Spalte mit flexiblem Gauge
- **Entscheidung:** Suchphase als nicht scrollende `Column`; der Tacho nimmt per `weight(1f)` den Restplatz und skaliert Strich/Texte proportional; kompakte Abstände nur in dieser Phase.
- **Konsequenzen:** auf kleinen Displays verkleinert sich der Tacho (Test: ≥ 80 dp bei 360×600 dp mit gesetzter Filiale).

### ADR-15 Ton und Vibration über das CT37
- **Entscheidung:** `ToneGenerator` (`STREAM_NOTIFICATION`, Lautstärke 90) und `Vibrator` des CT37, nicht der Beeper der RFD8500.
- **Konsequenzen:** unabhängig vom Reader; Beep-Takt 1000 → 80 ms.

### ADR-16 CSV-Export über MediaStore
- **Entscheidung:** `CsvDocumentWriter`-Abstraktion; Implementierung schreibt nach *Documents* (MediaStore ab API 29).
- **Konsequenzen:** auswertbar ohne Zusatz-App; Dateien sind für andere Apps lesbar (Seite 11).

### ADR-17 Vendor-SDKs als lokale AARs
- **Kontext:** SDKs sind nicht auf Maven.
- **Entscheidung:** AARs im Repository (`app/libs/`, `vendor/zebra/`, seit 2026-10-01 `vendor/honeywell/`), per `fileTree`. Die Zebra-Bibliothek `API3_LLRP` wird ausgeschlossen (Klassenkollision `a.a` mit dem Honeywell-RFID-SDK, siehe ADR-20).
- **Konsequenzen:** reproduzierbar ohne Download, aber große Binärdateien im Repo, kein Prüfsummen-Nachweis, **R8-Konflikt im Release** (Seite 10).

### ADR-18 Sprachen
- **Entscheidung:** Englisch als Standard (`values/`), zusätzlich `values-de`; Sprachwahl über `AppCompatDelegate.setApplicationLocales`. Die polnische Übersetzung wurde am 2026-10-01 entfernt (sie war nicht wählbar).
- **Konsequenzen:** zwei Ressourcen-Sets; QCC-Klartexte teils noch deutsch (Seite 13).

### ADR-19 QCC-Vergleich über GTIN-14 + Seriennummer
- **Kontext:** Der QCC-Vergleich verglich den Punkt-String `GCP.ItemRef.Serial`. Die QR-Seite muss dafür die Länge des Company Prefix **annehmen** (7 Stellen), die EPC-Seite liest sie aus der Partition. Bei einem anderen Präfix hätten gleiche Artikel unterschiedliche Strings ergeben (falscher Mismatch). Das Unternehmen nutzt derzeit 7 Stellen.
- **Entscheidung:** Match = `qr.gtin == rfid.gtin` **und** gleiche Seriennummer (`trim`, `lowercase`). Beide Seiten liefern die GTIN-14 unabhängig von der Präfixlänge (EPC-Seite inkl. berechneter Prüfziffer).
- **Konsequenzen:** Risiko R-09 entfällt für den Vergleich. Die angezeigte „QR SGTIN“ und die Spalten `sgtinQr`/CSV „SGTIN“ unterstellen weiterhin 7 Stellen (nur Darstellung). Tests: `ScanViewModelTest` (Prefix-Länge, andere Serial/GTIN).

### ADR-20 IH45 als zweiter RFID-Reader – USB, Umschaltung, volle Leistung + RSSI-Auswahl, Leistungsstufen in der Ortung
- **Kontext:** Neben dem Zebra RFD8500 (Bluetooth) soll das CT37 mit dem **Honeywell IH45** arbeiten. Hardware-Befunde (2026-10-01, CT37 + IH45 + RFD8500):
  - Der IH45 funkt per BLE, lehnt aber die Kopplung über die Android-Einstellungen ab („Pairing rejected by IH45“). Per **USB** (`/dev/ttyACM0`, 115200 Baud, USB-IDs `0C2E:10EA`) lässt er sich mit dem Honeywell RFID SDK (`honeywell_rfid_sdk_v01.01.03.04.11.aar`) zuverlässig verbinden.
  - Eine niedrige Sendeleistung zur Reichweitenbegrenzung (Zebra: 5 % der Leistungstabelle, ADR-09) ist beim IH45 unbrauchbar: 5 dBm (Rohwert 500) liest nur bei Berührung, die Rohwerte 0 und 100 lesen nichts. Die Einheit des SDK ist vermutlich 1/100 dBm (verifiziert für 500…3000). Die Leistung ist nur setzbar, solange nicht gelesen wird (vorher `stopRead`).
  - Der RSSI ändert sich **nicht** mit der Sendeleistung; gehaltenes Tag liest im Feld −23…−43 dBm, entfernte Tags −56…−60 dBm. Im Nahbereich (0–20 cm) **sättigt** der RSSI (−23…−27 dBm): Berührung und 15–20 cm sind nicht unterscheidbar.
  - Im SDK-Triggermodus `BARCODE` kommen keine Trigger-Ereignisse und der CT37-Scanner reagiert nicht; nur im Modus `RFID` kommt `onRfidTriggered(pressed)`.
  - Die Zebra-Bibliothek `API3_LLRP` und das Honeywell-SDK enthalten beide eine obfuskierte Klasse `a.a` („Duplicate class a.a“).
- **Entscheidung:**
  1. **Anbindung per USB** mit `HoneywellIh45Gateway` (Interface `RfidReaderGateway`). Bluetooth-Anbindung ist **nicht umgesetzt** (offen, Seite 13). Das SDK bleibt im Triggermodus `RFID`; der QR-Scan wird wie beim Zebra von der App per Software ausgelöst.
  2. **Genau ein Reader gleichzeitig:** `SwitchingRfidReaderGateway` (statt `ZebraRfd8500Gateway` in `GatewayModule` an die Schnittstelle gebunden). Der IH45 hat Vorrang, solange er per USB steckt, sonst der Zebra. Beim Wechsel wird der andere Reader zuerst getrennt; alle Flows folgen dem aktiven Reader (`flatMapLatest`); An-/Abstecken (`usbPresent`) löst Neuwahl und Verbinden aus; der zuletzt gesetzte Leistungs-Level wird danach erneut gesetzt; Trigger-Modus und Besitzer-API werden an beide Reader weitergereicht.
  3. **Volle Leistung + RSSI-Auswahl:** Der IH45 läuft **immer mit voller Leistung (30 dBm)**; der Level-Regler (1–30) wirkt nur beim Zebra, `setPower` ist beim IH45 ein No-op. Die Reichweitenbegrenzung ersetzt die Auswahl nach RSSI: QCC (ab erstem Tag mit RSSI ≥ −50 dBm, `QCC_MIN_RSSI`, Sammelfenster 400 ms, stärkstes Tag; Loslassen beendet früher) sowie „Tag übernehmen“ und „Fund bestätigen“ (Fenster 700 ms; Tags mit RSSI ≥ −50 dBm, `NEARBY_MIN_RSSI`, und höchstens 5 dB, `NEARBY_MARGIN_DB`, unter dem stärksten; bei mehreren übrigen bleibt die Meldung „mehrere Tags“).
  4. **Ortung per RSSI des gesuchten EPC** (Maske wird ausgewertet: `(tag & mask) == (epc & mask)`) mit **Leistungsstufen** gegen die Nahfeld-Sättigung: Stufe 0 volle Leistung (Anzeige 0–80 % aus der RSSI-Skala −46 dBm = 0 % bis −27 dBm); Stufe 1 = 10 dBm (Rohwert 1000, Anzeige 90 %); Stufe 2 = 5 dBm (Rohwert 500, Anzeige 100 % = Berührung). Wechsel 0→1 bei Lesung mit RSSI ≥ −29 dBm und 300 ms Streak (Lücke bis 500 ms erlaubt), 1→2 bei 300 ms stetigem Lesen auf Stufe 1, zurück nach 2 s ohne Lesung (`LOSS_MS`), Wiederholsperre 4 s nach erfolglosem Versuch. Auf Stufe 1/2 wird nur die EPC gelesen (TID-Lesen braucht mehr Leistung als eine Inventur); nur Stufe 0 liest die TID. Nach Ende der Ortung wird die volle Leistung wiederhergestellt; endete die Ortung auf Stufe ≠ 0, wird die TID beim Bestätigen frisch gelesen (Zwischenspeicher der Treffer 3 s gültig).
  5. **Build:** `API3_LLRP*` wird aus den Zebra-AARs ausgeschlossen (LLRP ist nur für Netzwerk-Reader nötig); ProGuard-Keep-Regeln für die Honeywell-SDK-Pakete (JNI ist an Klassennamen gebunden).
- **Alternativen:**
  - *Niedrige Sendeleistung wie beim Zebra* – verworfen (siehe Kontext: Rohwert 500 liest nur bei Berührung, 0/100 lesen nichts).
  - *SDK-Triggermodus `BARCODE`* – verworfen (keine Trigger-Ereignisse, CT37-Scanner reagiert nicht).
  - *Bluetooth-Anbindung* – derzeit nicht möglich (Kopplung abgelehnt); als Backup-Wunsch offen.
  - *Beide Reader gleichzeitig verbinden* – verworfen zugunsten einer eindeutigen Zuordnung von Trigger und Tags; ein Umschalter ist einfacher zu testen.
  - *Ortung ohne Leistungsstufen (nur RSSI)* – im Nahbereich nicht brauchbar (Sättigung).
- **Konsequenzen:**
  - ViewModels sind unverändert (nur die Meldung „Kein RFID-Reader gefunden“); das IH45-Gateway selbst ist nicht unit-testbar (SDK) – Absicherung über `SwitchingRfidReaderGatewayTest`, `HoneywellIh45GatewayHelpersTest` (reine Hilfsfunktionen) und Hardware-Abnahme (Seite 09).
  - Jeder Leistungswechsel unterbricht das Lesen ca. 0,5–0,8 s; auf den schwachen Stufen liefert der IH45 nur wenige Lesungen pro Sekunde, daher wird die Anzeige alle 250 ms aufgefrischt.
  - Bekannte Grenze: der RSSI im Nahfeld schwankt je nach Tag-Lage um 3–5 dB; ungünstig liegende Tags erreichen evtl. nie die Stufe 2.
  - Die USB-Verbindung reißt gelegentlich ab (`USB detached`, Ursache unbekannt, Seite 13); das USB-Kabel belegt den USB-Port des CT37 (Entwicklung per WLAN-adb, Seite 08).
  - Das Release-Build mit den neuen Keep-Regeln baut, ist aber mit IH45 auf dem Gerät **noch nicht getestet**; der Regressionstest des RFD8500 nach dem Ausschluss von `API3_LLRP` steht aus.
  - Lizenz: Das Honeywell-SDK (signiert von Honeywell) liegt im Git; die Weitergabe-Lizenz wurde vom Product Owner freigegeben.

### ADR-21 Trigger-gesteuerte Bestätigung in der Duplikatsuche
- **Kontext:** Bei der Duplikatsuche braucht der Anwender eine Hand für den Artikel, eine für die Pistole – und eine dritte für „Confirm find“ auf dem Display.
- **Entscheidung (Product Owner, gilt für beide Reader):** Trigger **halten** = Ortung; Trigger **am Tag loslassen** (Nähe war innerhalb der letzten 600 ms (`AT_TAG_GRACE_MS`) ≥ 95 % (`CONFIRM_PROXIMITY`), beides in `DuplicateSearchViewModel`) bestätigt den Fund automatisch (wie „Confirm find“); weiter weg loslassen beendet nur die Ortung. Ein **Trigger-Druck schließt Dialoge** (wie OK) und startet dabei keine Ortung. Der Button „Confirm find“ bleibt als Alternative. `ProximityFeedback.resultTone(success)` liefert einen hellen Bestätigungston (`TONE_PROP_ACK`, 300 ms, 150 ms Vibration) bei gespeichertem Fund und einen Warnton (`TONE_PROP_NACK`) bei jeder anderen Meldung, da der Anwender beim Loslassen nicht aufs Display schaut.
- **Alternativen:** Bestätigung nur per Button (dritte Hand nötig); automatische Bestätigung bei Erreichen von 100 % ohne Loslassen (würde ohne Absicht des Anwenders registrieren).
- **Konsequenzen:** versehentliches Loslassen am Tag registriert einen Fund (durch die Regel „je Suche einmal“ und den Dialog „Already found“ abgefedert); vier zusätzliche ViewModel-Tests (Loslassen, Trigger schließt Dialog, Warnton). Die Schwellwerte 95 %/600 ms sind auf dem Gerät abgenommen; ob sie für weitere Tag-Typen/Umgebungen passen, ist **[zu bestätigen]**.
