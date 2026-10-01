# 14 Glossar

| Begriff | Erklärung |
|---|---|
| **AI (Application Identifier)** | GS1-Kennzeichen eines Datenfeldes, z. B. `01` = GTIN, `21` = Seriennummer, `00` = SSCC |
| **Antennen-Icon (Reader-Anzeige)** | grünes Symbol oben rechts (`ReaderConnectionIndicator`), solange ein RFID-Reader verbunden ist; zusätzlich Toast beim Verbinden/Trennen |
| **AIDC** | Automatic Identification and Data Capture; Honeywell-SDK für den integrierten Scanner des CT37 |
| **Already found** | Meldung im Duplicate Finder: dieselbe TID wurde in **derselben** Suche schon gezählt (zählt nicht erneut) |
| **Anlesen / Capture** | Lesen genau eines Tags im Nahbereich (Trigger in Phase 1), um dessen EPC als Suchziel zu übernehmen |
| **Arbiter (TriggerModeArbiter)** | Klasse, die festhält, welcher Bildschirm den Trigger-Modus „besitzt“ |
| **CAPTURE / LOCATE / QCC** | Trigger-Modi der RFID-Gateways (Anlesen / Ortung / Standard-Vergleich) |
| **Company Prefix (GCP)** | GS1-Firmenpräfix (6–12 Stellen), Teil von GTIN/SGTIN |
| **Confirm find** | Aktion im Duplicate Finder: liest die TID des Artikels an der Pistole und registriert den Fund |
| **Continuous search** | Schalter: Ortung läuft ohne gedrückten Trigger |
| **CT37** | Honeywell-Handheld (Android) – Host der App und 2D-Scanner |
| **DataMatrix / QR** | 2D-Codes; im Projekt GS1-konforme Inhalte bzw. QR-URLs mit `/01/<GTIN>/21/<Serial>` |
| **Duplikat** | RFID-Tag mit **identischem EPC** wie ein anderer (fehlerhafte Codierung durch den Tag-Produzenten) |
| **EPC** | Electronic Product Code; hier SGTIN-96 als 24 Hex-Zeichen |
| **EPCIS 2.0** | GS1-Standard für Ereignisdaten in der Lieferkette; geplante Integration („Inbound Read“) |
| **Filterwert** | 3-Bit-Feld im SGTIN-96 (0–7); 1 = Verkaufsartikel (Point-of-Sale Trade Item) |
| **Found again** | Dialog: TID wurde in einer **früheren** Suche gefunden, zählt aber erneut, mit Datum des Vorfunds |
| **Fund (Find)** | Ein registrierter Tag einer Suche (Zeile in `duplicate_finds`) |
| **Gateway** | Kotlin-Interface + Implementierung, die Hardware-SDKs kapselt |
| **GS1** | Standardisierungsorganisation (GTIN, SGTIN, SSCC, EPC …) |
| **GTIN** | Global Trade Item Number (hier GTIN-14) |
| **Hilt** | Dependency-Injection-Framework (Dagger) |
| **IH45 (Honeywell IH45)** | Honeywell-UHF-RFID-Reader mit Trigger; in der App per USB angebunden (Honeywell RFID SDK), Vorrang vor dem RFD8500 |
| **Inbound Read** | geplanter Use Case: Wareneingang mit SSCC, Mehr-Tag-Inventur und EPCIS |
| **Inventory** | RFID-Leseoperation, bei der alle Tags im Feld antworten; im QCC-Modus nach dem ersten Tag gestoppt |
| **ItemRef (Item Reference)** | Artikelreferenz in GTIN/SGTIN; in der App inkl. führendem Indikator |
| **Leistungsstufe (IH45)** | Stufen 0/1/2 der Sendeleistung in der IH45-Ortung (30 dBm / 10 dBm / 5 dBm), um im Nahfeld Berührung von 15–20 cm zu unterscheiden |
| **Locate (TagLocationing)** | Zebra-Funktion zur Ortung eines Tags über relative Nähe (0–100); beim IH45 aus RSSI und Leistungsstufen berechnet |
| **Match / Mismatch** | Ergebnis des Vergleichs: SGTIN von QR und Tag stimmen überein / nicht |
| **MediaStore** | Android-API zum Schreiben in öffentliche Ordner (hier *Documents*) |
| **MV-C** | geplanter neuer Anzeigename der App (Bedeutung **[zu bestätigen]**) |
| **Nahbereich / Nearby** | Lesen, bei dem nur Tags direkt an der Pistole zählen: Zebra mit stark reduzierter Leistung (5 %), IH45 mit voller Leistung und Auswahl nach RSSI |
| **Nahfeld (IH45)** | Bereich 0–20 cm, in dem der RSSI sättigt (−23…−27 dBm) und Berührung nicht von 15–20 cm unterscheidbar ist |
| **Partition** | 3-Bit-Feld im SGTIN-96, legt die Bitaufteilung von Company Prefix und Item Reference fest |
| **Pistole** | umgangssprachlich: RFID-Reader (RFD8500 oder IH45) mit Trigger |
| **Proximity / Nähe** | relative Entfernungsangabe 0–100 % bei der Ortung |
| **QCC** | Name der App und des Standard-Vergleichsmodus; Abkürzung **[zu bestätigen]** |
| **R8** | Android-Code-Shrinker/Optimierer für Release-Builds |
| **RSSI** | Received Signal Strength Indicator: Empfangsstärke der Tag-Antwort in dBm (IH45: gehaltenes Tag −23…−43, entferntes −56…−60); ändert sich nicht mit der Sendeleistung |
| **RFD8500** | Zebra-RFID-Reader (UHF) mit Trigger, per Bluetooth |
| **Switching-Gateway (`SwitchingRfidReaderGateway`)** | Fassade, die zwischen IH45 (USB, Vorrang) und Zebra (Bluetooth) umschaltet; genau ein Reader gleichzeitig |
| **Room** | AndroidX-Persistenzbibliothek auf SQLite |
| **SGTIN / SGTIN-96** | Serialized GTIN / dessen 96-Bit-EPC-Codierung |
| **Singulation / Session S0 / State A** | RFID-Parameter für Mehrfachlesung; Einstellung: S0, A, SL „alle“ |
| **SSCC** | Serial Shipping Container Code (18 Stellen, AI 00); noch nicht unterstützt |
| **Store (Filiale)** | optionales Textfeld einer Duplikatsuche |
| **TID** | Tag Identifier: vom Chip-Hersteller vergebene, eindeutige Chip-Kennung (hier 96 Bit gelesen) |
| **Trigger** | Abzug der Pistole (RFD8500 oder IH45) bzw. Scan-Taste des CT37 |
| **Loslassen am Tag** | Bestätigung eines Fundes durch Loslassen des Triggers, wenn die Nähe zuletzt ≥ 95 % war (Duplicate Finder) |
| **Tacho / Gauge** | Halbkreis-Anzeige der Nähe im Duplicate Finder |
| **Zyklus** | ein Vergleichsvorgang: QR + RFID + Ergebnis |
| **USB-Port / `UsbPort`** | beim IH45 Anbindung als serieller Port (`/dev/ttyACM0`, 115200 Baud, USB-IDs `0C2E:10EA`) über das Honeywell RFID SDK |
