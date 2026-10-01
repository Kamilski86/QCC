# 04 Benutzerhandbuch

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Zielgruppe** | Operator, Filialmitarbeiter, Schichtleiter |
| **Quellen** | Code, Design-Spec; Bezeichnungen entsprechen der englischen Standard-UI |

## 1 Voraussetzungen

- Honeywell **CT37** mit installierter QCC-App, **Bluetooth eingeschaltet**.
- Zebra **RFD8500** eingeschaltet, geladen und mit dem CT37 **gekoppelt** (Android-Bluetooth-Einstellungen).
- Beim ersten Start fragt Android nach den **Bluetooth-Berechtigungen** („Nearby devices“ / `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`). Ohne Freigabe kann der Reader nicht verbunden werden.

## 2 Start und Reader-Verbindung

1. App starten – nach dem kurzen Splash erscheint das **Dashboard**.
2. Die App sucht automatisch den ersten verfügbaren Zebra-Reader und verbindet sich.
3. Status prüfen: Menü (☰) → **Reader**. Dort steht „Verbunden“ bzw. der Fehlertext. Mit **Connect** wird erneut gesucht und verbunden (z. B. nach Ausschalten des Readers).

> Ohne verbundenen Reader reagiert der Trigger nicht, und im Duplicate Finder ist „Start search“ gesperrt.

## 3 Dashboard

| Element | Bedeutung |
|---|---|
| **Scans today** | Anzahl aller Vergleiche des heutigen Tages |
| **Mismatches today** | Anzahl der Abweichungen heute (rot, wenn > 0) |
| Karte **QR Raw / QR SGTIN / RFID SGTIN** | zuletzt gelesene Werte („-“ = noch nichts) |
| **Match** (grün) / **Mismatch** (rot) | Ergebnis des Vergleichs |
| **Reset** | verwirft QR-, RFID- und Ergebniswerte des laufenden Zyklus |
| **Export Mismatches (CSV)** | schreibt die komplette Scan-Historie nach `Documents/scans_<Datum>_<Zeit>.csv`; grüne Meldung bei Erfolg |
| **Find duplicates** | öffnet die Duplikatsuche |

## 4 Artikel prüfen (QCC-Vergleich)

1. Dashboard offen lassen.
2. Artikel mit **QR-Code** und **RFID-Tag** an die Pistole halten und den **Trigger drücken**. Dadurch werden QR-Scanner und RFID-Lesung gestartet. (Alternativ löst die Scan-Taste des CT37 nur den QR-Scan aus.)
3. Die gelesenen Werte erscheinen in der Karte; die Reihenfolge ist egal.
4. Sobald beide Werte vorliegen, vergleicht die App automatisch:
   - **Match** (grün, Bestätigungston/kurze Vibration): SGTIN von Code und Tag stimmen überein.
   - **Mismatch** (rot, Warnton/längere Vibration): Abweichung – oder der QR enthält keine Seriennummer.
5. Nächsten Artikel scannen: Die Anzeige wird automatisch geleert.

**Fehlermeldungen** erscheinen als roter Text im Dashboard, z. B. wenn der QR-Code nicht dem erwarteten Muster entspricht oder der EPC kein gültiger SGTIN-96 ist. Dann einfach erneut scannen.

## 5 Duplikate finden

### 5.1 Ziel festlegen (Phase 1)

Öffnen über **Find duplicates** im Dashboard (oder Menü ☰).

1. **Ziel vorgeben** – drei Möglichkeiten:
   - **Anlesen (empfohlen):** Einen Artikel mit dem gesuchten EPC direkt an die Pistole halten und den **Trigger drücken**. Der EPC wird übernommen.
   - **EPC tippen:** 24 Hex-Zeichen, z. B. `3034F8242800C10300000011`.
   - **SGTIN tippen:** Format `Company.ItemRef.Serial`, z. B. `4065546.092768.12884901894`. Darunter erscheint die Auswahl des **Filterwerts** (Standard **1** = Verkaufsartikel). Nur ändern, wenn bekannt ist, dass die Tags einen anderen Filterwert tragen.
2. Sobald die Eingabe gültig ist, zeigt die App **Target SGTIN** und **EPC** zur Kontrolle an. Es gibt keinen „Apply“-Button.
3. Optional **Store (optional)** eintragen (wird für die nächste Suche vorbelegt).
4. **Start search** drücken. Die Eingabe wird dabei nochmals geprüft; bei Fehler erscheint ein Dialog.

### 5.2 Suche (Phase 2)

Der Bildschirm zeigt – ohne Scrollen – von oben nach unten: **Target SGTIN, EPC, TID, Store**, den **Tacho** (Nähe in %), den Schalter **Continuous search**, den Zähler **Found (unique TIDs)** sowie die Buttons **Confirm find** und **End search**.

1. **Trigger halten** und den Reader über die Ware streichen. Je näher ein Tag mit dem gesuchten EPC ist, desto höher die Prozentzahl, desto schneller Beep und Vibration.
   - **Continuous search** (Schalter): Die Ortung läuft ohne Trigger. Der Trigger ist dann ohne Funktion.
2. Den vermuteten Artikel **entnehmen**, **direkt an die Pistole halten** und **Confirm find** tippen (nur ein Artikel gleichzeitig!). Die App liest die TID des Tags im Nahbereich.
3. Die **TID-Zeile** zeigt die zuletzt gelesene TID. Der Zähler **Found** steigt mit jedem neuen Fund.
4. Artikel **physisch entfernen** (z. B. Abschirmtasche); die App schaltet keine Tags stumm.
5. Weiter bei 1, bis alle gefunden sind.
6. **End search**: beendet die Suche, schreibt die **CSV** (`duplicates_<Datum>_<Zeit>_<Nr>.csv` im Ordner *Documents*) und zeigt den Dateinamen. Danach ist wieder Phase 1 aktiv.

> Die Suche **bleibt gespeichert**, wenn man den Bildschirm verlässt (z. B. zurück ins Dashboard): beim nächsten Öffnen geht es mit Ziel, Zähler und letzter TID dort weiter. Während man im Dashboard ist, läuft keine Ortung und der QCC-Vergleich funktioniert normal.

### 5.3 Meldungen im Duplicate Finder

Warnungen und Fehler erscheinen als **Dialog**, den man mit **OK** schließt. Solange er offen ist, ist der Trigger gesperrt.

| Titel / Meldung | Bedeutung | Was tun |
|---|---|---|
| **Found again** – „Counted as find #n. This tag was already found on <Datum>. TID: …“ | Der Tag wurde in einer **früheren** Suche schon einmal gefunden, ist aber wieder da (z. B. von jemandem zurückgelegt). Der Fund **zählt**. | Artikel entfernen; ggf. Ursache klären. Das Datum steht auch in der CSV. |
| **Already found** – „Already counted in this search as find #n (<Datum>). TID: …“ | Dieser Tag wurde in **dieser** Suche schon gezählt (z. B. doppelt bestätigt). Es wird **nicht** nochmals gezählt. | Prüfen, ob derselbe Artikel nochmals vorgelegt wurde. |
| No duplicate at the reader – wrong item? | Kein Tag mit dem gesuchten EPC im Nahbereich | Artikel direkt an die Pistole halten, ggf. falscher Artikel |
| Several duplicates detected – isolate the item | Mehrere passende Tags gleichzeitig | Artikel von anderen entfernen, einzeln prüfen |
| No tag detected – hold the item directly to the reader | Anlesen (Phase 1) ohne Treffer | Artikel direkt an die Pistole halten |
| Several tags detected – hold a single item to the reader | Anlesen mit mehreren Tags | nur einen Artikel vorhalten |
| Enter a 24-character EPC … | Eingabe ungültig | EPC/SGTIN korrigieren |
| Reader not connected | Reader nicht verbunden | im Menü **Reader → Connect** |
| Reader error: … | Fehler vom Reader (Text vom Gerät) | erneut versuchen, ggf. Reader neu starten |
| Export failed: … | CSV konnte nicht geschrieben werden | **End search** erneut tippen (Suche bleibt offen) |

Kurze Hinweise unten am Bildschirm (Snackbar): „Find #n saved – remove the item“, „Exported: <Datei>“, „No duplicates found – nothing exported“.

## 6 Weitere Einstellungen

- **Language:** English oder Deutsch (wirkt sofort).
- **About:** App-Name und Version.

## 7 Fehlerbehebung

| Symptom | Mögliche Ursache | Maßnahme |
|---|---|---|
| Trigger löst im Dashboard nichts aus | Reader nicht verbunden; Duplicate Finder noch offen und Modus nicht zurückgesetzt | Menü → Reader → Connect; zum Dashboard wechseln (Menü → Dashboard); bei Wiederholung Logcat (`adb logcat -s QccZebra`) sichern und melden |
| „Reader nicht verbunden“ / „Nicht verbunden“ | Reader aus, außer Reichweite, Bluetooth aus | Reader einschalten, Bluetooth prüfen, **Connect** |
| Keine Bluetooth-Verbindung trotz eingeschaltetem Reader | Berechtigung nicht erteilt | App-Einstellungen → Berechtigungen → „Nearby devices“ erlauben |
| Tacho bleibt bei 0 % | Trigger nicht gehalten bzw. Ziel-EPC weicht ab (Filterwert!) | Trigger halten; bei getippter SGTIN Filterwert prüfen (Standard 1); besser **anlesen** |
| Confirm find: „No duplicate at the reader“ | Artikel nicht direkt an der Pistole (Nahbereichs-Leistung ist gering) | Artikel direkt an die Pistole legen |
| CSV nicht auffindbar | Ablage im Ordner *Documents* des Geräts | Dateimanager → Documents |
| Mismatch trotz richtigem Artikel | QR ohne Seriennummer, anderer Company-Prefix, falscher Tag | QR-Rohwert in der Karte prüfen; Tag/Etikett kontrollieren |
