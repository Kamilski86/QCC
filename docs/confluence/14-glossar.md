# 14 Glossar

| Begriff | Erklärung |
|---|---|
| **AI (Application Identifier)** | GS1-Kennzeichen eines Datenfeldes, z. B. `01` = GTIN, `21` = Seriennummer, `00` = SSCC |
| **AIDC** | Automatic Identification and Data Capture; Honeywell-SDK für den integrierten Scanner des CT37 |
| **Already found** | Meldung im Duplicate Finder: dieselbe TID wurde in **derselben** Suche schon gezählt (zählt nicht erneut) |
| **Anlesen / Capture** | Lesen genau eines Tags im Nahbereich (Trigger in Phase 1), um dessen EPC als Suchziel zu übernehmen |
| **Arbiter (TriggerModeArbiter)** | Klasse, die festhält, welcher Bildschirm den Trigger-Modus „besitzt“ |
| **CAPTURE / LOCATE / QCC** | Trigger-Modi des Zebra-Gateways (Anlesen / Ortung / Standard-Vergleich) |
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
| **Inbound Read** | geplanter Use Case: Wareneingang mit SSCC, Mehr-Tag-Inventur und EPCIS |
| **Inventory** | RFID-Leseoperation, bei der alle Tags im Feld antworten; im QCC-Modus nach dem ersten Tag gestoppt |
| **ItemRef (Item Reference)** | Artikelreferenz in GTIN/SGTIN; in der App inkl. führendem Indikator |
| **Locate (TagLocationing)** | Zebra-Funktion zur Ortung eines Tags über relative Nähe (0–100) |
| **Match / Mismatch** | Ergebnis des Vergleichs: SGTIN von QR und Tag stimmen überein / nicht |
| **MediaStore** | Android-API zum Schreiben in öffentliche Ordner (hier *Documents*) |
| **MV-C** | geplanter neuer Anzeigename der App (Bedeutung **[zu bestätigen]**) |
| **Nahbereich / Nearby** | Lesen mit stark reduzierter Leistung (5 %), damit nur Tags direkt an der Pistole antworten |
| **Partition** | 3-Bit-Feld im SGTIN-96, legt die Bitaufteilung von Company Prefix und Item Reference fest |
| **Pistole** | umgangssprachlich: RFD8500 mit Trigger |
| **Proximity / Nähe** | relative Entfernungsangabe 0–100 % bei der Ortung |
| **QCC** | Name der App und des Standard-Vergleichsmodus; Abkürzung **[zu bestätigen]** |
| **R8** | Android-Code-Shrinker/Optimierer für Release-Builds |
| **RFD8500** | Zebra-RFID-Reader (UHF) mit Trigger, per Bluetooth |
| **Room** | AndroidX-Persistenzbibliothek auf SQLite |
| **SGTIN / SGTIN-96** | Serialized GTIN / dessen 96-Bit-EPC-Codierung |
| **Singulation / Session S0 / State A** | RFID-Parameter für Mehrfachlesung; Einstellung: S0, A, SL „alle“ |
| **SSCC** | Serial Shipping Container Code (18 Stellen, AI 00); noch nicht unterstützt |
| **Store (Filiale)** | optionales Textfeld einer Duplikatsuche |
| **TID** | Tag Identifier: vom Chip-Hersteller vergebene, eindeutige Chip-Kennung (hier 96 Bit gelesen) |
| **Trigger** | Abzug der Pistole (RFD8500) bzw. Scan-Taste des CT37 |
| **Tacho / Gauge** | Halbkreis-Anzeige der Nähe im Duplicate Finder |
| **Zyklus** | ein Vergleichsvorgang: QR + RFID + Ergebnis |
