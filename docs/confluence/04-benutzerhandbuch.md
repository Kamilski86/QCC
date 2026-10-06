# 04 Benutzerhandbuch

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Zielgruppe** | Operator, Filialmitarbeiter, Schichtleiter |
| **Quellen** | Code, Design-Spec; Bezeichnungen entsprechen der englischen Standard-UI |

## 1 Voraussetzungen

- Honeywell **CT37** mit installierter QCC-App.
- **Ein** RFID-Reader – die App verbindet immer nur einen:
  - **Honeywell IH45 per USB-Kabel** am CT37 (hat **Vorrang**, solange er steckt). Keine Kopplung nötig; die App verbindet sich automatisch (nach dem Einstecken nach ca. 5 s, beim App-Start ca. 2 s). Die Bluetooth-Kopplung des IH45 über die Android-Einstellungen funktioniert nicht („Pairing rejected“) und wird nicht benötigt.
  - **oder Zebra RFD8500** eingeschaltet, geladen und mit dem CT37 **gekoppelt** (Android-Bluetooth-Einstellungen); dafür muss **Bluetooth eingeschaltet** sein.
- Beim ersten Start fragt Android nach den **Bluetooth-Berechtigungen** („Nearby devices“ / `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`). Ohne Freigabe kann der Zebra-Reader nicht verbunden werden (der IH45 per USB braucht sie nicht).

## 2 Start und Reader-Verbindung

1. App starten – nach dem kurzen Splash erscheint das **Dashboard**.
2. Die App sucht automatisch einen RFID-Reader und verbindet sich: zuerst den **IH45 (USB)**, sonst den ersten verfügbaren **Zebra-Reader (Bluetooth)**.
3. **Verbindungsanzeige:** Solange ein Reader verbunden ist, zeigt jeder Bildschirm (außer dem Splash) oben rechts ein **grünes Antennen-Icon**. Beim Verbinden erscheint kurz ein Hinweis „<Name> verbunden“ („connected“), beim Trennen „RFID-Reader getrennt“ („RFID reader disconnected“). Beim App-Start mit schon verbundenem Reader erscheint kein Hinweis.
4. Genaueren Status prüfen: Menü (☰) → **Reader**. Dort steht „Verbunden“ bzw. der Fehlertext. Mit **Connect** wird erneut gesucht und verbunden (z. B. nach Ausschalten des Readers oder erneutem Einstecken des USB-Kabels).
5. **Reader wechseln:** Wird der IH45 eingesteckt, schaltet die App automatisch vom Zebra auf den IH45 um; wird das USB-Kabel gezogen, wieder auf den Zebra (sofern eingeschaltet).

> Die **Reichweite** unterscheidet sich je nach Reader: Der Zebra liest mit stark reduzierter Leistung nur Tags direkt an der Pistole. Der IH45 läuft immer mit voller Leistung und nimmt von mehreren Tags das **stärkste** (das Tag am Reader) – ein Tag in der anderen Hand ist meist schwächer und wird ignoriert. Der Level-Regler im Reader-Screen wirkt nur beim Zebra.

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
   - **Anlesen (empfohlen):** Einen Artikel mit dem gesuchten EPC direkt an die Pistole halten und den **Trigger drücken**. Der EPC wird übernommen. (Beim IH45 zählt das Tag mit dem stärksten Signal; liegt ein zweites Tag fast gleich stark am Reader, erscheint „Several tags detected“.)
   - **EPC tippen:** 24 Hex-Zeichen, z. B. `3034F8242800C10300000011`.
   - **SGTIN tippen:** Format `Company.ItemRef.Serial`, z. B. `4065546.092768.12884901894`. Darunter erscheint die Auswahl des **Filterwerts** (Standard **1** = Verkaufsartikel). Nur ändern, wenn bekannt ist, dass die Tags einen anderen Filterwert tragen.
2. Sobald die Eingabe gültig ist, zeigt die App **Target SGTIN** und **EPC** zur Kontrolle an. Es gibt keinen „Apply“-Button.
3. Optional **Store (optional)** eintragen (wird für die nächste Suche vorbelegt).
4. **Start search** drücken. Die Eingabe wird dabei nochmals geprüft; bei Fehler erscheint ein Dialog.

### 5.2 Suche (Phase 2)

Der Bildschirm zeigt – ohne Scrollen – von oben nach unten: **Target SGTIN, EPC, TID, Store**, den **Tacho** (Nähe in %), den Schalter **Continuous search**, den Zähler **Found (unique TIDs)** sowie die Buttons **Confirm find** und **End search**.

1. **Trigger halten** und den Reader über die Ware streichen. Je näher ein Tag mit dem gesuchten EPC ist, desto höher die Prozentzahl, desto schneller Beep und Vibration. Die Hinweiszeile lautet „Trigger halten, zum Tag führen, am Tag loslassen = bestätigen“.
   - **Continuous search** (Schalter): Die Ortung läuft ohne Trigger. Der Trigger ist dann ohne Funktion für die Ortung.
   - **IH45:** Im Nahbereich schaltet der Reader die Leistung stufenweise herunter (kurze Unterbrechungen der Anzeige von ca. 0,5–0,8 s sind normal); **100 %** bedeutet: das Tag liegt am Reader (Berührung). Die Anzeige wird auf den schwachen Stufen etwas seltener aufgefrischt.
2. Den vermuteten Artikel **entnehmen**, **direkt an die Pistole halten** (nur ein Artikel gleichzeitig!) und den **Trigger am Tag loslassen** – das bestätigt den Fund automatisch (Voraussetzung: die Nähe war kurz zuvor ≥ 95 %). Alternativ **Confirm find** tippen. Die App liest die TID des Tags im Nahbereich. Wird der Trigger weiter weg losgelassen, endet nur die Ortung.
   - **Ton:** ein **heller Bestätigungston** mit kurzer Vibration = Fund gespeichert; ein **Warnton** = jede andere Meldung (z. B. „No duplicate at the reader“, „Already found“) – dann auf das Display schauen.
3. Die **TID-Zeile** zeigt die zuletzt gelesene TID. Der Zähler **Found** steigt mit jedem neuen Fund.
4. Artikel **physisch entfernen** (z. B. Abschirmtasche); die App schaltet keine Tags stumm.
5. Weiter bei 1, bis alle gefunden sind.
6. **End search**: beendet die Suche, schreibt die **CSV** (`duplicates_<Datum>_<Zeit>_<Nr>.csv` im Ordner *Documents*) und zeigt den Dateinamen. Danach ist wieder Phase 1 aktiv.

> Die Suche **bleibt gespeichert**, wenn man den Bildschirm verlässt (z. B. zurück ins Dashboard): beim nächsten Öffnen geht es mit Ziel, Zähler und letzter TID dort weiter. Während man im Dashboard ist, läuft keine Ortung und der QCC-Vergleich funktioniert normal.

### 5.3 Meldungen im Duplicate Finder

Warnungen und Fehler erscheinen als **Dialog**, den man mit **OK** **oder mit einem Trigger-Druck** schließt (dabei startet keine Ortung). Solange er offen ist, startet der Trigger keine Ortung.

| Titel / Meldung | Bedeutung | Was tun |
|---|---|---|
| **Found again** – „Counted as find #n. This tag was already found on <Datum>. TID: …“ | Der Tag wurde in einer **früheren** Suche schon einmal gefunden, ist aber wieder da (z. B. von jemandem zurückgelegt). Der Fund **zählt**. | Artikel entfernen; ggf. Ursache klären. Das Datum steht auch in der CSV. |
| **Already found** – „Already counted in this search as find #n (<Datum>). TID: …“ | Dieser Tag wurde in **dieser** Suche schon gezählt (z. B. doppelt bestätigt). Es wird **nicht** nochmals gezählt. | Prüfen, ob derselbe Artikel nochmals vorgelegt wurde. |
| No duplicate at the reader – wrong item? | Kein Tag mit dem gesuchten EPC im Nahbereich | Artikel direkt an die Pistole halten, ggf. falscher Artikel |
| Several duplicates detected – isolate the item | Mehrere passende Tags gleichzeitig | Artikel von anderen entfernen, einzeln prüfen |
| No tag detected – hold the item directly to the reader | Anlesen (Phase 1) ohne Treffer | Artikel direkt an die Pistole halten |
| Several tags detected – hold a single item to the reader | Anlesen mit mehreren Tags | nur einen Artikel vorhalten |
| Enter a 24-character EPC … | Eingabe ungültig | EPC/SGTIN korrigieren |
| Reader not connected | Reader nicht verbunden | kein grünes Antennen-Icon: IH45 einstecken bzw. Zebra einschalten, im Menü **Reader → Connect** |
| Reader error: … | Fehler vom Reader (Text vom Gerät) | erneut versuchen, ggf. Reader neu starten |
| Export failed: … | CSV konnte nicht geschrieben werden | **End search** erneut tippen (Suche bleibt offen) |

Kurze Hinweise unten am Bildschirm (Snackbar): „Find #n saved – remove the item“, „Exported: <Datei>“, „No duplicates found – nothing exported“.

## 6 Weitere Einstellungen

- **Language:** English oder Deutsch (wirkt sofort).
- **About:** App-Name und Version.

## 7 Fehlerbehebung

| Symptom | Mögliche Ursache | Maßnahme |
|---|---|---|
| Trigger löst im Dashboard nichts aus | Reader nicht verbunden (kein Antennen-Icon); Duplicate Finder noch offen und Modus nicht zurückgesetzt | Menü → Reader → Connect; zum Dashboard wechseln (Menü → Dashboard); bei Wiederholung Logcat (`adb logcat -s QccZebra QccIh45 QccCt37`) sichern und melden |
| „Reader nicht verbunden“ / „Nicht verbunden“ | Zebra: Reader aus, außer Reichweite, Bluetooth aus. IH45: USB-Kabel nicht gesteckt | Reader einschalten, Bluetooth prüfen bzw. Kabel prüfen, **Connect** |
| „Kein RFID-Reader gefunden“ / „Kein IH45 per USB gefunden“ | weder IH45 per USB noch Zebra gefunden | Kabel/Reader prüfen, ca. 10 s warten (ein beim Start schon steckendes Kabel meldet das SDK erst nach Sekunden), dann **Connect** |
| IH45 trennt sich im Betrieb von selbst (Antennen-Icon verschwindet, Toast „RFID reader disconnected“) | USB-Verbindung reißt gelegentlich ab, Ursache unbekannt (Kabel/Akku/Energiesparen) | Kabel prüfen/neu einstecken (die App verbindet automatisch neu); Vorfall mit Uhrzeit melden |
| IH45: Tacho springt kurz auf 0 oder friert ein | Leistungsstufenwechsel im Nahbereich (0,5–0,8 s) | normal; Reader ruhig am Tag halten |
| IH45: 100 % wird nicht erreicht | Lage des Tags schwankt im Nahfeld um 3–5 dB; ungünstig liegende Tags erreichen evtl. nie Stufe 2 | Tag anders ausrichten; per Trigger-Loslassen trotzdem bestätigen, sobald die Nähe ≥ 95 % war |
| Falsches Tag übernommen (IH45) | Nachbartag in der anderen Hand ähnlich stark | Nur ein Artikel am Reader, andere Artikel weiter weg halten |
| Keine Bluetooth-Verbindung trotz eingeschaltetem Reader | Berechtigung nicht erteilt | App-Einstellungen → Berechtigungen → „Nearby devices“ erlauben |
| Tacho bleibt bei 0 % | Trigger nicht gehalten bzw. Ziel-EPC weicht ab (Filterwert!) | Trigger halten; bei getippter SGTIN Filterwert prüfen (Standard 1); besser **anlesen** |
| Confirm find / Loslassen: „No duplicate at the reader“ | Artikel nicht direkt an der Pistole (Zebra: Nahbereichs-Leistung gering; IH45: Signal unter −50 dBm) | Artikel direkt an die Pistole legen |
| CSV nicht auffindbar | Ablage im Ordner *Documents* des Geräts | Dateimanager → Documents |
| Mismatch trotz richtigem Artikel | QR ohne Seriennummer, anderer Company-Prefix, falscher Tag | QR-Rohwert in der Karte prüfen; Tag/Etikett kontrollieren |
