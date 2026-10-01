# 01 Hintergrund und Nutzen

| | |
|---|---|
| **Stand** | 2026-10-01 |
| **Quellen** | `docs/superpowers/specs/2026-09-29-duplicate-finder-design.md`, bisherige Rollen-Dokumente (`docs/01`, `docs/03`), Code |
| **Kennzeichnung** | Aussagen, die sich nicht aus Code oder Repository belegen lassen, sind mit **[zu bestätigen]** markiert und auf Seite 13 gesammelt. |

## 1 Geschäftlicher Kontext

Im Handel (hier: Textil/Bekleidung) tragen Artikel zwei maschinenlesbare Kennzeichnungen:

- einen **aufgedruckten 2D-Code** (GS1 DataMatrix bzw. QR-URL der Form `…/01/<GTIN-14>/21/<Seriennummer>`), und
- einen **RFID-Tag** (UHF, EPC-Format **SGTIN-96**), der u. a. für Bestandsführung und Inventur gelesen wird.

Beide Kennzeichnungen müssen **dieselbe Artikelidentität** (GTIN + Seriennummer = SGTIN) tragen. Weicht der Tag vom Aufdruck ab, ist der Artikel in Warenwirtschaft und Inventur falsch zugeordnet.

> Der Bezug zum Unternehmen ergibt sich aus Repository-Artefakten (Paketname `de.ca`, Firmenlogo im Repository, Domain `qr.c-a.com` in den Testdaten). Auftraggeber, Fachbereich und Product Owner sind im Repository **nicht** benannt **[zu bestätigen]**.

## 2 Problemstellungen

### P1 – Fehlzuordnung zwischen Tag und Aufdruck (Use Case „QCC-Vergleich“)

Tag und Aufdruck können auseinanderlaufen (falsch codierter Tag, vertauschtes Etikett, falsch gedruckter Code). Das ist am Artikel nicht sichtbar. Es braucht eine Prüfung, die am Arbeitsplatz **in Sekunden** eine verlässliche Ja/Nein-Aussage liefert und jede Prüfung nachvollziehbar protokolliert.

### P2 – Doppelt vergebene EPCs (Use Case „Find duplicates“)

Ein Tag-Produzent hat **dieselbe SGTIN/denselben EPC auf ca. 480 RFID-Tags** codiert. Ein EPC muss weltweit eindeutig sein; mehrfach vorhandene EPCs verfälschen Bestandsführung und Inventur. Die betroffenen Artikel müssen in den **Filialen einzeln gefunden**, aus dem Verkauf genommen und für die **Reklamation dokumentiert** werden. Da alle Duplikate denselben EPC senden, kann man sie nur über die **TID** (vom Chip-Hersteller vergebene, eindeutige Chip-ID) unterscheiden.

## 3 Produktvision und Leitprinzipien

QCC reduziert Fehlzuordnungen zwischen physischem RFID-Tag und gedruckter Kennzeichnung und macht fehlerhafte Tags auffindbar.

- **Geschwindigkeit vor Komplexität** – Bedienung über den Pistolen-Trigger, möglichst wenige Eingaben.
- **Eindeutige Bedienung unter Produktionsdruck** – klare Rückmeldung (Farbe, Ton, Vibration, Dialog bei Warnungen).
- **Nachvollziehbarkeit** – jede Entscheidung wird lokal gespeichert und ist als CSV exportierbar.
- **Robustheit** – eine Funktion darf die andere nie blockieren (der Duplicate Finder pausiert den QCC-Vergleich und gibt die Hardware beim Verlassen sicher zurück).

## 4 Nutzen (Benefits)

| Nutzen | Wirkung | Wie die App ihn erzeugt |
|---|---|---|
| Fehlzuordnungen früh erkennen | weniger falsch zugeordnete Artikel in Bestand/Inventur | automatischer Vergleich SGTIN(QR) ↔ SGTIN(RFID), Signal bei Mismatch |
| Schnellere Prüfung | kürzere Zeit je Artikel | ein Trigger-Druck löst QR- und RFID-Lesung aus; Reihenfolge egal |
| Revisionsfähigkeit | belegbare Prüfhistorie | jeder Vergleich in `scans`, Mismatches zusätzlich in `mismatches`, CSV-Export |
| Transparenz für die Schichtsteuerung | Tagesleistung sichtbar | Zähler „Scans heute“ / „Mismatches heute“ |
| Gezielte Beseitigung doppelter EPCs | Duplikate einzeln auffindbar statt Massen-Sichtung | Nähe-Ortung (Tacho, Beep, Vibration) mit RFD8500 |
| Belegbare Reklamation | lückenlose Liste der Fundstücke | je Fund TID + Zeit + Filiale in der CSV; Wiederfunde mit Datum des Vorfunds |
| Wenig Schulungsaufwand | schnelle Einarbeitung | wenige Bildschirme, durchgängig weiße Karten/schwarze Buttons |

> **Quantifizierung:** Für Zeitersparnis, Fehlerquote oder Fundquote liegen im Repository **keine Messwerte oder Zielwerte** vor. Die KPI-Definitionen (Abschnitt 6) sind bewusst ohne Zahlen formuliert; Zielwerte sind vom Fachbereich festzulegen **[zu bestätigen]**.

## 5 Stakeholder und Personas

| Rolle | Interesse | Kontakt / Verantwortlicher |
|---|---|---|
| **P1 Operator** (primär, QCC) | schnelle, eindeutige Prüfung im Takt; keine Altwerte-Verwechslung | – |
| **P2 Schichtleiter** (sekundär) | Tageszähler, Mismatch-Quote | – |
| **P3 Prozessverantwortlicher / Qualität** (sekundär) | Ursachenanalyse, exportierbare Daten | – |
| **P4 Filialmitarbeiter / Reklamationsbearbeitung** (Duplicate Finder; aus der Aufgabenstellung abgeleitet) | Duplikate finden, entfernen, dokumentieren | – |
| **IT / Entwicklung** | Betrieb, Weiterentwicklung | Entwickler: Repo-Besitzer (GitHub `Kamilski86/QCC`) |
| **Product Owner / Auftraggeber** | Scope, Priorisierung, Abnahme | **[zu bestätigen]** |

## 6 Geschäftsziele und KPI-Definitionen

| Ziel | KPI | Definition | Zielwert |
|---|---|---|---|
| O1 Prüfprozess beschleunigen | KPI-1 | Median-Zeit je vollständigem Scanzyklus | **[offen]** |
| | KPI-2 | Anteil Zyklen ohne manuellen Reset | **[offen]** |
| O2 Fehlzuordnungen früh erkennen | KPI-3 | Mismatch-Rate je Schicht (= Mismatches ÷ Scans, Quelle `scans.isMatch`) | **[offen]** |
| | KPI-4 | First-Pass-Match-Rate | **[offen]** |
| O3 Auditierbarkeit | KPI-5 | Exportquote je Woche | **[offen]** |
| | KPI-6 | Vollständigkeit persistierter Zyklen (Anzahl Vergleiche = Anzahl `scans`-Zeilen) | 100 % (BR-05) |
| O4 Duplikate beseitigen | KPI-7 | gefundene eindeutige TIDs je Suche (`Found`-Zähler / CSV-Zeilen) | **[offen]** (Gesamtmenge ca. 480 laut Aufgabenstellung; kein „n von 480“-Fortschritt in der App) |
| | KPI-8 | Wiederfunde (Tags, die in einer späteren Suche erneut auftauchen; CSV-Spalte „Previously Found“) | **[offen]** |

## 7 Scope

### Im Scope (Ist, Version 0.1.0)
- QR-Scan, RFID-Scan, automatischer Vergleich, Ergebnisrückmeldung (Anzeige, Ton, Vibration), Reihenfolgefreiheit, Zyklus-Clearing
- Lokale Persistenz (Room), Tageszähler, CSV-Export aller Scans
- Reader-Verbindung (Auto-Connect, Reconnect, Status)
- Duplikatsuche: Zielvorgabe (Anlesen, EPC, SGTIN), Ortung, TID-Erfassung, Wiederfunde, CSV-Export
- Mehrsprachige Oberfläche (EN/DE/PL-Ressourcen)

### Nicht im Scope (derzeit)
- SSCC-Parsing (AI 00), Mehr-Tag-Inventur, EPCIS 2.0 / Online-Kommunikation („Inbound Read“, geplant, Seite 13)
- Benutzer-/Rollenverwaltung, Anmeldung, Mehrgeräte-Synchronisation
- Software-Stummschaltung gefundener Tags (Kill, Neucodierung, Pre-Filter) – gefundene Artikel werden **physisch entfernt**
- Fortschrittsanzeige gegen eine Sollmenge, Suche nach mehreren SGTINs gleichzeitig

## 8 Annahmen und Randbedingungen

- Betrieb ausschließlich mit **Honeywell CT37 + Zebra RFD8500**; es gibt kein Emulator-Ziel.
- Die RFD8500 liefert die Trigger-Ereignisse (Pistolengriff); ohne verbundenen Reader funktioniert der Hauptprozess nicht (daher entfielen die manuellen Scan-Buttons im Dashboard).
- Die App ist **offline**; alle Daten liegen lokal auf dem Gerät.
- Das Unternehmen nutzt derzeit einen **7-stelligen GS1 Company Prefix**. Der Vergleich ist davon unabhängig (GTIN-14 + Seriennummer); nur die angezeigte „QR SGTIN“ unterstellt 7 Stellen für die Punktsetzung.
- Echter Duplikat-EPC aus dem Test: `3034F8242800C10300000011` (Filterwert 1, Partition 5).
