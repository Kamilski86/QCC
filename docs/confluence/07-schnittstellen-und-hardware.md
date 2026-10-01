# 07 Schnittstellen, Hardware und GS1

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Quellen** | `device/*`, `core/gs1/Gs1ParserService.kt`, Design-Spec (Hardware-Spike 2026-09-30), Tests |

## 1 Hardware und SDKs

| Gerät | Rolle | Anbindung | SDK (lokal im Repository) |
|---|---|---|---|
| **Honeywell CT37** (Android 14) | Host-Gerät der App; 2D-Scanner, Anzeige, Ton, Vibration | integrierter Scanner | `app/libs/DataCollection.aar`, `app/libs/hedc-release.aar` (AIDC-API) |
| **Zebra RFD8500** | RFID-Reader (UHF) mit Trigger | Bluetooth | `vendor/zebra/*.aar` (Zebra RFID API3 2.0.5.238, u. a. `API3_READER`, `API3_TRANSPORT`, `rfidhostlib`, `BarcodeScannerLibrary`) |

Beispiel-Apps der Hersteller (`vendor/zebra/HHSampleApp`, `ZIOTCSampleApp`) sind nicht Teil des Builds und per `.gitignore` ausgeschlossen.

## 2 Gateway-Schnittstellen

### 2.1 `BarcodeScannerGateway` (Honeywell)

| Methode | Beschreibung |
|---|---|
| `observeScans(): Flow<String>` | decodierte Rohdaten (nicht-leer) |
| `suspend triggerScan()` | Software-Auslösung: `aim(true)`, `light(true)`, `decode(true)`; wirft `IllegalStateException`, wenn der Scanner nicht verfügbar ist |
| `suspend stopScan()` | `aim/light/decode(false)` (Fehler werden verschluckt) |

Implementierung `HoneywellCt37ScannerGateway`: Initialisierung über `AidcManager.create`, Reader im **CLIENT_CONTROL**-Modus (Hardware-Taste → `onTriggerEvent` → aim/light/decode, Software-Auslösung über `triggerScan`); `claim()` sofort nach dem Anlegen und in `MainActivity.onResume`, `release()` in `onPause`, `close()` in `onDestroy`. Nach einem Treffer (`onBarcodeEvent`) wird der Scanner gestoppt und der Wert per `tryEmit` ausgegeben.

### 2.2 `RfidReaderGateway` (Zebra)

| Methode | Beschreibung |
|---|---|
| `observeStatus(): Flow<ReaderStatus>` | Verbindungsstatus und Text |
| `observeTagScans(): Flow<String>` | **ein** EPC je Inventory-Session (QCC) |
| `observeTriggerEvents(): Flow<Unit>` | Trigger gedrückt – **nur im Modus `QCC`** |
| `discoverReaders()`, `connect(id)`, `disconnect()` | Verbindungsaufbau (Bluetooth) |
| `triggerInventory()` | Software-Auslösung des Einzel-Tag-Inventory (QCC) |
| `setPower(level)` | Pegel 1–30 → Leistungsindex |
| `setTriggerMode(mode)` | `QCC`, `CAPTURE`, `LOCATE` (Standardimplementierung: nichts) |
| `claimTriggerMode(owner, mode)` / `releaseTriggerMode(owner)` | Besitzer-API (siehe Seite 05, Abschnitt 5.3) |
| `observeHandheldTrigger(): Flow<Boolean>` | `true` gedrückt, `false` losgelassen – nur außerhalb `QCC` |
| `observeProximity(): Flow<Int>` | relative Nähe 0–100 während der Ortung |
| `startLocate(epc, mask)` / `stopLocate()` | Ortung per `TagLocationing` |
| `readEpcsNearby()` | verschiedene EPCs aller im Nahbereich gelesenen Tags (Anlesen) |
| `readTidsNearby(epc, mask)` | TIDs (96 Bit) der Tags mit passendem EPC im Nahbereich |

Die Standardimplementierungen im Interface (leere Flows, `Result.failure(UnsupportedOperation…)`) halten Test-Fakes kompilierbar.

## 3 Zebra-Gateway im Detail

### 3.1 Verbindung und Konfiguration (`configureReader`)

- Beim Start sucht `ScanViewModel` die Reader (`GetAvailableRFIDReaderList`) und verbindet den **ersten**; Verbindungsabbruch (`DISCONNECTION_EVENT`) setzt alle Betriebs-Flags zurück und meldet „Nicht verbunden“.
- Beim Verbinden: Leistungstabelle auslesen (`maxPowerIndex`), Event-Listener registrieren, Handheld- und Tag-Read-Events aktivieren, **Trigger → RFID-Modus**, Start-/Stop-Trigger **sofort**, **Dynamic Power Optimization aus** (nur RFD8500), Antenne 1: Leistungsindex, `rfModeTableIndex 0`, `Tari 0`, **Singulation Session S0, Zustand A, SL-Flag „alle“**, Access-Operation-Timeout 1000 ms.
- Konfigurationsfehler werden als Statustext „Konfigurationsfehler: …“ ausgegeben.

### 3.2 Leistung

- UI-Pegel 1–30 → Index `(pegel − 1) × maxPowerIndex / 29`. Standard-Pegel 1 → **Index 0 (Minimum)**.
- **Nahbereichs-Lesen** (Anlesen, TID): temporär **5 % des Indexbereichs** (`NEARBY_POWER_PERCENT`), mindestens Index **15** (`MIN_NEARBY_POWER_INDEX`, hardware-verifiziert bei 300 Stufen), höchstens `maxPowerIndex`; danach Wiederherstellung der Anwender-Leistung (ein Wiederholungsversuch; bei Fehlschlag Statustext „Power-Fehler: …“).

### 3.3 Operationen und Exklusivität

| Operation | Dauer | Bedingung |
|---|---|---|
| QCC-Inventory | bis zum ersten Tag oder Trigger-Loslassen | `inventoryActive` (AtomicBoolean) |
| Ortung | solange aktiv | `locateActive`; Start nur, wenn weder Nearby noch Inventory läuft |
| Nearby-Lesen | **700 ms** (`NEARBY_READ_WINDOW_MS`) | `nearbyActive`; nicht parallel zu Ortung/Inventory; stoppt in `finally` immer die Operation |

Verletzungen liefern „Reader beschäftigt“. `setTriggerMode(QCC)` stoppt eine aktive Ortung.

### 3.4 Lesen der TID

`TagAccess.readEvent` mit `ReadAccessParams` (Speicherbank **TID**, Offset 0, **count = 6** Wörter = 96 Bit) und **EPC-Filter**: Speicherbank EPC, `bitOffset = 32` (CRC + PC überspringen), Muster = Ziel-EPC, 96 Bit Muster- und Maskenlänge, Maske `FFFFFFFFFFFFFFFFFFFFFFFF`. Ausgewertet werden nur Lese-Operationen mit Status `ACCESS_SUCCESS` und nicht leeren Daten; Ergebnisse werden großgeschrieben und dedupliziert.

### 3.5 Ortung

`TagLocationing.Perform(epc, mask, null)`; die Nähe (`LocationInfo.relativeDistance`) wird als Ganzzahl 0–100 ausgegeben; Stopp setzt die Nähe auf 0.

## 4 Hardware-Befunde (Spike 2026-09-30, RFD8500)

| # | Befund | Konsequenz |
|---|---|---|
| H1 | `TagLocationing` akzeptiert nur Masken, die **ab dem MSB lückenlos 1** sind. Maske mit Lücke (z. B. `FF1F…`, `FF00…`) → `Invalid EPC Mask`. | Ortung/TID-Prüfung immer **exakt** (`LocateTarget.FULL_MASK`); getippte SGTIN braucht expliziten **Filterwert** (Standard 1, wählbar 0–7). |
| H2 | Exakte Ortung funktioniert; 4–5 Nähewerte/s; Werte 38–99 je nach Abstand. | Tacho/Beep-Takt sinnvoll. |
| H3 | TID-Lesen funktioniert; `count = 0` liefert 96 Bit + 12 angehängte Nullen. | `count = 6` (96 Bit). |
| H4 | Anlesen mit Leistungsindex 0 unzuverlässig; **5 %** (Index 15 von 300) liest zuverlässig bei Kontakt, bei ca. 30 cm nichts. | Nahbereichs-Leistung 5 % mit Untergrenze Index 15. |
| H5 | Trigger-Ereignisse kommen außerhalb des QCC-Modus zuverlässig an; QCC danach unverändert. | Modus-Routing im Gateway. |
| H6 | **Nicht getestet:** zwei Duplikate gleichzeitig an der Pistole (nur ein Duplikat verfügbar). | offener Hardware-Test (Seite 09/13) |
| H7 | Echter Duplikat-EPC `3034F8242800C10300000011` (Filter 1, Partition 5) = SGTIN `4065546.000772.12884901905`. | Testdaten |

## 5 GS1-Standards und Parser

### 5.1 Begriffe in Kürze
- **GTIN-14**: Indikator (1) + Company Prefix + Item Reference + Prüfziffer.
- **SGTIN**: GTIN + Seriennummer; **Pure Identity** in der App als `GCP.ItemRef.Serial`, wobei `ItemRef` den Indikator als erste Ziffer enthält.
- **SGTIN-96**: 96-Bit-EPC-Format (24 Hex-Zeichen) für RFID-Tags.

### 5.2 SGTIN-96-Bitaufbau

| Feld | Bits | Hinweis |
|---|---|---|
| Header | 8 | `00110000` = `0x30` |
| Filter | 3 | 0–7; Standard der App für getippte SGTINs: **1** (Verkaufsartikel) |
| Partition | 3 | 0–6, bestimmt die Aufteilung unten |
| Company Prefix + Item Reference | 44 | partitionsabhängig |
| Seriennummer | 38 | max. 2³⁸ − 1; im Parser ohne führende Nullen |

| Partition | GCP-Bits | GCP-Stellen | ItemRef-Bits | ItemRef-Stellen (inkl. Indikator) |
|---|---|---|---|---|
| 0 | 40 | 12 | 4 | 1 |
| 1 | 37 | 11 | 7 | 2 |
| 2 | 34 | 10 | 10 | 3 |
| 3 | 30 | 9 | 14 | 4 |
| 4 | 27 | 8 | 17 | 5 |
| 5 | 24 | 7 | 20 | 6 |
| 6 | 20 | 6 | 24 | 7 |

### 5.3 Parser-Regeln (`Gs1ParserService`)

| Funktion | Eingabe → Ausgabe | Regeln / Fehler |
|---|---|---|
| `parseQr(raw)` | String → `QrScanResult` | Regex `.*/01/(\d{14})/21/([^/]+).*`; sonst „QR-Code enthält keine erwartete GTIN/Seriennummer-Struktur“ |
| `buildQrSgtin(gtin, serial)` | → `GCP.ItemRef.Serial` | GTIN 14-stellig; Serial nicht leer; **GCP = 7 Stellen fest** (Stellen 2–8 der GTIN), ItemRef = Indikator + Stellen 9–13; Prüfziffer entfällt |
| `parseEpcToSgtin(epc)` | 24 Hex → `RfidScanResult` | nur Hex; gerade Länge; genau 24 Zeichen; Header `0x30`; Partition 0–6; GTIN ≠ 0…0; Serial ≠ `0000000000`; Prüfziffer wird berechnet (Gewichte 3/1) |
| `parseLocateTarget(input, filter)` | EPC (24 Hex) **oder** `Ziffern.Ziffern.Ziffern` → `LocateTarget` | sonst „Eingabe ist weder EPC (24 Hex) noch SGTIN“; ein Hex-EPC trägt seinen Filter selbst |
| `encodeSgtin96(gcp, itemRef, serial, filter)` | → 24 Hex | GCP 6–12 Stellen; ItemRef-Länge passend zur Partition; Filter 0–7; Serial ohne führende Null (außer `0`) und < 2³⁸ |

### 5.4 Durchgerechnetes Beispiel

| Größe | Wert |
|---|---|
| QR (Rohdaten) | `http://qr.c-a.com/01/04065546927682/21/12884901894` |
| GTIN-14 | `04065546927682` (Indikator `0`, GCP `4065546`, ItemRef ohne Indikator `92768`, Prüfziffer `2`) |
| QR-SGTIN | `4065546.092768.12884901894` |
| EPC (Filter 1, Partition 5) | `3034F824285A980300000006`: Header `30`, Filter `001`, Partition `101`, GCP-Bits für `4065546`, ItemRef-Bits für `092768`, Serial 12884901894 = `0x300000006` |
| EPC (Filter 0) | `3014F824285A980300000006` – **derselbe** SGTIN, daher `MATCH` im QCC-Vergleich (der Filterwert fließt nicht ein) |

> Der QCC-Vergleich vergleicht SGTIN-Strings, **nicht** EPC-Bits; der Filterwert ist daher für den Vergleich ohne Bedeutung, für die **Ortung/TID-Prüfung** (exakte EPC-Maske) aber entscheidend.

## 6 Interne Ereignisströme

| Ströme | Typ | Puffer |
|---|---|---|
| `scanFlow` (Honeywell), `tagFlow`, `triggerFlow` (Zebra) | `MutableSharedFlow`, `extraBufferCapacity = 1`, `DROP_OLDEST` | 1 |
| `handheldTriggerFlow` | `MutableSharedFlow<Boolean>` | 4 |
| `proximityFlow` | `MutableSharedFlow<Int>` | 16 |
| `statusFlow` | `MutableStateFlow<ReaderStatus>` | – |

Hardware-Ereignisse werden nie blockierend zugestellt; bei vollem Puffer geht das älteste verloren.

## 7 Schnittstellen nach außen

| Schnittstelle | Richtung | Details |
|---|---|---|
| Dateisystem (`Documents`) | Ausgabe | CSV; `MediaStore` ab API 29 |
| Netzwerk | – | keine; geplante Erweiterung EPCIS 2.0 (Retrofit/OkHttp, `INTERNET`), Seite 13 |
| Intent-Filter | Eingabe | nur `MAIN/LAUNCHER`; Activity `exported=true` |
