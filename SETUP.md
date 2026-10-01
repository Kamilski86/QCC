# SETUP.md — QCC: Erste Schritte

Diese Anleitung erklärt, wie du das Projekt lokal zum Laufen bringst.

---

## Voraussetzungen

### Software
| Tool | Mindestversion | Hinweis |
|------|---------------|---------|
| Android Studio | Hedgehog / 2023.1+ | Empfohlen: aktuellste stabile Version |
| JDK | 17 | Wird von Android Studio mitgeliefert |
| Gradle | 8.x | Wrapper im Repo enthalten (`./gradlew`) |
| Kotlin | 1.9+ | Über Gradle konfiguriert |

### Hardware (zwingend erforderlich)
> ⚠️ **Ohne physische Hardware kann die App nicht vollständig getestet werden. Ein Android-Emulator reicht nicht aus.**

| Gerät | Rolle |
|-------|-------|
| **Honeywell CT37** | Zielgerät (Android-Handheld), führt die App aus + liest QR/Barcodes |
| **Zebra RFD8500** | Bluetooth-RFID-Reader, wird via Zebra SDK angebunden |

---

## Repository klonen

```bash
git clone <REPO_URL>
cd QCC
```

---

## Projekt in Android Studio öffnen

1. Android Studio starten
2. **File → Open** → Ordner `QCC` auswählen
3. Gradle-Sync abwarten (kann einige Minuten dauern beim ersten Mal)

> Falls der Sync fehlschlägt: **File → Invalidate Caches / Restart** probieren.

---

## Vendor-SDKs (lokale AARs)

Die Vendor-SDKs liegen bereits im Repo und werden automatisch eingebunden:

| SDK | Pfad |
|-----|------|
| Honeywell AIDC (Barcode) | `app/libs/DataCollection.aar` |
| Honeywell Barcode Engine | `app/libs/hedc-release.aar` |
| Zebra RFID SDK (11 Module) | `vendor/zebra/*.aar` |

**Kein manueller Download nötig** — alles ist im Repository enthalten.

---

## App bauen

### Debug-Build (für Entwicklung/Tests)
```bash
./gradlew assembleDebug
```
APK liegt danach unter:
`app/build/outputs/apk/debug/app-debug.apk`

### Release-Build
```bash
./gradlew assembleRelease
```

### Lint-Prüfung
```bash
./gradlew lint
```

### Unit-Tests ausführen
```bash
./gradlew test
```

> Hinweis: Es gibt **keine instrumented Tests** (keine Emulator- oder Gerätetests). Nur Unit-Tests laufen ohne Hardware.

---

## App auf das Gerät deployen

1. **USB-Debugging** auf dem Honeywell CT37 aktivieren:
   - Einstellungen → Über das Telefon → Build-Nummer 7× tippen
   - Einstellungen → Entwickleroptionen → USB-Debugging einschalten
2. Gerät per USB verbinden
3. In Android Studio: **Run → Run 'app'** (Shift+F10)
   oder direkt per ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Zebra RFD8500 verbinden

- Der RFID-Reader verbindet sich automatisch per **Bluetooth**.
- Die App sucht beim Start automatisch nach dem ersten verfügbaren Zebra-Reader.
- Stelle sicher, dass der RFD8500 **eingeschaltet** und **Bluetooth am CT37 aktiviert** ist.
- Die Verbindung kann im **Reader-Screen** der App gesteuert werden (Suchen, Verbinden, Sendeleistung einstellen).

---

## App-Navigation (Überblick)

```
Splash → Dashboard
           └── Drawer-Menü:
                ├── Dashboard       — QR + RFID Vergleich per Pistolen-Trigger, Reset, CSV-Export, „Find duplicates“
                ├── Find duplicates — Duplikatsuche (auch über Button im Dashboard)
                ├── Reader      — Zebra Reader Status / Verbinden
                ├── Language    — Sprachauswahl (DE / EN / PL)
                └── About       — App-Informationen
```

---

## Datenbank

- Room-Datenbank: `qcc.db`
- Schema Version: **8** (Tabellen `scans`, `mismatches`, `duplicate_searches`, `duplicate_finds`)
- Liegt lokal auf dem Gerät unter dem App-Datenpfad
- **Kein manuelles Setup nötig** — wird beim ersten App-Start automatisch angelegt

---

## Lokalisierung

Die App unterstützt drei Sprachen:

| Ordner | Sprache |
|--------|---------|
| `res/values/` | Englisch (Standard) |
| `res/values-de/` | Deutsch |
| `res/values-pl/` | Polnisch |

---

## Häufige Probleme

| Problem | Lösung |
|---------|--------|
| Gradle-Sync schlägt fehl | `./gradlew clean` ausführen, dann erneut syncen |
| AAR nicht gefunden | Prüfen ob `app/libs/` und `vendor/zebra/` vollständig vorhanden sind |
| Zebra Reader nicht verbunden | Bluetooth prüfen, Reader neu starten, im Reader-Screen neu verbinden |
| App startet nicht auf CT37 | USB-Debugging prüfen, ADB-Verbindung testen (`adb devices`) |
| Build-Fehler wegen JDK | Android Studio → SDK Manager → JDK-Pfad auf Version 17 setzen |

---

## Weiterführende Dokumentation

| Datei | Inhalt |
|-------|--------|
| `AGENTS.md` | Architektur-Übersicht, Entscheidungen, Code-Muster |
| `CLAUDE.md` | Build-Befehle, Workflow-Prinzipien |
| `docs/confluence/` | Vollständige Applikationsdokumentation (Einstieg: `00-index.md`) |

---

> Bei Fragen zur Architektur oder zum Code: zuerst `AGENTS.md` lesen — dort sind alle wichtigen Entscheidungen dokumentiert.

